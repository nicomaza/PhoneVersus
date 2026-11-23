package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.ProductContentAdminDtos;
import celulares.cordobacelulares.dtos.seo.PorteBulkPublishDtos;
import celulares.cordobacelulares.dtos.seo.PublishedProductContent;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.entities.SeoProductContent;
import celulares.cordobacelulares.entities.SeoProductContentStatus;
import celulares.cordobacelulares.entities.SeoProductHighlight;
import celulares.cordobacelulares.entities.SeoProductImage;
import celulares.cordobacelulares.entities.SeoProductImportExclusion;
import celulares.cordobacelulares.entities.SeoProductSpec;
import celulares.cordobacelulares.entities.SeoProductSpecSection;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import celulares.cordobacelulares.exceptions.ApiInternalException;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.repository.SeoProductContentRepository;
import celulares.cordobacelulares.repository.SeoProductImageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeoProductContentService {

    private final SeoCatalogProductRepository productRepository;
    private final SeoProductContentRepository contentRepository;
    private final SeoProductImageRepository imageRepository;
    private final SeoProductMediaStorage mediaStorage;
    private final ObjectMapper objectMapper;

    public SeoProductContentService(
            SeoCatalogProductRepository productRepository,
            SeoProductContentRepository contentRepository,
            SeoProductImageRepository imageRepository,
            SeoProductMediaStorage mediaStorage,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.contentRepository = contentRepository;
        this.imageRepository = imageRepository;
        this.mediaStorage = mediaStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProductContentAdminDtos.PageResponse list(
            String text,
            String brand,
            String state,
            int requestedPage,
            int requestedLimit
    ) {
        int page = Math.max(1, requestedPage);
        int limit = Math.min(100, Math.max(1, requestedLimit));
        String query = clean(text).toLowerCase(Locale.ROOT);
        String brandFilter = clean(brand).toLowerCase(Locale.ROOT);
        String stateFilter = clean(state).toUpperCase(Locale.ROOT);

        List<SeoCatalogProduct> products = productRepository.findAllByOrderByMarcaAscModeloAsc();
        Map<Long, SeoProductContent> contents = contentRepository.findAll().stream()
                .collect(Collectors.toMap(
                        item -> item.getSeoCatalogProduct().getId(),
                        Function.identity()
                ));
        List<String> brands = products.stream()
                .map(SeoCatalogProduct::getMarca)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<ProductContentAdminDtos.ListItem> filtered = products.stream()
                .filter(product -> query.isBlank() || matches(product, query))
                .filter(product -> brandFilter.isBlank()
                        || clean(product.getMarca()).toLowerCase(Locale.ROOT).equals(brandFilter))
                .filter(product -> matchesState(contents.get(product.getId()), stateFilter))
                .map(product -> toListItem(product, contents.get(product.getId())))
                .toList();

        int from = Math.min((page - 1) * limit, filtered.size());
        int to = Math.min(from + limit, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / limit);
        return new ProductContentAdminDtos.PageResponse(
                filtered.subList(from, to), brands, page, limit, filtered.size(), totalPages, to < filtered.size()
        );
    }

    @Transactional(readOnly = true)
    public ProductContentAdminDtos.DetailResponse get(String slug) {
        SeoCatalogProduct product = requireProduct(slug);
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug).orElse(null);
        return toDetail(product, content);
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse saveDraft(
            String slug,
            ProductContentAdminDtos.SaveRequest request
    ) {
        SeoCatalogProduct product = requireProduct(slug);
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug)
                .orElseGet(() -> newContent(product));
        applyDraft(content, request == null
                ? new ProductContentAdminDtos.SaveRequest(null, null, null, List.of(), List.of())
                : request);
        if (content.getPublishedSnapshot() == null) {
            content.setStatus(SeoProductContentStatus.DRAFT);
            content.setDraftDirty(false);
        } else {
            content.setStatus(SeoProductContentStatus.PUBLISHED);
            content.setDraftDirty(true);
        }
        return toDetail(product, contentRepository.save(content));
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse publish(String slug) {
        SeoCatalogProduct product = requireProduct(slug);
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug)
                .orElseThrow(() -> new ApiBadRequestException("Primero guarda un borrador"));
        content.setPublishedSnapshot(serialize(snapshot(content)));
        content.setStatus(SeoProductContentStatus.PUBLISHED);
        content.setPublishedAt(Instant.now());
        content.setDraftDirty(false);
        return toDetail(product, contentRepository.save(content));
    }

    @Transactional(readOnly = true)
    public PorteBulkPublishPlan porteBulkPublishPlan() {
        int alreadyPublished = 0;
        int withoutValidPorteSheet = 0;
        int inactiveProducts = 0;
        int skippedOtherReasons = 0;
        List<String> eligibleSlugs = new ArrayList<>();

        for (SeoProductContent content : contentRepository.findAll()) {
            SeoCatalogProduct product = content.getSeoCatalogProduct();
            if (product.getOrigen() != CatalogProductOrigin.TIENDA_PORTE) {
                skippedOtherReasons++;
            } else if (!product.isActive()) {
                inactiveProducts++;
            } else if (hasPublishedSnapshot(content)) {
                alreadyPublished++;
            } else if (!hasValidPorteSynchronization(content)) {
                withoutValidPorteSheet++;
            } else if (!hasUsefulContent(content)) {
                skippedOtherReasons++;
            } else {
                eligibleSlugs.add(product.getSlug());
            }
        }

        PorteBulkPublishDtos.PreviewResponse preview = new PorteBulkPublishDtos.PreviewResponse(
                eligibleSlugs.size(), alreadyPublished, withoutValidPorteSheet,
                inactiveProducts, skippedOtherReasons
        );
        return new PorteBulkPublishPlan(preview, List.copyOf(eligibleSlugs));
    }

    @Transactional
    public boolean publishPorteCandidate(String slug) {
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug).orElse(null);
        if (content == null || !isEligiblePorteCandidate(content)) return false;
        publish(slug);
        return true;
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse unpublish(String slug) {
        SeoCatalogProduct product = requireProduct(slug);
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug)
                .orElseThrow(() -> new ApiNotFoundException("Contenido de producto no encontrado"));
        content.setPublishedSnapshot(null);
        content.setStatus(SeoProductContentStatus.DRAFT);
        content.setPublishedAt(null);
        content.setDraftDirty(false);
        return toDetail(product, contentRepository.save(content));
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse uploadImage(String slug, MultipartFile file, String altText) {
        SeoCatalogProduct product = requireProduct(slug);
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug)
                .orElseGet(() -> contentRepository.save(newContent(product)));
        SeoProductMediaStorage.StoredMedia stored = mediaStorage.store(file);
        try {
            SeoProductImage image = new SeoProductImage();
            image.setContent(content);
            image.setStoredFilename(stored.storedFilename());
            image.setPublicPath(stored.publicPath());
            image.setOriginalFilename(stored.originalFilename());
            image.setAltText(limit(clean(altText), 500));
            image.setSortOrder(content.getImages().size());
            image.setPrimaryImage(content.getImages().isEmpty());
            content.getImages().add(image);
            markDraftChanged(content);
            return toDetail(product, contentRepository.save(content));
        } catch (RuntimeException ex) {
            mediaStorage.deleteQuietly(stored.storedFilename());
            throw ex;
        }
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse updateImageAlt(
            String slug,
            long imageId,
            ProductContentAdminDtos.ImageAltRequest request
    ) {
        SeoProductContent content = requireContent(slug);
        SeoProductImage image = requireImage(content, imageId);
        image.setAltText(limit(clean(request == null ? null : request.altText()), 500));
        markDraftChanged(content);
        return toDetail(content.getSeoCatalogProduct(), contentRepository.save(content));
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse reorderImages(
            String slug,
            ProductContentAdminDtos.ImageOrderRequest request
    ) {
        SeoProductContent content = requireContent(slug);
        List<Long> ids = request == null || request.imageIds() == null ? List.of() : request.imageIds();
        Set<Long> existing = content.getImages().stream().map(SeoProductImage::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() != existing.size() || !existing.equals(new LinkedHashSet<>(ids))) {
            throw new ApiBadRequestException("El orden debe incluir todas las imagenes una sola vez");
        }
        Map<Long, SeoProductImage> byId = content.getImages().stream()
                .collect(Collectors.toMap(SeoProductImage::getId, Function.identity()));
        for (int index = 0; index < ids.size(); index++) {
            byId.get(ids.get(index)).setSortOrder(index);
        }
        markDraftChanged(content);
        return toDetail(content.getSeoCatalogProduct(), contentRepository.save(content));
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse setPrimaryImage(String slug, long imageId) {
        SeoProductContent content = requireContent(slug);
        SeoProductImage selected = requireImage(content, imageId);
        content.getImages().forEach(image -> image.setPrimaryImage(image == selected));
        markDraftChanged(content);
        return toDetail(content.getSeoCatalogProduct(), contentRepository.save(content));
    }

    @Transactional
    public ProductContentAdminDtos.DetailResponse deleteImage(String slug, long imageId) {
        SeoProductContent content = requireContent(slug);
        SeoProductImage image = requireImage(content, imageId);
        boolean retainedByPublication = publishedSnapshotReferences(content, image.getPublicPath());
        boolean wasPrimary = image.isPrimaryImage();
        if (image.isImportedFromPorte() && image.getSourceKey() != null) {
            addExclusion(content, "IMAGE", image.getSourceKey());
        }
        content.getImages().remove(image);
        imageRepository.delete(image);
        for (int index = 0; index < content.getImages().size(); index++) {
            content.getImages().get(index).setSortOrder(index);
        }
        if (wasPrimary && !content.getImages().isEmpty()) {
            content.getImages().get(0).setPrimaryImage(true);
        }
        markDraftChanged(content);
        ProductContentAdminDtos.DetailResponse response = toDetail(
                content.getSeoCatalogProduct(), contentRepository.save(content)
        );
        if (!retainedByPublication) {
            mediaStorage.deleteQuietly(image.getStoredFilename());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public Optional<PublishedProductContent> published(String slug) {
        return contentRepository.findBySeoCatalogProductSlug(slug)
                .filter(content -> content.getStatus() == SeoProductContentStatus.PUBLISHED)
                .map(SeoProductContent::getPublishedSnapshot)
                .filter(snapshot -> snapshot != null && !snapshot.isBlank())
                .map(this::deserialize);
    }

    @Transactional(readOnly = true)
    public List<String> publishedSlugs() {
        return contentRepository.findSlugsWithPublishedSnapshot(SeoProductContentStatus.PUBLISHED);
    }

    private SeoProductContent newContent(SeoCatalogProduct product) {
        SeoProductContent content = new SeoProductContent();
        content.setSeoCatalogProduct(product);
        content.setStatus(SeoProductContentStatus.DRAFT);
        return content;
    }

    private void applyDraft(SeoProductContent content, ProductContentAdminDtos.SaveRequest request) {
        content.setDescription(limit(clean(request.description()), 12000));
        content.setSourceName(limit(clean(request.sourceName()), 255));
        content.setSourceUrl(validSourceUrl(request.sourceUrl()));

        List<SeoProductHighlight> existingHighlights = new ArrayList<>(content.getHighlights());
        List<SeoProductHighlight> nextHighlights = new ArrayList<>();
        List<ProductContentAdminDtos.HighlightInput> highlights = request.highlights() == null
                ? List.of() : request.highlights();
        for (int index = 0; index < highlights.size(); index++) {
            ProductContentAdminDtos.HighlightInput input = highlights.get(index);
            String text = limit(clean(input == null ? null : input.text()), 1000);
            if (text.isBlank()) continue;
            SeoProductHighlight highlight = findHighlight(existingHighlights, input, text, index);
            if (highlight == null) highlight = new SeoProductHighlight();
            existingHighlights.remove(highlight);
            highlight.setContent(content);
            highlight.setText(text);
            highlight.setSortOrder(nextHighlights.size());
            if (highlight.getSourceKey() != null) {
                highlight.setManuallyEdited(!Objects.equals(text, clean(highlight.getSourceText())));
            }
            nextHighlights.add(highlight);
        }
        existingHighlights.stream()
                .filter(item -> item.getSourceKey() != null)
                .forEach(item -> addExclusion(content, "HIGHLIGHT", item.getSourceKey()));
        content.getHighlights().clear();
        content.getHighlights().addAll(nextHighlights);

        List<SeoProductSpecSection> existingSections = new ArrayList<>(content.getSpecSections());
        List<SeoProductSpecSection> nextSections = new ArrayList<>();
        List<ProductContentAdminDtos.SpecSectionInput> sections = request.specSections() == null
                ? List.of() : request.specSections();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            ProductContentAdminDtos.SpecSectionInput input = sections.get(sectionIndex);
            if (input == null) continue;
            List<ProductContentAdminDtos.SpecInput> requestedSpecs = input.specs() == null
                    ? List.of() : input.specs();
            String title = limit(clean(input.title()), 255);
            boolean hasEnteredSpec = requestedSpecs.stream().anyMatch(this::hasEnteredSpec);
            if (title.isBlank() && !hasEnteredSpec) continue;
            if (title.isBlank()) throw new ApiBadRequestException("Cada seccion debe tener un nombre");

            SeoProductSpecSection section = findSection(existingSections, input, title, sectionIndex);
            if (section == null) section = new SeoProductSpecSection();
            existingSections.remove(section);
            section.setContent(content);
            section.setTitle(title);
            section.setSortOrder(nextSections.size());
            if (section.getSourceKey() != null) {
                section.setManuallyEdited(!Objects.equals(title, clean(section.getSourceTitle())));
            }
            List<SeoProductSpec> existingSpecs = new ArrayList<>(section.getSpecs());
            List<SeoProductSpec> nextSpecs = new ArrayList<>();
            for (int specIndex = 0; specIndex < requestedSpecs.size(); specIndex++) {
                ProductContentAdminDtos.SpecInput specInput = requestedSpecs.get(specIndex);
                if (specInput == null || !hasEnteredSpec(specInput)) continue;
                String label = limit(clean(specInput.label()), 255);
                String value = limit(clean(specInput.value()), 2000);
                if (label.isBlank() || value.isBlank()) {
                    throw new ApiBadRequestException("Cada especificacion debe tener etiqueta y valor");
                }
                SeoProductSpec spec = findSpec(existingSpecs, specInput, label, value, specIndex);
                if (spec == null) spec = new SeoProductSpec();
                existingSpecs.remove(spec);
                spec.setSection(section);
                spec.setLabel(label);
                spec.setValue(value);
                spec.setSortOrder(nextSpecs.size());
                if (spec.getSourceKey() != null) {
                    spec.setManuallyEdited(
                            !Objects.equals(label, clean(spec.getSourceLabel()))
                                    || !Objects.equals(value, clean(spec.getSourceValue()))
                    );
                }
                nextSpecs.add(spec);
            }
            existingSpecs.stream()
                    .filter(item -> item.getSourceKey() != null)
                    .forEach(item -> addExclusion(content, "SPEC", item.getSourceKey()));
            section.getSpecs().clear();
            section.getSpecs().addAll(nextSpecs);
            nextSections.add(section);
        }
        existingSections.stream()
                .filter(item -> item.getSourceKey() != null)
                .forEach(item -> addExclusion(content, "SECTION", item.getSourceKey()));
        content.getSpecSections().clear();
        content.getSpecSections().addAll(nextSections);
    }

    private SeoProductHighlight findHighlight(
            List<SeoProductHighlight> existing,
            ProductContentAdminDtos.HighlightInput input,
            String text,
            int index
    ) {
        if (input != null && input.id() != null) {
            Optional<SeoProductHighlight> byId = existing.stream()
                    .filter(item -> input.id().equals(item.getId())).findFirst();
            if (byId.isPresent()) return byId.get();
        }
        Optional<SeoProductHighlight> exact = existing.stream()
                .filter(item -> Objects.equals(clean(item.getText()), text)).findFirst();
        return exact.orElse(existing.isEmpty() ? null : existing.get(0));
    }

    private SeoProductSpecSection findSection(
            List<SeoProductSpecSection> existing,
            ProductContentAdminDtos.SpecSectionInput input,
            String title,
            int index
    ) {
        if (input.id() != null) {
            Optional<SeoProductSpecSection> byId = existing.stream()
                    .filter(item -> input.id().equals(item.getId())).findFirst();
            if (byId.isPresent()) return byId.get();
        }
        Optional<SeoProductSpecSection> exact = existing.stream()
                .filter(item -> Objects.equals(clean(item.getTitle()), title)).findFirst();
        return exact.orElse(existing.isEmpty() ? null : existing.get(0));
    }

    private SeoProductSpec findSpec(
            List<SeoProductSpec> existing,
            ProductContentAdminDtos.SpecInput input,
            String label,
            String value,
            int index
    ) {
        if (input.id() != null) {
            Optional<SeoProductSpec> byId = existing.stream()
                    .filter(item -> input.id().equals(item.getId())).findFirst();
            if (byId.isPresent()) return byId.get();
        }
        Optional<SeoProductSpec> exact = existing.stream()
                .filter(item -> Objects.equals(clean(item.getLabel()), label)
                        && Objects.equals(clean(item.getValue()), value))
                .findFirst();
        return exact.orElse(existing.isEmpty() ? null : existing.get(0));
    }

    private void addExclusion(SeoProductContent content, String itemType, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return;
        boolean exists = content.getImportExclusions().stream()
                .anyMatch(item -> itemType.equals(item.getItemType()) && sourceKey.equals(item.getSourceKey()));
        if (exists) return;
        SeoProductImportExclusion exclusion = new SeoProductImportExclusion();
        exclusion.setContent(content);
        exclusion.setItemType(itemType);
        exclusion.setSourceKey(sourceKey);
        content.getImportExclusions().add(exclusion);
    }

    private boolean hasEnteredSpec(ProductContentAdminDtos.SpecInput spec) {
        return spec != null && (!clean(spec.label()).isBlank() || !clean(spec.value()).isBlank());
    }

    private PublishedProductContent snapshot(SeoProductContent content) {
        List<PublishedProductContent.Highlight> highlights = content.getHighlights().stream()
                .sorted(Comparator.comparingInt(SeoProductHighlight::getSortOrder))
                .map(item -> new PublishedProductContent.Highlight(item.getText()))
                .toList();
        List<PublishedProductContent.SpecSection> sections = content.getSpecSections().stream()
                .sorted(Comparator.comparingInt(SeoProductSpecSection::getSortOrder))
                .map(section -> new PublishedProductContent.SpecSection(
                        section.getTitle(),
                        section.getSpecs().stream()
                                .sorted(Comparator.comparingInt(SeoProductSpec::getSortOrder))
                                .map(spec -> new PublishedProductContent.Spec(spec.getLabel(), spec.getValue()))
                                .toList()
                ))
                .toList();
        List<PublishedProductContent.Image> images = content.getImages().stream()
                .sorted(Comparator.comparing(SeoProductImage::isPrimaryImage).reversed()
                        .thenComparingInt(SeoProductImage::getSortOrder))
                .map(image -> new PublishedProductContent.Image(
                        image.getPublicPath(), image.getAltText(), image.isPrimaryImage()
                ))
                .toList();
        return new PublishedProductContent(
                content.getDescription(), content.getSourceUrl(), content.getSourceName(), highlights, sections, images
        );
    }

    private ProductContentAdminDtos.ListItem toListItem(
            SeoCatalogProduct product,
            SeoProductContent content
    ) {
        return new ProductContentAdminDtos.ListItem(
                product.getId(), product.getMarca(), product.getModelo(), product.getSlug(), product.isActive(),
                content == null ? null : content.getStatus(),
                content == null ? 0 : content.getImages().size(),
                content == null ? null : content.getUpdatedAt(),
                content != null && content.isDraftDirty()
        );
    }

    private ProductContentAdminDtos.DetailResponse toDetail(
            SeoCatalogProduct product,
            SeoProductContent content
    ) {
        if (content == null) {
            return new ProductContentAdminDtos.DetailResponse(
                    product.getId(), product.getMarca(), product.getModelo(), product.getSlug(), product.isActive(),
                    null, "", "", "", List.of(), List.of(), List.of(), null, null, null, false
            );
        }
        List<ProductContentAdminDtos.HighlightResponse> highlights = content.getHighlights().stream()
                .sorted(Comparator.comparingInt(SeoProductHighlight::getSortOrder))
                .map(item -> new ProductContentAdminDtos.HighlightResponse(
                        item.getId(), item.getText(), item.getSortOrder()
                )).toList();
        List<ProductContentAdminDtos.SpecSectionResponse> sections = content.getSpecSections().stream()
                .sorted(Comparator.comparingInt(SeoProductSpecSection::getSortOrder))
                .map(section -> new ProductContentAdminDtos.SpecSectionResponse(
                        section.getId(), section.getTitle(), section.getSortOrder(),
                        section.getSpecs().stream()
                                .sorted(Comparator.comparingInt(SeoProductSpec::getSortOrder))
                                .map(spec -> new ProductContentAdminDtos.SpecResponse(
                                        spec.getId(), spec.getLabel(), spec.getValue(), spec.getSortOrder()
                                )).toList()
                )).toList();
        List<ProductContentAdminDtos.ImageResponse> images = content.getImages().stream()
                .sorted(Comparator.comparing(SeoProductImage::isPrimaryImage).reversed()
                        .thenComparingInt(SeoProductImage::getSortOrder))
                .map(image -> new ProductContentAdminDtos.ImageResponse(
                        image.getId(), image.getPublicPath(), image.getOriginalFilename(), image.getAltText(),
                        image.getSortOrder(), image.isPrimaryImage(), image.getCreatedAt()
                )).toList();
        return new ProductContentAdminDtos.DetailResponse(
                product.getId(), product.getMarca(), product.getModelo(), product.getSlug(), product.isActive(),
                content.getStatus(), content.getDescription(), content.getSourceUrl(), content.getSourceName(),
                highlights, sections, images, content.getCreatedAt(), content.getUpdatedAt(), content.getPublishedAt(),
                content.isDraftDirty()
        );
    }

    private boolean matches(SeoCatalogProduct product, String query) {
        return (clean(product.getMarca()) + " " + clean(product.getModelo()) + " " + clean(product.getSlug()))
                .toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesState(SeoProductContent content, String state) {
        return switch (state) {
            case "NONE", "SIN_CONTENIDO" -> content == null;
            case "DRAFT", "BORRADOR" -> content != null && content.getStatus() == SeoProductContentStatus.DRAFT;
            case "PUBLISHED", "PUBLICADO" -> content != null && content.getStatus() == SeoProductContentStatus.PUBLISHED;
            default -> true;
        };
    }

    private SeoCatalogProduct requireProduct(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiNotFoundException("Producto SEO no encontrado"));
    }

    private SeoProductContent requireContent(String slug) {
        return contentRepository.findBySeoCatalogProductSlug(slug)
                .orElseThrow(() -> new ApiNotFoundException("Contenido de producto no encontrado"));
    }

    private SeoProductImage requireImage(SeoProductContent content, long imageId) {
        return imageRepository.findByIdAndContentId(imageId, content.getId())
                .orElseThrow(() -> new ApiNotFoundException("Imagen de producto no encontrada"));
    }

    private void markDraftChanged(SeoProductContent content) {
        if (content.getPublishedSnapshot() == null) {
            content.setStatus(SeoProductContentStatus.DRAFT);
            content.setDraftDirty(false);
        } else {
            content.setStatus(SeoProductContentStatus.PUBLISHED);
            content.setDraftDirty(true);
        }
    }

    private boolean publishedSnapshotReferences(SeoProductContent content, String publicPath) {
        if (content.getPublishedSnapshot() == null || publicPath == null) return false;
        return deserialize(content.getPublishedSnapshot()).images().stream()
                .anyMatch(image -> publicPath.equals(image.publicPath()));
    }

    private boolean isEligiblePorteCandidate(SeoProductContent content) {
        SeoCatalogProduct product = content.getSeoCatalogProduct();
        return product.getOrigen() == CatalogProductOrigin.TIENDA_PORTE
                && product.isActive()
                && !hasPublishedSnapshot(content)
                && hasValidPorteSynchronization(content)
                && hasUsefulContent(content);
    }

    private boolean hasPublishedSnapshot(SeoProductContent content) {
        return content.getPublishedSnapshot() != null && !content.getPublishedSnapshot().isBlank();
    }

    private boolean hasValidPorteSynchronization(SeoProductContent content) {
        return "SINCRONIZADO".equals(content.getPorteSyncStatus())
                && content.getPorteReferenceId() != null
                && content.getPorteLastSyncedAt() != null;
    }

    private boolean hasUsefulContent(SeoProductContent content) {
        if (!clean(content.getDescription()).isBlank()) return true;
        if (content.getHighlights().stream().anyMatch(item -> !clean(item.getText()).isBlank())) return true;
        if (content.getSpecSections().stream().flatMap(section -> section.getSpecs().stream())
                .anyMatch(spec -> !clean(spec.getLabel()).isBlank() && !clean(spec.getValue()).isBlank())) return true;
        return content.getImages().stream().anyMatch(image -> !clean(image.getPublicPath()).isBlank());
    }

    public record PorteBulkPublishPlan(
            PorteBulkPublishDtos.PreviewResponse preview,
            List<String> eligibleSlugs
    ) {}

    private String serialize(PublishedProductContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw new ApiInternalException("No se pudo publicar el contenido del producto");
        }
    }

    private PublishedProductContent deserialize(String value) {
        try {
            return objectMapper.readValue(value, PublishedProductContent.class);
        } catch (JsonProcessingException ex) {
            throw new ApiInternalException("El contenido publicado del producto no es valido");
        }
    }

    private String validSourceUrl(String value) {
        String cleaned = limit(clean(value), 2048);
        if (cleaned.isBlank()) return "";
        try {
            URI uri = new URI(cleaned);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new ApiBadRequestException("La URL oficial debe usar http o https");
            }
            return cleaned;
        } catch (URISyntaxException ex) {
            throw new ApiBadRequestException("La URL oficial no es valida");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        throw new ApiBadRequestException("Uno de los campos supera el largo permitido");
    }
}
