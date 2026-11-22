package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductSpecsResponse;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.entities.SeoProductContent;
import celulares.cordobacelulares.entities.SeoProductContentStatus;
import celulares.cordobacelulares.entities.SeoProductHighlight;
import celulares.cordobacelulares.entities.SeoProductImage;
import celulares.cordobacelulares.entities.SeoProductSpec;
import celulares.cordobacelulares.entities.SeoProductSpecSection;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.repository.SeoProductContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeoPorteContentImporter {

    private final SeoCatalogProductRepository productRepository;
    private final SeoProductContentRepository contentRepository;
    private final TiendaPorteProductImageDownloader imageDownloader;
    private final SeoProductMediaStorage mediaStorage;

    public SeoPorteContentImporter(
            SeoCatalogProductRepository productRepository,
            SeoProductContentRepository contentRepository,
            TiendaPorteProductImageDownloader imageDownloader,
            SeoProductMediaStorage mediaStorage
    ) {
        this.productRepository = productRepository;
        this.contentRepository = contentRepository;
        this.imageDownloader = imageDownloader;
        this.mediaStorage = mediaStorage;
    }

    @Transactional
    public ImportOutcome importProduct(
            String slug,
            long referenceId,
            TiendaPorteProductSpecsResponse response,
            List<String> imageUrls
    ) {
        SeoCatalogProduct product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiNotFoundException("Producto SEO no encontrado"));
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug).orElse(null);
        boolean created = content == null;
        if (created) {
            content = new SeoProductContent();
            content.setSeoCatalogProduct(product);
            content.setStatus(SeoProductContentStatus.DRAFT);
        }

        List<String> normalizedImages = normalizeImageUrls(imageUrls);
        if (!created && sameHash(content, referenceId, response.getHash())
                && hasEveryImportedImage(content, normalizedImages)
                && !sourceNeedsMerge(content, response)) {
            return ImportOutcome.UNCHANGED;
        }

        boolean changed = false;
        List<String> newlyStoredFiles = new ArrayList<>();
        try {
            changed |= mergeDescription(content, response);
            changed |= mergeHighlights(content, response);
            changed |= mergeSections(content, response);
            changed |= mergeImages(content, product, normalizedImages, newlyStoredFiles);

            content.setPorteReferenceId(referenceId);
            content.setPorteHash(clean(response.getHash()));
            content.setPorteLastSyncedAt(Instant.now());
            content.setPorteSyncStatus("SINCRONIZADO");
            content.setPorteSyncError(null);
            if (changed) markDraftChanged(content);
            contentRepository.save(content);
            return created ? ImportOutcome.IMPORTED : (changed ? ImportOutcome.UPDATED : ImportOutcome.UNCHANGED);
        } catch (RuntimeException ex) {
            newlyStoredFiles.forEach(mediaStorage::deleteQuietly);
            throw ex;
        }
    }

    @Transactional
    public void recordMissingSheet(String slug, long referenceId, String status, String error) {
        SeoCatalogProduct product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiNotFoundException("Producto SEO no encontrado"));
        SeoProductContent content = contentRepository.findBySeoCatalogProductSlug(slug).orElseGet(() -> {
            SeoProductContent created = new SeoProductContent();
            created.setSeoCatalogProduct(product);
            created.setStatus(SeoProductContentStatus.DRAFT);
            return created;
        });
        content.setPorteReferenceId(referenceId);
        content.setPorteLastSyncedAt(Instant.now());
        content.setPorteSyncStatus(status);
        content.setPorteSyncError(limit(clean(error), 1000));
        contentRepository.save(content);
    }

    private boolean mergeDescription(SeoProductContent content, TiendaPorteProductSpecsResponse response) {
        TiendaPorteProductSpecsResponse.Sheet sheet = response.getSheet();
        String subtitle = sheet == null ? "" : clean(sheet.getSubtitle());
        boolean changed = false;
        if (clean(content.getDescription()).isBlank() && !subtitle.isBlank()) {
            content.setDescription(limit(subtitle, 12000));
            changed = true;
        }
        changed |= mergeSource(content, response);
        return changed;
    }

    private boolean mergeSource(SeoProductContent content, TiendaPorteProductSpecsResponse response) {
        if (!sourceNeedsMerge(content, response)) return false;
        String verifiedUrl = verifiedGsmaArenaUrl(response);
        content.setSourceName(verifiedUrl.isBlank() ? null : "GSMArena");
        content.setSourceUrl(verifiedUrl.isBlank() ? null : verifiedUrl);
        return true;
    }

    private boolean sourceNeedsMerge(SeoProductContent content, TiendaPorteProductSpecsResponse response) {
        String currentName = clean(content.getSourceName());
        String currentUrl = clean(content.getSourceUrl());
        boolean legacyPorteSource = "tienda porte".equalsIgnoreCase(currentName);
        boolean emptySource = currentName.isBlank() && currentUrl.isBlank();
        if (!legacyPorteSource && !emptySource) return false;

        String verifiedUrl = verifiedGsmaArenaUrl(response);
        if (legacyPorteSource) return true;
        return !verifiedUrl.isBlank();
    }

    private String verifiedGsmaArenaUrl(TiendaPorteProductSpecsResponse response) {
        TiendaPorteProductSpecsResponse.Source source = response.getSheet() == null
                ? null : response.getSheet().getSource();
        if (source == null || !isGsmaArenaDomain(source.getDomain())) return "";
        try {
            URI uri = URI.create(clean(source.getUrl()));
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || !isGsmaArenaDomain(host)) {
                return "";
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private boolean isGsmaArenaDomain(String value) {
        String domain = clean(value).toLowerCase(Locale.ROOT);
        return domain.equals("gsmarena.com") || domain.endsWith(".gsmarena.com");
    }

    private boolean mergeHighlights(SeoProductContent content, TiendaPorteProductSpecsResponse response) {
        List<TiendaPorteProductSpecsResponse.Highlight> incoming = response.getSheet() == null
                || response.getSheet().getHighlights() == null
                ? List.of() : response.getSheet().getHighlights();
        Map<String, SeoProductHighlight> existing = content.getHighlights().stream()
                .filter(item -> item.getSourceKey() != null)
                .collect(Collectors.toMap(SeoProductHighlight::getSourceKey, Function.identity(), (a, b) -> a));
        Set<String> excluded = exclusions(content, "HIGHLIGHT");
        Map<String, Integer> occurrences = new HashMap<>();
        boolean changed = false;

        for (TiendaPorteProductSpecsResponse.Highlight item : incoming) {
            if (item == null) continue;
            String text = highlightText(item);
            if (text.isBlank()) continue;
            String base = firstNonBlank(item.getKey(), item.getLabel(), text);
            String key = stableKey("highlight", base, occurrences);
            if (excluded.contains(key)) continue;
            SeoProductHighlight target = existing.get(key);
            if (target == null) {
                target = new SeoProductHighlight();
                target.setContent(content);
                target.setSourceKey(key);
                target.setText(limit(text, 1000));
                target.setSourceText(limit(text, 1000));
                target.setSortOrder(content.getHighlights().size());
                content.getHighlights().add(target);
                changed = true;
            } else {
                if (!target.isManuallyEdited() && !clean(target.getText()).equals(text)) {
                    target.setText(limit(text, 1000));
                    changed = true;
                }
                if (!clean(target.getSourceText()).equals(text)) {
                    target.setSourceText(limit(text, 1000));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean mergeSections(SeoProductContent content, TiendaPorteProductSpecsResponse response) {
        List<TiendaPorteProductSpecsResponse.Section> incoming = response.getSheet() == null
                || response.getSheet().getSections() == null
                ? List.of() : response.getSheet().getSections();
        Map<String, SeoProductSpecSection> existing = content.getSpecSections().stream()
                .filter(item -> item.getSourceKey() != null)
                .collect(Collectors.toMap(SeoProductSpecSection::getSourceKey, Function.identity(), (a, b) -> a));
        Set<String> excludedSections = exclusions(content, "SECTION");
        Set<String> excludedSpecs = exclusions(content, "SPEC");
        Map<String, Integer> sectionOccurrences = new HashMap<>();
        boolean changed = false;

        for (TiendaPorteProductSpecsResponse.Section item : incoming) {
            if (item == null) continue;
            String title = clean(item.getTitle());
            if (title.isBlank()) title = "Caracteristicas";
            String sectionKey = stableKey("section", firstNonBlank(item.getKey(), title), sectionOccurrences);
            if (excludedSections.contains(sectionKey)) continue;
            SeoProductSpecSection section = existing.get(sectionKey);
            if (section == null) {
                section = new SeoProductSpecSection();
                section.setContent(content);
                section.setSourceKey(sectionKey);
                section.setTitle(limit(title, 255));
                section.setSourceTitle(limit(title, 255));
                section.setSortOrder(content.getSpecSections().size());
                content.getSpecSections().add(section);
                changed = true;
            } else {
                if (!section.isManuallyEdited() && !clean(section.getTitle()).equals(title)) {
                    section.setTitle(limit(title, 255));
                    changed = true;
                }
                if (!clean(section.getSourceTitle()).equals(title)) {
                    section.setSourceTitle(limit(title, 255));
                    changed = true;
                }
            }
            changed |= mergeSpecs(section, item.getRows(), excludedSpecs);
        }
        return changed;
    }

    private boolean mergeSpecs(
            SeoProductSpecSection section,
            List<TiendaPorteProductSpecsResponse.Row> rows,
            Set<String> excluded
    ) {
        Map<String, SeoProductSpec> existing = section.getSpecs().stream()
                .filter(item -> item.getSourceKey() != null)
                .collect(Collectors.toMap(SeoProductSpec::getSourceKey, Function.identity(), (a, b) -> a));
        Map<String, Integer> occurrences = new HashMap<>();
        boolean changed = false;
        for (TiendaPorteProductSpecsResponse.Row row : rows == null ? List.<TiendaPorteProductSpecsResponse.Row>of() : rows) {
            if (row == null) continue;
            String label = clean(row.getLabel());
            String value = clean(row.getValue());
            if (label.isBlank() || value.isBlank()) continue;
            String rowBase = firstNonBlank(row.getKey(), label);
            String key = section.getSourceKey() + "/" + stableKey("row", rowBase, occurrences);
            if (excluded.contains(key)) continue;
            SeoProductSpec target = existing.get(key);
            if (target == null) {
                target = new SeoProductSpec();
                target.setSection(section);
                target.setSourceKey(key);
                target.setLabel(limit(label, 255));
                target.setValue(limit(value, 2000));
                target.setSourceLabel(limit(label, 255));
                target.setSourceValue(limit(value, 2000));
                target.setSortOrder(section.getSpecs().size());
                section.getSpecs().add(target);
                changed = true;
            } else {
                if (!target.isManuallyEdited()) {
                    if (!clean(target.getLabel()).equals(label)) {
                        target.setLabel(limit(label, 255));
                        changed = true;
                    }
                    if (!clean(target.getValue()).equals(value)) {
                        target.setValue(limit(value, 2000));
                        changed = true;
                    }
                }
                if (!clean(target.getSourceLabel()).equals(label)) {
                    target.setSourceLabel(limit(label, 255));
                    changed = true;
                }
                if (!clean(target.getSourceValue()).equals(value)) {
                    target.setSourceValue(limit(value, 2000));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean mergeImages(
            SeoProductContent content,
            SeoCatalogProduct product,
            List<String> imageUrls,
            List<String> newlyStoredFiles
    ) {
        Set<String> excluded = exclusions(content, "IMAGE");
        Set<String> existingUrls = content.getImages().stream()
                .filter(SeoProductImage::isImportedFromPorte)
                .map(SeoProductImage::getSourceUrl)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        boolean changed = false;
        for (String url : imageUrls) {
            String key = "image:" + sha256(url);
            if (excluded.contains(key) || existingUrls.contains(url)) continue;
            SeoProductMediaStorage.StoredMedia stored = imageDownloader.download(url);
            newlyStoredFiles.add(stored.storedFilename());
            SeoProductImage image = new SeoProductImage();
            image.setContent(content);
            image.setStoredFilename(stored.storedFilename());
            image.setPublicPath(stored.publicPath());
            image.setOriginalFilename(stored.originalFilename());
            image.setAltText(limit(clean(product.getMarca() + " " + product.getModelo()), 500));
            image.setSortOrder(content.getImages().size());
            image.setPrimaryImage(content.getImages().isEmpty());
            image.setImportedFromPorte(true);
            image.setSourceKey(key);
            image.setSourceUrl(url);
            content.getImages().add(image);
            existingUrls.add(url);
            changed = true;
        }
        return changed;
    }

    private Set<String> exclusions(SeoProductContent content, String type) {
        return content.getImportExclusions().stream()
                .filter(item -> type.equals(item.getItemType()))
                .map(item -> item.getSourceKey())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean sameHash(SeoProductContent content, long referenceId, String hash) {
        String normalized = clean(hash);
        return !normalized.isBlank()
                && content.getPorteReferenceId() != null
                && content.getPorteReferenceId() == referenceId
                && normalized.equals(clean(content.getPorteHash()));
    }

    private boolean hasEveryImportedImage(SeoProductContent content, List<String> imageUrls) {
        Set<String> existing = content.getImages().stream()
                .filter(SeoProductImage::isImportedFromPorte)
                .map(SeoProductImage::getSourceUrl)
                .collect(Collectors.toSet());
        Set<String> excludedKeys = exclusions(content, "IMAGE");
        return imageUrls.stream().allMatch(url -> existing.contains(url)
                || excludedKeys.contains("image:" + sha256(url)));
    }

    private List<String> normalizeImageUrls(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(this::clean).filter(value -> !value.isBlank()).distinct().toList();
    }

    private String highlightText(TiendaPorteProductSpecsResponse.Highlight item) {
        String label = clean(item.getLabel());
        String value = clean(item.getValue());
        if (!label.isBlank() && !value.isBlank()) return label + ": " + value;
        return firstNonBlank(value, label);
    }

    private String stableKey(String prefix, String value, Map<String, Integer> occurrences) {
        String normalized = normalizeKey(value);
        int occurrence = occurrences.merge(normalized, 1, Integer::sum);
        return prefix + ":" + normalized + ":" + occurrence;
    }

    private String normalizeKey(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "sin-clave" : normalized;
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
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

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) if (!clean(value).isBlank()) return clean(value);
        }
        return "";
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String limit(String value, int max) {
        String cleaned = clean(value);
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    public enum ImportOutcome { IMPORTED, UPDATED, UNCHANGED }
}
