package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductSpecsResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.services.implement.CatalogProductPolicy;
import celulares.cordobacelulares.services.implement.ProductType;
import celulares.cordobacelulares.services.implement.TiendaPorteCatalogCacheService;
import celulares.cordobacelulares.services.implement.TiendaPorteClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoPorteSyncServiceTest {

    private final TiendaPorteCatalogCacheService cache = mock(TiendaPorteCatalogCacheService.class);
    private final TiendaPorteClient client = mock(TiendaPorteClient.class);
    private final CatalogProductPolicy policy = mock(CatalogProductPolicy.class);
    private final SeoCatalogProductRepository products = mock(SeoCatalogProductRepository.class);
    private final SeoPorteContentImporter importer = mock(SeoPorteContentImporter.class);
    private SeoPorteSyncService service;

    @BeforeEach
    void setUp() {
        TiendaPorteProperties properties = new TiendaPorteProperties();
        properties.getProductSync().setMaxConcurrency(2);
        properties.getProductSync().setBatchSize(2);
        properties.getProductSync().setMaxRetries(0);
        service = new SeoPorteSyncService(properties, cache, client, policy, products, importer);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void fetchesSharedReferenceOnceAndImportsEveryExactVariant() throws Exception {
        TiendaPorteExternalProduct first = product(65054, "SAMSUNG GALAXY S25 128GB");
        TiendaPorteExternalProduct second = product(65054, "SAMSUNG GALAXY S25 256GB");
        when(cache.getProducts()).thenReturn(List.of(first, second));
        when(policy.resolveProductType(any(TiendaPorteExternalProduct.class), isNull())).thenReturn(ProductType.PHONE);
        when(products.findAll()).thenReturn(List.of(
                seo(1L, "samsung-galaxy-s25-128gb"),
                seo(2L, "samsung-galaxy-s25-256gb")
        ));
        when(client.getProductSpecs(65054)).thenReturn(Optional.of(sheet()));
        when(importer.importProduct(anyString(), eq(65054L), any(), any()))
                .thenReturn(SeoPorteContentImporter.ImportOutcome.IMPORTED);

        assertThat(service.startAll("TEST").started()).isTrue();
        waitUntilFinished();

        verify(client, times(1)).getProductSpecs(65054);
        verify(importer).importProduct(eq("samsung-galaxy-s25-128gb"), eq(65054L), any(), any());
        verify(importer).importProduct(eq("samsung-galaxy-s25-256gb"), eq(65054L), any(), any());
        assertThat(service.status().imported()).isEqualTo(2);
        assertThat(service.status().errors()).isZero();
    }

    @Test
    void recordsMissingSheetAndContinuesForAllProducts() throws Exception {
        when(cache.getProducts()).thenReturn(List.of(product(44, "MOTOROLA EDGE 60")));
        when(policy.resolveProductType(any(TiendaPorteExternalProduct.class), isNull())).thenReturn(ProductType.PHONE);
        when(products.findAll()).thenReturn(List.of(seo(3L, "motorola-edge-60")));
        when(client.getProductSpecs(44)).thenReturn(Optional.empty());

        service.startAll("TEST");
        waitUntilFinished();

        verify(importer).recordMissingSheet("motorola-edge-60", 44, "SIN_FICHA_EN_PORTE", null);
        assertThat(service.status().withoutSheet()).isEqualTo(1);
        assertThat(service.status().state()).isEqualTo("COMPLETED");
    }

    private void waitUntilFinished() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (service.status().running() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(service.status().running()).isFalse();
    }

    private TiendaPorteExternalProduct product(long id, String name) {
        TiendaPorteProductReference reference = new TiendaPorteProductReference();
        reference.setId(id);
        reference.setName(name);
        reference.setImages(List.of());
        TiendaPorteExternalProduct product = new TiendaPorteExternalProduct();
        product.setName(name);
        product.setProductReference(reference);
        return product;
    }

    private SeoCatalogProduct seo(long id, String slug) {
        return new SeoCatalogProduct(
                id, slug, "SAMSUNG", slug, CatalogProductOrigin.TIENDA_PORTE,
                Instant.now(), Instant.now(), true
        );
    }

    private TiendaPorteProductSpecsResponse sheet() {
        TiendaPorteProductSpecsResponse result = new TiendaPorteProductSpecsResponse();
        result.setHash("hash");
        TiendaPorteProductSpecsResponse.Sheet sheet = new TiendaPorteProductSpecsResponse.Sheet();
        TiendaPorteProductSpecsResponse.Highlight highlight = new TiendaPorteProductSpecsResponse.Highlight();
        highlight.setKey("display");
        highlight.setValue("AMOLED");
        sheet.setHighlights(List.of(highlight));
        result.setSheet(sheet);
        return result;
    }
}
