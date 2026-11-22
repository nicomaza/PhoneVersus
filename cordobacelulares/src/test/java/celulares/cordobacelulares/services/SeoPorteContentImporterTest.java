package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.PublishedProductContent;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductSpecsResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.entities.SeoProductContent;
import celulares.cordobacelulares.entities.SeoProductContentStatus;
import celulares.cordobacelulares.entities.SeoProductImportExclusion;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.repository.SeoProductContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoPorteContentImporterTest {

    private final SeoCatalogProductRepository productRepository = mock(SeoCatalogProductRepository.class);
    private final SeoProductContentRepository contentRepository = mock(SeoProductContentRepository.class);
    private final TiendaPorteProductImageDownloader imageDownloader = mock(TiendaPorteProductImageDownloader.class);
    private final SeoProductMediaStorage mediaStorage = mock(SeoProductMediaStorage.class);
    private final AtomicReference<SeoProductContent> stored = new AtomicReference<>();
    private SeoPorteContentImporter importer;

    @BeforeEach
    void setUp() {
        SeoCatalogProduct product = new SeoCatalogProduct(
                1L, "samsung-galaxy-s25", "SAMSUNG", "SAMSUNG GALAXY S25",
                CatalogProductOrigin.TIENDA_PORTE, Instant.now(), Instant.now(), true
        );
        when(productRepository.findBySlug(product.getSlug())).thenReturn(Optional.of(product));
        when(contentRepository.findBySeoCatalogProductSlug(product.getSlug()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(contentRepository.save(any(SeoProductContent.class))).thenAnswer(invocation -> {
            SeoProductContent content = invocation.getArgument(0);
            if (content.getId() == null) content.setId(10L);
            stored.set(content);
            return content;
        });
        importer = new SeoPorteContentImporter(
                productRepository, contentRepository, imageDownloader, mediaStorage
        );
    }

    @Test
    void initialImportIsIdempotentAndUpdatesOriginalValues() {
        assertThat(importer.importProduct("samsung-galaxy-s25", 65054, sheet("h1", "6.2"), List.of()))
                .isEqualTo(SeoPorteContentImporter.ImportOutcome.IMPORTED);
        assertThat(stored.get().getHighlights()).hasSize(1);
        assertThat(stored.get().getSpecSections()).hasSize(1);
        assertThat(stored.get().getSpecSections().get(0).getSpecs().get(0).getValue()).isEqualTo("6.2");

        assertThat(importer.importProduct("samsung-galaxy-s25", 65054, sheet("h1", "6.2"), List.of()))
                .isEqualTo(SeoPorteContentImporter.ImportOutcome.UNCHANGED);
        verify(contentRepository, times(1)).save(any(SeoProductContent.class));

        assertThat(importer.importProduct("samsung-galaxy-s25", 65054, sheet("h2", "6.3"), List.of()))
                .isEqualTo(SeoPorteContentImporter.ImportOutcome.UPDATED);
        assertThat(stored.get().getSpecSections().get(0).getSpecs().get(0).getValue()).isEqualTo("6.3");
    }

    @Test
    void preservesManualEditsAndDoesNotOverwritePublishedSnapshot() throws Exception {
        importer.importProduct("samsung-galaxy-s25", 65054, sheet("h1", "6.2"), List.of());
        var spec = stored.get().getSpecSections().get(0).getSpecs().get(0);
        spec.setValue("Valor administrado");
        spec.setManuallyEdited(true);
        String snapshot = new ObjectMapper().writeValueAsString(new PublishedProductContent(
                "Publicado", "", "", List.of(), List.of(), List.of()
        ));
        stored.get().setPublishedSnapshot(snapshot);
        stored.get().setStatus(SeoProductContentStatus.PUBLISHED);

        importer.importProduct("samsung-galaxy-s25", 65054, sheet("h2", "6.3"), List.of());

        assertThat(spec.getValue()).isEqualTo("Valor administrado");
        assertThat(spec.getSourceValue()).isEqualTo("6.3");
        assertThat(stored.get().getPublishedSnapshot()).isEqualTo(snapshot);
        assertThat(stored.get().isDraftDirty()).isTrue();
    }

    @Test
    void respectsManualExclusionsAndDoesNotReimportDeletedSpec() {
        importer.importProduct("samsung-galaxy-s25", 65054, sheet("h1", "6.2"), List.of());
        SeoProductContent content = stored.get();
        var spec = content.getSpecSections().get(0).getSpecs().remove(0);
        SeoProductImportExclusion exclusion = new SeoProductImportExclusion();
        exclusion.setContent(content);
        exclusion.setItemType("SPEC");
        exclusion.setSourceKey(spec.getSourceKey());
        content.getImportExclusions().add(exclusion);

        importer.importProduct("samsung-galaxy-s25", 65054, sheet("h2", "6.4"), List.of());

        assertThat(content.getSpecSections().get(0).getSpecs()).isEmpty();
    }

    @Test
    void downloadsEachImageOnceAndKeepsManualImagesUntouched() {
        when(imageDownloader.download(anyString())).thenReturn(
                new SeoProductMediaStorage.StoredMedia(
                        "11111111-1111-1111-1111-111111111111.webp",
                        "/product-media/11111111-1111-1111-1111-111111111111.webp",
                        "frente.webp",
                        "image/webp"
                )
        );

        importer.importProduct(
                "samsung-galaxy-s25", 65054, sheet("h1", "6.2"),
                List.of("https://api.tiendaporte.com/images/front.webp", "https://api.tiendaporte.com/images/front.webp")
        );
        importer.importProduct(
                "samsung-galaxy-s25", 65054, sheet("h1", "6.2"),
                List.of("https://api.tiendaporte.com/images/front.webp")
        );

        assertThat(stored.get().getImages()).hasSize(1);
        assertThat(stored.get().getImages().get(0).isImportedFromPorte()).isTrue();
        verify(imageDownloader, times(1)).download(anyString());
        verify(mediaStorage, never()).deleteQuietly(anyString());
    }

    @Test
    void completesMissingImagesWhenTheSpecsHashDidNotChange() {
        when(imageDownloader.download(anyString())).thenReturn(
                new SeoProductMediaStorage.StoredMedia(
                        "22222222-2222-2222-2222-222222222222.webp",
                        "/product-media/22222222-2222-2222-2222-222222222222.webp",
                        "dorso.webp",
                        "image/webp"
                )
        );
        TiendaPorteProductSpecsResponse response = sheet("same-hash", "6.2");

        importer.importProduct("samsung-galaxy-s25", 65054, response, List.of());
        assertThat(importer.importProduct(
                "samsung-galaxy-s25", 65054, response, List.of("/productReferences/dorso.webp")
        )).isEqualTo(SeoPorteContentImporter.ImportOutcome.UPDATED);

        assertThat(stored.get().getImages()).hasSize(1);
        verify(imageDownloader).download("/productReferences/dorso.webp");
    }

    @Test
    void importsVerifiedGsmaArenaSourceAndPreservesLaterManualSource() {
        TiendaPorteProductSpecsResponse response = sheet("h1", "6.2");
        TiendaPorteProductSpecsResponse.Source source = new TiendaPorteProductSpecsResponse.Source();
        source.setDomain("www.gsmarena.com");
        source.setUrl("https://www.gsmarena.com/samsung_galaxy_s25-13610.php");
        response.getSheet().setSource(source);

        importer.importProduct("samsung-galaxy-s25", 65054, response, List.of());
        assertThat(stored.get().getSourceName()).isEqualTo("GSMArena");
        assertThat(stored.get().getSourceUrl()).isEqualTo(source.getUrl());

        stored.get().setSourceName("Fuente administrada");
        stored.get().setSourceUrl("https://example.com/ficha");
        response.setHash("h2");
        source.setUrl("https://www.gsmarena.com/otra_ficha-1.php");
        importer.importProduct("samsung-galaxy-s25", 65054, response, List.of());

        assertThat(stored.get().getSourceName()).isEqualTo("Fuente administrada");
        assertThat(stored.get().getSourceUrl()).isEqualTo("https://example.com/ficha");
    }

    private TiendaPorteProductSpecsResponse sheet(String hash, String screenSize) {
        TiendaPorteProductSpecsResponse response = new TiendaPorteProductSpecsResponse();
        response.setHash(hash);
        TiendaPorteProductSpecsResponse.Sheet sheet = new TiendaPorteProductSpecsResponse.Sheet();
        sheet.setSubtitle("Ficha oficial");
        TiendaPorteProductSpecsResponse.Highlight highlight = new TiendaPorteProductSpecsResponse.Highlight();
        highlight.setKey("display");
        highlight.setLabel("Pantalla");
        highlight.setValue("AMOLED");
        sheet.setHighlights(List.of(highlight));
        TiendaPorteProductSpecsResponse.Section section = new TiendaPorteProductSpecsResponse.Section();
        section.setKey("display");
        section.setTitle("Pantalla");
        TiendaPorteProductSpecsResponse.Row row = new TiendaPorteProductSpecsResponse.Row();
        row.setLabel("Otros");
        row.setValue(screenSize);
        section.setRows(List.of(row));
        sheet.setSections(List.of(section));
        response.setSheet(sheet);
        return response;
    }
}
