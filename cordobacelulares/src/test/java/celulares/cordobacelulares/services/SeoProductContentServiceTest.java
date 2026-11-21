package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.ProductContentAdminDtos;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.entities.SeoProductContent;
import celulares.cordobacelulares.entities.SeoProductContentStatus;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.repository.SeoProductContentRepository;
import celulares.cordobacelulares.repository.SeoProductImageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoProductContentServiceTest {

    private final SeoCatalogProductRepository productRepository = mock(SeoCatalogProductRepository.class);
    private final SeoProductContentRepository contentRepository = mock(SeoProductContentRepository.class);
    private final SeoProductImageRepository imageRepository = mock(SeoProductImageRepository.class);
    private final SeoProductMediaStorage mediaStorage = mock(SeoProductMediaStorage.class);
    private final AtomicReference<SeoProductContent> stored = new AtomicReference<>();
    private SeoProductContentService service;

    @BeforeEach
    void setUp() {
        SeoCatalogProduct product = new SeoCatalogProduct(
                7L, "redmi-note-15", "XIAOMI", "REDMI NOTE 15", null,
                Instant.now(), Instant.now(), true
        );
        when(productRepository.findBySlug("redmi-note-15")).thenReturn(Optional.of(product));
        when(contentRepository.findBySeoCatalogProductSlug("redmi-note-15"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(contentRepository.save(any(SeoProductContent.class))).thenAnswer(invocation -> {
            SeoProductContent content = invocation.getArgument(0);
            if (content.getId() == null) content.setId(10L);
            for (int index = 0; index < content.getImages().size(); index++) {
                if (content.getImages().get(index).getId() == null) {
                    content.getImages().get(index).setId(100L + index);
                }
            }
            stored.set(content);
            return content;
        });
        when(imageRepository.findByIdAndContentId(any(Long.class), any(Long.class))).thenAnswer(invocation -> {
            Long imageId = invocation.getArgument(0);
            return stored.get() == null ? Optional.empty() : stored.get().getImages().stream()
                    .filter(image -> imageId.equals(image.getId()))
                    .findFirst();
        });
        service = new SeoProductContentService(
                productRepository, contentRepository, imageRepository, mediaStorage, new ObjectMapper()
        );
    }

    @Test
    void createsDraftLinkedToHistoricalProductThenPublishesAndUnpublishes() {
        ProductContentAdminDtos.SaveRequest request = new ProductContentAdminDtos.SaveRequest(
                "Descripción publicada", "https://www.mi.com/ar", "Xiaomi Argentina",
                List.of(new ProductContentAdminDtos.HighlightInput("Pantalla AMOLED")),
                List.of(new ProductContentAdminDtos.SpecSectionInput(
                        "Pantalla", List.of(new ProductContentAdminDtos.SpecInput("Tamaño", "6,7 pulgadas"))
                ))
        );

        ProductContentAdminDtos.DetailResponse draft = service.saveDraft("redmi-note-15", request);
        ProductContentAdminDtos.DetailResponse published = service.publish("redmi-note-15");

        assertThat(draft.status()).isEqualTo(SeoProductContentStatus.DRAFT);
        assertThat(stored.get().getSeoCatalogProduct().getId()).isEqualTo(7L);
        assertThat(published.status()).isEqualTo(SeoProductContentStatus.PUBLISHED);
        assertThat(service.published("redmi-note-15").orElseThrow().description())
                .isEqualTo("Descripción publicada");

        ProductContentAdminDtos.DetailResponse unpublished = service.unpublish("redmi-note-15");
        assertThat(unpublished.status()).isEqualTo(SeoProductContentStatus.DRAFT);
        assertThat(service.published("redmi-note-15")).isEmpty();
    }

    @Test
    void savingDraftAfterPublicationKeepsPublishedSnapshotVisible() {
        service.saveDraft("redmi-note-15", requestWithDescription("Versión pública"));
        service.publish("redmi-note-15");

        ProductContentAdminDtos.DetailResponse edited = service.saveDraft(
                "redmi-note-15", requestWithDescription("Cambio todavía privado")
        );

        assertThat(edited.status()).isEqualTo(SeoProductContentStatus.PUBLISHED);
        assertThat(edited.hasUnpublishedChanges()).isTrue();
        assertThat(service.published("redmi-note-15").orElseThrow().description())
                .isEqualTo("Versión pública");
    }

    @Test
    void publicationWithoutImagesIsValidAndDoesNotInventAny() {
        service.saveDraft("redmi-note-15", requestWithDescription("Sin fotos por ahora"));
        service.publish("redmi-note-15");

        assertThat(service.published("redmi-note-15").orElseThrow().images()).isEmpty();
    }

    @Test
    void firstUploadedImageIsPrimaryAndPrimaryCanBeChanged() {
        when(mediaStorage.store(any())).thenReturn(
                new SeoProductMediaStorage.StoredMedia("one.webp", "/product-media/one.webp", "one.webp", "image/webp"),
                new SeoProductMediaStorage.StoredMedia("two.webp", "/product-media/two.webp", "two.webp", "image/webp")
        );
        MockMultipartFile file = new MockMultipartFile("file", "image.webp", "image/webp", new byte[]{1});

        service.uploadImage("redmi-note-15", file, "Frente");
        ProductContentAdminDtos.DetailResponse twoImages = service.uploadImage("redmi-note-15", file, "Dorso");
        ProductContentAdminDtos.DetailResponse changed = service.setPrimaryImage(
                "redmi-note-15", twoImages.images().get(1).id()
        );

        assertThat(twoImages.images().get(0).primaryImage()).isTrue();
        assertThat(changed.images()).filteredOn(ProductContentAdminDtos.ImageResponse::primaryImage)
                .singleElement().extracting(ProductContentAdminDtos.ImageResponse::altText).isEqualTo("Dorso");
    }

    private ProductContentAdminDtos.SaveRequest requestWithDescription(String description) {
        return new ProductContentAdminDtos.SaveRequest(description, "", "", List.of(), List.of());
    }
}
