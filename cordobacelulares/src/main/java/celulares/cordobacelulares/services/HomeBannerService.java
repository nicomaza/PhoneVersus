package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.HomeBannerDtos;
import celulares.cordobacelulares.entities.HomeBanner;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.repository.HomeBannerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class HomeBannerService {

    private final HomeBannerRepository repository;
    private final HomeBannerMediaStorage storage;

    public HomeBannerService(HomeBannerRepository repository, HomeBannerMediaStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<HomeBannerDtos.AdminResponse> listAll() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toAdminResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<HomeBannerDtos.PublicResponse> listActive() {
        return repository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream().map(this::toPublicResponse).toList();
    }

    @Transactional
    public HomeBannerDtos.AdminResponse create(
            String title,
            String altText,
            String linkUrl,
            MultipartFile desktopImage,
            MultipartFile mobileImage
    ) {
        Metadata metadata = validateMetadata(title, altText, linkUrl);
        HomeBannerMediaStorage.StoredMedia desktop = storage.store(desktopImage);
        HomeBannerMediaStorage.StoredMedia mobile = null;
        try {
            if (mobileImage != null && !mobileImage.isEmpty()) {
                mobile = storage.store(mobileImage);
            }

            HomeBanner banner = new HomeBanner();
            applyMetadata(banner, metadata);
            applyDesktop(banner, desktop);
            applyMobile(banner, mobile);
            banner.setActive(false);
            HomeBanner last = repository.findFirstByOrderBySortOrderDescIdDesc();
            banner.setSortOrder(nextSortOrder(last));

            HomeBanner saved = repository.saveAndFlush(banner);
            cleanupOnRollback(desktop.storedFilename());
            if (mobile != null) cleanupOnRollback(mobile.storedFilename());
            return toAdminResponse(saved);
        } catch (RuntimeException ex) {
            storage.deleteQuietly(desktop.storedFilename());
            if (mobile != null) storage.deleteQuietly(mobile.storedFilename());
            throw ex;
        }
    }

    @Transactional
    public HomeBannerDtos.AdminResponse updateMetadata(Long id, HomeBannerDtos.MetadataRequest request) {
        HomeBanner banner = get(id);
        applyMetadata(banner, validateMetadata(request.title(), request.altText(), request.linkUrl()));
        return toAdminResponse(repository.saveAndFlush(banner));
    }

    @Transactional
    public HomeBannerDtos.AdminResponse setActive(Long id, boolean active) {
        HomeBanner banner = get(id);
        banner.setActive(active);
        return toAdminResponse(repository.saveAndFlush(banner));
    }

    @Transactional
    public List<HomeBannerDtos.AdminResponse> reorder(List<Long> bannerIds) {
        if (bannerIds == null) throw new ApiBadRequestException("El orden de banners es obligatorio");
        List<HomeBanner> banners = repository.findAllByOrderBySortOrderAscIdAsc();
        Set<Long> expectedIds = banners.stream().map(HomeBanner::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> suppliedIds = new HashSet<>(bannerIds);
        if (bannerIds.stream().anyMatch(java.util.Objects::isNull)
                || suppliedIds.size() != bannerIds.size()
                || !suppliedIds.equals(expectedIds)) {
            throw new ApiBadRequestException("El orden debe incluir cada banner exactamente una vez");
        }

        java.util.Map<Long, HomeBanner> byId = banners.stream()
                .collect(java.util.stream.Collectors.toMap(HomeBanner::getId, banner -> banner));
        for (int index = 0; index < bannerIds.size(); index++) {
            byId.get(bannerIds.get(index)).setSortOrder(index);
        }
        repository.saveAllAndFlush(banners);
        return bannerIds.stream().map(byId::get).map(this::toAdminResponse).toList();
    }

    @Transactional
    public HomeBannerDtos.AdminResponse replaceDesktop(Long id, MultipartFile image) {
        HomeBanner banner = get(id);
        HomeBannerMediaStorage.StoredMedia replacement = storage.store(image);
        String previousFilename = banner.getDesktopStoredFilename();
        try {
            applyDesktop(banner, replacement);
            HomeBanner saved = repository.saveAndFlush(banner);
            cleanupOnCommit(previousFilename);
            cleanupOnRollback(replacement.storedFilename());
            return toAdminResponse(saved);
        } catch (RuntimeException ex) {
            storage.deleteQuietly(replacement.storedFilename());
            throw ex;
        }
    }

    @Transactional
    public HomeBannerDtos.AdminResponse replaceMobile(Long id, MultipartFile image) {
        HomeBanner banner = get(id);
        HomeBannerMediaStorage.StoredMedia replacement = storage.store(image);
        String previousFilename = banner.getMobileStoredFilename();
        try {
            applyMobile(banner, replacement);
            HomeBanner saved = repository.saveAndFlush(banner);
            cleanupOnCommit(previousFilename);
            cleanupOnRollback(replacement.storedFilename());
            return toAdminResponse(saved);
        } catch (RuntimeException ex) {
            storage.deleteQuietly(replacement.storedFilename());
            throw ex;
        }
    }

    @Transactional
    public HomeBannerDtos.AdminResponse removeMobile(Long id) {
        HomeBanner banner = get(id);
        String previousFilename = banner.getMobileStoredFilename();
        applyMobile(banner, null);
        HomeBanner saved = repository.saveAndFlush(banner);
        cleanupOnCommit(previousFilename);
        return toAdminResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        HomeBanner banner = get(id);
        String desktopFilename = banner.getDesktopStoredFilename();
        String mobileFilename = banner.getMobileStoredFilename();
        repository.delete(banner);
        repository.flush();
        cleanupOnCommit(desktopFilename);
        cleanupOnCommit(mobileFilename);
    }

    private HomeBanner get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiNotFoundException("Banner no encontrado"));
    }

    private int nextSortOrder(HomeBanner last) {
        if (last == null) return 0;
        try {
            return Math.addExact(last.getSortOrder(), 1);
        } catch (ArithmeticException ex) {
            throw new ApiBadRequestException("No se puede asignar un nuevo orden de banner");
        }
    }

    private Metadata validateMetadata(String title, String altText, String linkUrl) {
        String cleanTitle = requiredText(title, 255, "El titulo es obligatorio");
        String cleanAltText = requiredText(altText, 500, "La descripcion alternativa es obligatoria");
        String cleanLink = optionalText(linkUrl, 2048);
        if (cleanLink != null) validateLink(cleanLink);
        return new Metadata(cleanTitle, cleanAltText, cleanLink);
    }

    private String requiredText(String value, int maxLength, String requiredMessage) {
        if (value == null || value.isBlank()) throw new ApiBadRequestException(requiredMessage);
        String clean = value.trim();
        if (clean.length() > maxLength) throw new ApiBadRequestException("El texto supera el largo permitido");
        return clean;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > maxLength) throw new ApiBadRequestException("El enlace supera el largo permitido");
        return clean;
    }

    private void validateLink(String link) {
        try {
            URI uri = new URI(link);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new ApiBadRequestException("El enlace debe ser una URL HTTP o HTTPS absoluta");
            }
        } catch (URISyntaxException ex) {
            throw new ApiBadRequestException("El enlace no es una URL valida");
        }
    }

    private void applyMetadata(HomeBanner banner, Metadata metadata) {
        banner.setTitle(metadata.title());
        banner.setAltText(metadata.altText());
        banner.setLinkUrl(metadata.linkUrl());
    }

    private void applyDesktop(HomeBanner banner, HomeBannerMediaStorage.StoredMedia media) {
        banner.setDesktopStoredFilename(media.storedFilename());
        banner.setDesktopPublicPath(media.publicPath());
        banner.setDesktopOriginalFilename(media.originalFilename());
        banner.setDesktopMimeType(media.mimeType());
    }

    private void applyMobile(HomeBanner banner, HomeBannerMediaStorage.StoredMedia media) {
        banner.setMobileStoredFilename(media == null ? null : media.storedFilename());
        banner.setMobilePublicPath(media == null ? null : media.publicPath());
        banner.setMobileOriginalFilename(media == null ? null : media.originalFilename());
        banner.setMobileMimeType(media == null ? null : media.mimeType());
    }

    private HomeBannerDtos.AdminResponse toAdminResponse(HomeBanner banner) {
        return new HomeBannerDtos.AdminResponse(
                banner.getId(), banner.getTitle(), banner.getAltText(), banner.getLinkUrl(),
                banner.isActive(), banner.getSortOrder(), banner.getDesktopPublicPath(),
                banner.getMobilePublicPath() == null ? banner.getDesktopPublicPath() : banner.getMobilePublicPath(),
                banner.getCreatedAt(), banner.getUpdatedAt()
        );
    }

    private HomeBannerDtos.PublicResponse toPublicResponse(HomeBanner banner) {
        String mobileUrl = banner.getMobilePublicPath() == null
                ? banner.getDesktopPublicPath() : banner.getMobilePublicPath();
        return new HomeBannerDtos.PublicResponse(
                banner.getTitle(), banner.getAltText(), banner.getLinkUrl(), banner.getDesktopPublicPath(), mobileUrl
        );
    }

    private void cleanupOnCommit(String filename) {
        if (filename == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.deleteQuietly(filename);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) storage.deleteQuietly(filename);
            }
        });
    }

    private void cleanupOnRollback(String filename) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.deleteQuietly(filename);
            }
        });
    }

    private record Metadata(String title, String altText, String linkUrl) {}
}
