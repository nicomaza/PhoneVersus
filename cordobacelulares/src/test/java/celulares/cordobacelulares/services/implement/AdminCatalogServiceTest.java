package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.admin.AdminCatalogPageResponse;
import celulares.cordobacelulares.dtos.tiendaporte.admin.AdminCatalogProductRowResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.services.PriceConfigurationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCatalogServiceTest {

    @Test
    void listsTiendaPorteRowsFromSnapshotFlattenedByColor() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        AdminCatalogService service = new AdminCatalogService(
                cacheService,
                priceConfigurationService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                new CatalogProductPolicy(new CatalogCategoryResolver())
        );

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(priceConfiguration());
        when(cacheService.getProducts()).thenReturn(List.of(
                product(123L, "REDMI NOTE 15 PRO", "XIAOMI", "420", Map.of("Black", 3, "Titanium", 2)),
                product(456L, "BATERIA IPHONE 6", "IPHONE", "10", Map.of("White", 1)),
                product(789L, "TABLET SAMSUNG X620 TAB S10 FE", "SAMSUNG", "300", Map.of("Gray", 0))
        ));

        AdminCatalogPageResponse response = service.getProducts(null, null, 1, 50, "id", "asc", "TIENDA_PORTE", "todos");

        assertThat(response.getTotal()).isEqualTo(3);
        assertThat(response.getData()).extracting(AdminCatalogProductRowResponse::getModelo)
                .containsExactly("REDMI NOTE 15 PRO", "REDMI NOTE 15 PRO", "TABLET SAMSUNG X620 TAB S10 FE");
        assertThat(response.getData()).extracting(AdminCatalogProductRowResponse::getId)
                .containsExactly(123L, 123L, 789L);
        assertThat(response.getData()).extracting(AdminCatalogProductRowResponse::getMarca)
                .containsExactly("XIAOMI", "XIAOMI", "ARTICULOS VARIOS");
        assertThat(response.getData().get(0).getPrecioPesos()).isEqualByComparingTo("420000.00");
        verify(cacheService).getProducts();
    }

    @Test
    void searchCategoryStockAndPaginationAreAppliedAfterPolicy() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        AdminCatalogService service = new AdminCatalogService(
                cacheService,
                priceConfigurationService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                new CatalogProductPolicy(new CatalogCategoryResolver())
        );

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(priceConfiguration());
        when(cacheService.getProducts()).thenReturn(List.of(
                product(1L, "SAMSUNG A56", "SAMSUNG", "300", Map.of("Black", 2)),
                product(2L, "SAMSUNG GALAXY TAB S10", "SAMSUNG", "400", Map.of("Gray", 1)),
                product(3L, "REDMI NOTE 15 PRO", "XIAOMI", "250", Map.of("Blue", 0))
        ));

        AdminCatalogPageResponse response = service.getProducts("tab", "ARTICULOS VARIOS", 1, 25, "modelo", "asc", "TIENDA_PORTE", "con-stock");

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getData().get(0).getModelo()).isEqualTo("SAMSUNG GALAXY TAB S10");
        assertThat(response.getData().get(0).getMarca()).isEqualTo("ARTICULOS VARIOS");
    }

    @Test
    void adminCatalogPreservesArticulosVariosAndExcludesCajaManchada() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        AdminCatalogService service = new AdminCatalogService(
                cacheService,
                priceConfigurationService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                new CatalogProductPolicy(new CatalogCategoryResolver())
        );

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(priceConfiguration());
        when(cacheService.getProducts()).thenReturn(List.of(
                product(10L, "MI BAND 9", "XIAOMI", "ARTICULOS VARIOS", "60", Map.of("Black", 4), null),
                product(11L, "SAMSUNG A56", "SAMSUNG", null, "320", Map.of("Black", 2), "ARTICULOS VARIOS"),
                product(12L, "XIAOMI 15T CAJA MANCHADA", "XIAOMI", "ARTICULOS VARIOS", "500", Map.of("Blue", 1), null)
        ));

        AdminCatalogPageResponse response = service.getProducts(null, null, 1, 50, "id", "asc", "TIENDA_PORTE", "todos");

        assertThat(response.getTotal()).isEqualTo(2);
        assertThat(response.getData()).extracting(AdminCatalogProductRowResponse::getModelo)
                .containsExactly("MI BAND 9", "SAMSUNG A56");
        assertThat(response.getData()).extracting(AdminCatalogProductRowResponse::getMarca)
                .containsExactly("ARTICULOS VARIOS", "ARTICULOS VARIOS");
    }

    private PriceConfiguration priceConfiguration() {
        PriceConfiguration configuration = new PriceConfiguration();
        configuration.setDolarBillete(new BigDecimal("1000"));
        configuration.setUsdt(new BigDecimal("1000"));
        configuration.setTransferenciaBancaria(new BigDecimal("10"));
        configuration.setTarjeta3Pagos(new BigDecimal("20"));
        configuration.setTarjeta6Pagos(new BigDecimal("30"));
        configuration.setTarjeta12Pagos(new BigDecimal("40"));
        return configuration;
    }

    private TiendaPorteExternalProduct product(Long id, String name, String categoryName, String priceUsd, Map<String, Integer> stock) {
        return product(id, name, categoryName, categoryName, priceUsd, stock, null);
    }

    private TiendaPorteExternalProduct product(
            Long id,
            String name,
            String referenceCategory,
            String productCategory,
            String priceUsd,
            Map<String, Integer> stock,
            String sourceCategory
    ) {
        TiendaPorteProductReference reference = new TiendaPorteProductReference();
        reference.setId(id);
        reference.setName(name);
        reference.setPriceUsd(priceUsd);
        reference.setCategory(category(referenceCategory));

        TiendaPorteExternalProduct product = new TiendaPorteExternalProduct();
        product.setName(name);
        product.setProductReference(reference);
        product.setCategory(category(productCategory));
        product.setSourceCategory(sourceCategory);
        product.setColorStock(stock);
        return product;
    }

    private TiendaPorteCategory category(String name) {
        if (name == null) {
            return null;
        }
        TiendaPorteCategory category = new TiendaPorteCategory();
        category.setName(name);
        return category;
    }
}
