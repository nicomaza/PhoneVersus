package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.HomeBannerDtos;
import celulares.cordobacelulares.entities.HomeBanner;
import celulares.cordobacelulares.repository.HomeBannerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeBannerServiceTest {

    @Mock
    private HomeBannerRepository repository;
    @Mock
    private HomeBannerMediaStorage storage;

    private HomeBannerService service;
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        service = new HomeBannerService(repository, storage);
    }

    @Test
    void createsAndEditsBannerMetadata() {
        MockMultipartFile desktop = file("desktop.png");
        when(storage.store(desktop)).thenReturn(media("desktop.png"));
        when(repository.findFirstByOrderBySortOrderDescIdDesc()).thenReturn(null);
        when(repository.saveAndFlush(any(HomeBanner.class))).thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        HomeBannerDtos.AdminResponse created = service.create(
                " Oferta ", " Banner principal ", "https://example.com/oferta", desktop, null
        );

        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.title()).isEqualTo("Oferta");
        assertThat(created.mobileImageUrl()).isEqualTo(created.desktopImageUrl());

        HomeBanner stored = banner(1L, "Oferta", false, 0, "desktop.png", null);
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        HomeBannerDtos.AdminResponse edited = service.updateMetadata(
                1L, new HomeBannerDtos.MetadataRequest("Nueva", "Nueva descripcion", null)
        );

        assertThat(edited.title()).isEqualTo("Nueva");
        assertThat(edited.linkUrl()).isNull();
    }

    @Test
    void activatesAndReordersAllBanners() {
        HomeBanner first = banner(1L, "Primero", false, 0, "one.png", null);
        HomeBanner second = banner(2L, "Segundo", false, 1, "two.png", null);
        when(repository.findById(1L)).thenReturn(Optional.of(first));
        when(repository.saveAndFlush(any(HomeBanner.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.setActive(1L, true).active()).isTrue();

        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(first, second));
        when(repository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<HomeBannerDtos.AdminResponse> reordered = service.reorder(List.of(2L, 1L));

        assertThat(reordered).extracting(HomeBannerDtos.AdminResponse::id).containsExactly(2L, 1L);
        assertThat(second.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
    }

    @Test
    void publicListContainsOnlyRepositoryResultsAndFallsBackToDesktop() {
        HomeBanner active = banner(7L, "Activo", true, 0, "desktop.png", null);
        when(repository.findAllByActiveTrueOrderBySortOrderAscIdAsc()).thenReturn(List.of(active));

        List<HomeBannerDtos.PublicResponse> result = service.listActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mobileImageUrl()).isEqualTo("/home-banner-media/desktop.png");
    }

    @Test
    void replacementAndDeletionCleanPreviousFiles() {
        HomeBanner banner = banner(1L, "Banner", true, 0, "old-desktop.png", "old-mobile.png");
        when(repository.findById(1L)).thenReturn(Optional.of(banner));
        when(storage.store(any())).thenReturn(media("new-desktop.png"));
        when(repository.saveAndFlush(any(HomeBanner.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.replaceDesktop(1L, file("new.png"));
        verify(storage).deleteQuietly("old-desktop.png");

        service.delete(1L);
        verify(repository).delete(banner);
        verify(storage).deleteQuietly("new-desktop.png");
        verify(storage).deleteQuietly("old-mobile.png");
    }

    private HomeBanner persisted(HomeBanner banner) {
        if (banner.getId() == null) banner.setId(ids.getAndIncrement());
        return banner;
    }

    private HomeBanner banner(
            Long id, String title, boolean active, int order, String desktopFilename, String mobileFilename
    ) {
        HomeBanner banner = new HomeBanner();
        banner.setId(id);
        banner.setTitle(title);
        banner.setAltText(title);
        banner.setActive(active);
        banner.setSortOrder(order);
        banner.setDesktopStoredFilename(desktopFilename);
        banner.setDesktopPublicPath("/home-banner-media/" + desktopFilename);
        banner.setDesktopOriginalFilename(desktopFilename);
        banner.setDesktopMimeType("image/png");
        if (mobileFilename != null) {
            banner.setMobileStoredFilename(mobileFilename);
            banner.setMobilePublicPath("/home-banner-media/" + mobileFilename);
            banner.setMobileOriginalFilename(mobileFilename);
            banner.setMobileMimeType("image/png");
        }
        return banner;
    }

    private HomeBannerMediaStorage.StoredMedia media(String filename) {
        return new HomeBannerMediaStorage.StoredMedia(
                filename, "/home-banner-media/" + filename, filename, "image/png"
        );
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile("image", filename, "image/png", new byte[]{1});
    }
}
