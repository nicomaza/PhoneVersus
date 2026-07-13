package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteCategoryPageResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.services.PriceConfigurationService;
import celulares.cordobacelulares.services.SupplierSheetService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TiendaPorteServiceImplCatalogTest {

    private final CatalogProductPolicy catalogProductPolicy = new CatalogProductPolicy(new CatalogCategoryResolver());
    private final TiendaPorteServiceImpl service = new TiendaPorteServiceImpl(
            null,
            null,
            null,
            null,
            new TiendaPortePriceCalculator(),
            catalogProductPolicy,
            null
    );

    @Test
    void reclassifiesCatalogBeforeGroupingAndDoesNotDuplicateMovedProducts() {
        List<TiendaPorteBrandResponse> catalog = service.buildCatalog(
                List.of(
                        product(1L, "REALME C75X 8GB 256GB", "OTRO", "OTRO", "100"),
                        product(1L, "REALME C75X 8GB 256GB", "REALME", "OTRO", "100"),
                        product(2L, "REALME C85 PRO 8GB 256GB", "OTRO", "REALME", "110"),
                        product(3L, "INFINIX HOT 60I 8GB 256GB", "OTRO", "OTRO", "90"),
                        product(4L, "HONOR MAGIC 7 LITE 12GB 512GB", "OTRO", "OTRO", "120"),
                        product(5L, "MACBOOK PRO M5 10CPU 16GB 1TB 14", "APPLE", "APPLE", "1000")
                ),
                new PriceConfiguration(),
                BigDecimal.ONE,
                null
        );

        Map<String, TiendaPorteBrandResponse> brandsByName = catalog.stream()
                .collect(Collectors.toMap(TiendaPorteBrandResponse::getMarca, Function.identity()));

        assertThat(brandsByName)
                .containsKeys("REALME", "INFINIX", "HONOR", "PRODUCTOS APPLE")
                .doesNotContainKeys("OTROS", "OTRO", "APPLE", "OTHER", "SIN CATEGORIA");
        assertThat(modelNames(brandsByName.get("REALME")))
                .containsExactlyInAnyOrder("REALME C75X 8GB 256GB", "REALME C85 PRO 8GB 256GB");
        assertThat(modelNames(brandsByName.get("INFINIX")))
                .containsExactly("INFINIX HOT 60I 8GB 256GB");
        assertThat(modelNames(brandsByName.get("HONOR")))
                .containsExactly("HONOR MAGIC 7 LITE 12GB 512GB");
        assertThat(modelNames(brandsByName.get("PRODUCTOS APPLE")))
                .containsExactly("MACBOOK PRO M5 10CPU 16GB 1TB 14");
    }

    @Test
    void allowedCategoriesUseCachedSnapshot() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        SupplierSheetService supplierSheetService = mock(SupplierSheetService.class);

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(new PriceConfiguration());
        when(supplierSheetService.getProducts()).thenReturn(List.of());
        when(cacheService.getProducts()).thenReturn(List.of(
                product(1L, "REALME C75X 8GB 256GB", "REALME", "REALME", "100"),
                product(2L, "CABLE USB", "OTRO", "OTRO", "10")
        ));

        TiendaPorteServiceImpl cachedService = new TiendaPorteServiceImpl(
                cacheService,
                priceConfigurationService,
                supplierSheetService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                catalogProductPolicy,
                emptyBlockedCatalogProductService()
        );

        List<TiendaPorteBrandResponse> catalog = cachedService.getAllowedCategories(null, null);

        assertThat(catalog).extracting(TiendaPorteBrandResponse::getMarca)
                .contains("REALME")
                .doesNotContain("OTROS");
        verify(cacheService).getProducts();
    }

    @Test
    void recalculatesPricesWithCurrentConfigurationUsingSameSnapshot() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        SupplierSheetService supplierSheetService = mock(SupplierSheetService.class);

        when(priceConfigurationService.getRequiredForCatalog())
                .thenReturn(priceConfiguration("1000"))
                .thenReturn(priceConfiguration("2000"));
        when(supplierSheetService.getProducts()).thenReturn(List.of());
        when(cacheService.getProducts()).thenReturn(List.of(product(1L, "REALME C75X 8GB 256GB", "REALME", "REALME", "100")));

        TiendaPorteServiceImpl cachedService = new TiendaPorteServiceImpl(
                cacheService,
                priceConfigurationService,
                supplierSheetService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                catalogProductPolicy,
                emptyBlockedCatalogProductService()
        );

        BigDecimal firstPrice = cachedService.getAll(null, null).get(0).getModelos().get(0).getPrecioPesos();
        BigDecimal secondPrice = cachedService.getAll(null, null).get(0).getModelos().get(0).getPrecioPesos();

        assertThat(firstPrice).isEqualByComparingTo("100000.00");
        assertThat(secondPrice).isEqualByComparingTo("200000.00");
        verify(cacheService, times(2)).getProducts();
    }

    @Test
    void categoryPaginationWorksFromSnapshot() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        SupplierSheetService supplierSheetService = mock(SupplierSheetService.class);

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(priceConfiguration("1000"));
        when(supplierSheetService.getProducts()).thenReturn(List.of());
        when(cacheService.getProducts()).thenReturn(List.of(
                product(1L, "REALME A", "REALME", "REALME", "100"),
                product(2L, "REALME B", "REALME", "REALME", "200"),
                product(3L, "REALME C", "REALME", "REALME", "300"),
                product(4L, "XIAOMI A", "XIAOMI", "XIAOMI", "50")
        ));

        TiendaPorteServiceImpl cachedService = new TiendaPorteServiceImpl(
                cacheService,
                priceConfigurationService,
                supplierSheetService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                catalogProductPolicy,
                emptyBlockedCatalogProductService()
        );

        TiendaPorteCategoryPageResponse response = cachedService.getByCategory("REALME", 2, 1, null, null);

        assertThat(response.getTotal()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.isHasNext()).isTrue();
        assertThat(modelNames(response.getData().get(0))).containsExactly("REALME B");
        verify(cacheService).getProducts();
    }

    @Test
    void excludesPartsAndVapersBeforeBuildingCatalog() {
        List<TiendaPorteBrandResponse> catalog = service.buildCatalog(
                List.of(
                        product(1L, "BATERIA IPHONE 6 ORIGINAL (FOXCONN)", "IPHONE", "IPHONE", "10"),
                        product(2L, "MODULO IPHONE 7G", "IPHONE", "IPHONE", "20"),
                        product(3L, "VAPER POD DESCARTABLE", "ARTICULOS VARIOS", "ARTICULOS VARIOS", "5"),
                        product(4L, "SAMSUNG A56 5G", "SAMSUNG", "SAMSUNG", "100")
                ),
                new PriceConfiguration(),
                BigDecimal.ONE,
                null
        );

        assertThat(catalog).extracting(TiendaPorteBrandResponse::getMarca).containsExactly("SAMSUNG");
        assertThat(modelNames(catalog.get(0))).containsExactly("SAMSUNG A56 5G");
    }

    @Test
    void classifiesNonAppleTabletsAsArticulosVariosAndKeepsIpadsAsAppleProducts() {
        List<TiendaPorteBrandResponse> catalog = service.buildCatalog(
                List.of(
                        product(1L, "TABLET XIAOMI REDMI PAD 2 PRO", "XIAOMI", "XIAOMI", "200"),
                        product(2L, "TABLET SAMSUNG X620 TAB S10 FE+", "SAMSUNG", "SAMSUNG", "300"),
                        product(3L, "IPAD AIR M3 11 128GB", "APPLE", "APPLE", "500"),
                        product(4L, "XIAOMI REDMI NOTE 14", "XIAOMI", "XIAOMI", "150")
                ),
                new PriceConfiguration(),
                BigDecimal.ONE,
                null
        );

        Map<String, TiendaPorteBrandResponse> brandsByName = catalog.stream()
                .collect(Collectors.toMap(TiendaPorteBrandResponse::getMarca, Function.identity()));

        assertThat(modelNames(brandsByName.get("ARTICULOS VARIOS")))
                .containsExactlyInAnyOrder("TABLET XIAOMI REDMI PAD 2 PRO", "TABLET SAMSUNG X620 TAB S10 FE+");
        assertThat(modelNames(brandsByName.get("PRODUCTOS APPLE"))).containsExactly("IPAD AIR M3 11 128GB");
        assertThat(modelNames(brandsByName.get("XIAOMI"))).containsExactly("XIAOMI REDMI NOTE 14");
    }

    @Test
    void supplierSheetProductsUseSamePolicy() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        SupplierSheetService supplierSheetService = mock(SupplierSheetService.class);

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(priceConfiguration("1000"));
        when(cacheService.getProducts()).thenReturn(List.of());
        when(supplierSheetService.getProducts()).thenReturn(List.of(
                supplierProduct("SAMSUNG", "SAMSUNG", "SAMSUNG X620 TAB S10 FE", "300"),
                supplierProduct("IPHONE", "IPHONE", "BATERIA IPHONE 6 ORIGINAL", "20"),
                supplierProduct("APPLE", "PRODUCTOS APPLE", "IPAD AIR M3 11 128GB", "500")
        ));

        TiendaPorteServiceImpl cachedService = new TiendaPorteServiceImpl(
                cacheService,
                priceConfigurationService,
                supplierSheetService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                catalogProductPolicy,
                emptyBlockedCatalogProductService()
        );

        List<TiendaPorteBrandResponse> catalog = cachedService.getAll(null, null);
        Map<String, TiendaPorteBrandResponse> brandsByName = catalog.stream()
                .collect(Collectors.toMap(TiendaPorteBrandResponse::getMarca, Function.identity()));

        assertThat(modelNames(brandsByName.get("ARTICULOS VARIOS"))).containsExactly("SAMSUNG X620 TAB S10 FE");
        assertThat(modelNames(brandsByName.get("PRODUCTOS APPLE"))).containsExactly("IPAD AIR M3 11 128GB");
        assertThat(catalog.stream()
                .flatMap(brand -> brand.getModelos().stream())
                .map(TiendaPorteModelResponse::getModeloNombre))
                .doesNotContain("BATERIA IPHONE 6 ORIGINAL");
    }

    @Test
    void blockedProductsAreExcludedFromTiendaPorteAndExactSupplierSheetMatch() {
        TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
        PriceConfigurationService priceConfigurationService = mock(PriceConfigurationService.class);
        SupplierSheetService supplierSheetService = mock(SupplierSheetService.class);

        when(priceConfigurationService.getRequiredForCatalog()).thenReturn(priceConfiguration("1000"));
        when(cacheService.getProducts()).thenReturn(List.of(
                product(99L, "SAMSUNG A56", "SAMSUNG", "SAMSUNG", "320"),
                product(100L, "SAMSUNG A35", "SAMSUNG", "SAMSUNG", "250")
        ));
        when(supplierSheetService.getProducts()).thenReturn(List.of(
                supplierProduct("SAMSUNG", "SAMSUNG", "SAMSUNG A56", "320"),
                supplierProduct("SAMSUNG", "SAMSUNG", "SAMSUNG A56 5G", "330")
        ));

        TiendaPorteServiceImpl cachedService = new TiendaPorteServiceImpl(
                cacheService,
                priceConfigurationService,
                supplierSheetService,
                new DollarQuotationResolver(),
                new TiendaPortePriceCalculator(),
                catalogProductPolicy,
                blockedCatalogProductService(Set.of(99L), Set.of("samsung|samsunga56"))
        );

        List<String> models = cachedService.getAll(null, null)
                .stream()
                .flatMap(brand -> brand.getModelos().stream())
                .map(TiendaPorteModelResponse::getModeloNombre)
                .toList();

        assertThat(models)
                .contains("SAMSUNG A35", "SAMSUNG A56 5G")
                .doesNotContain("SAMSUNG A56");
    }

    @Test
    void mixedCatalogAppliesPolicyBeforeGrouping() {
        List<TiendaPorteBrandResponse> catalog = service.buildCatalog(
                List.of(
                        product(1L, "BATERIA IPHONE 6", "IPHONE", "IPHONE", "10"),
                        product(2L, "MODULO IPHONE 7", "IPHONE", "IPHONE", "20"),
                        product(3L, "IPHONE 16 PRO", "APPLE", "APPLE", "900"),
                        product(4L, "REDMI NOTE 15 PRO", "XIAOMI", "XIAOMI", "300"),
                        product(5L, "TABLET XIAOMI REDMI PAD 2 PRO", "XIAOMI", "XIAOMI", "250"),
                        product(6L, "VAPER XIAOMI STYLE", "XIAOMI", "XIAOMI", "10"),
                        product(7L, "SAMSUNG A56", "SAMSUNG", "SAMSUNG", "320"),
                        product(8L, "TABLET SAMSUNG TAB S10", "SAMSUNG", "SAMSUNG", "450")
                ),
                new PriceConfiguration(),
                BigDecimal.ONE,
                null
        );

        Map<String, TiendaPorteBrandResponse> brandsByName = catalog.stream()
                .collect(Collectors.toMap(TiendaPorteBrandResponse::getMarca, Function.identity()));

        assertThat(modelNames(brandsByName.get("IPHONE"))).containsExactly("IPHONE 16 PRO");
        assertThat(modelNames(brandsByName.get("XIAOMI"))).containsExactly("REDMI NOTE 15 PRO");
        assertThat(modelNames(brandsByName.get("SAMSUNG"))).containsExactly("SAMSUNG A56");
        assertThat(modelNames(brandsByName.get("ARTICULOS VARIOS")))
                .containsExactlyInAnyOrder("TABLET XIAOMI REDMI PAD 2 PRO", "TABLET SAMSUNG TAB S10");
        assertThat(catalog.stream()
                .flatMap(brand -> brand.getModelos().stream())
                .map(TiendaPorteModelResponse::getModeloNombre))
                .doesNotContain("BATERIA IPHONE 6", "MODULO IPHONE 7", "VAPER XIAOMI STYLE");
    }

    @Test
    void articulosVariosProviderCategoryIsAuthoritativeAndDeduplicatesAgainstBrandCategories() {
        List<TiendaPorteBrandResponse> catalog = service.buildCatalog(
                List.of(
                        product(1L, "REDMI BUDS 6 PLAY", "XIAOMI", "ARTICULOS VARIOS", "80"),
                        product(2L, "MI BAND 9", "XIAOMI", "ARTICULOS VARIOS", "60"),
                        product(3L, "XIAOMI OUTDOOR CAMERA", "XIAOMI", "ARTICULOS VARIOS", "120"),
                        product(4L, "XIAOMI CAR CHARGER", "XIAOMI", "ARTICULOS VARIOS", "40"),
                        product(5L, "REDMI NOTE 15 PRO", "XIAOMI", "ARTICULOS VARIOS", "300"),
                        product(5L, "REDMI NOTE 15 PRO", "XIAOMI", "XIAOMI", "300"),
                        product(6L, "SAMSUNG A56", "SAMSUNG", null, "320", "ARTICULOS VARIOS"),
                        product(7L, "TABLET SAMSUNG TAB S10", "SAMSUNG", "ARTICULOS VARIOS", "450"),
                        product(8L, "XIAOMI 15T CAJA MANCHADA", "XIAOMI", "ARTICULOS VARIOS", "500"),
                        product(9L, "BATERIA IPHONE 16", "IPHONE", "ARTICULOS VARIOS", "20"),
                        product(10L, "MODULO SAMSUNG A56", "SAMSUNG", "ARTICULOS VARIOS", "30"),
                        product(11L, "VAPER 5000 PUFF", "XIAOMI", "ARTICULOS VARIOS", "10")
                ),
                new PriceConfiguration(),
                BigDecimal.ONE,
                null
        );

        Map<String, TiendaPorteBrandResponse> brandsByName = catalog.stream()
                .collect(Collectors.toMap(TiendaPorteBrandResponse::getMarca, Function.identity()));

        assertThat(brandsByName).containsOnlyKeys("ARTICULOS VARIOS");
        assertThat(modelNames(brandsByName.get("ARTICULOS VARIOS")))
                .containsExactlyInAnyOrder(
                        "REDMI BUDS 6 PLAY",
                        "MI BAND 9",
                        "XIAOMI OUTDOOR CAMERA",
                        "XIAOMI CAR CHARGER",
                        "REDMI NOTE 15 PRO",
                        "SAMSUNG A56",
                        "TABLET SAMSUNG TAB S10"
                );
        assertThat(modelNames(brandsByName.get("ARTICULOS VARIOS")))
                .doesNotContain("XIAOMI 15T CAJA MANCHADA", "BATERIA IPHONE 16", "MODULO SAMSUNG A56", "VAPER 5000 PUFF");
        assertThat(modelNames(brandsByName.get("ARTICULOS VARIOS")).stream()
                .filter("REDMI NOTE 15 PRO"::equals)
                .count()).isEqualTo(1);
    }

    @Test
    void deduplicationKeepsColorPriceVariantsWhenArticulosVariosWins() {
        List<TiendaPorteBrandResponse> catalog = service.buildCatalog(
                List.of(
                        product(
                                20L,
                                "REDMI NOTE 15 PRO",
                                "XIAOMI",
                                "ARTICULOS VARIOS",
                                "300",
                                null,
                                Map.of("Black", 2),
                                Map.of("Black", "300")
                        ),
                        product(
                                20L,
                                "REDMI NOTE 15 PRO",
                                "XIAOMI",
                                "XIAOMI",
                                "300",
                                null,
                                Map.of("Blue", 1),
                                Map.of("Blue", "330")
                        )
                ),
                new PriceConfiguration(),
                BigDecimal.ONE,
                null
        );

        Map<String, TiendaPorteBrandResponse> brandsByName = catalog.stream()
                .collect(Collectors.toMap(TiendaPorteBrandResponse::getMarca, Function.identity()));
        List<TiendaPorteModelResponse> models = brandsByName.get("ARTICULOS VARIOS").getModelos();
        TiendaPorteModelResponse blueVariant = models.stream()
                .filter(model -> model.getPrecioUsd().compareTo(new BigDecimal("330.00")) == 0)
                .findFirst()
                .orElseThrow();

        assertThat(brandsByName).containsOnlyKeys("ARTICULOS VARIOS");
        assertThat(models).hasSize(2);
        assertThat(models).extracting(TiendaPorteModelResponse::getPrecioUsd)
                .containsExactlyInAnyOrder(new BigDecimal("300.00"), new BigDecimal("330.00"));
        assertThat(blueVariant.getColores().stream().map(color -> color.getColor()).toList())
                .containsExactly("Blue");
    }

    private List<String> modelNames(TiendaPorteBrandResponse brand) {
        assertThat(brand).isNotNull();
        return brand.getModelos().stream()
                .map(TiendaPorteModelResponse::getModeloNombre)
                .toList();
    }

    private BlockedCatalogProductService emptyBlockedCatalogProductService() {
        return blockedCatalogProductService(Set.of(), Set.of());
    }

    private BlockedCatalogProductService blockedCatalogProductService(Set<Long> blockedExternalProductIds, Set<String> blockedNormalizedModelKeys) {
        BlockedCatalogProductService blockedCatalogProductService = mock(BlockedCatalogProductService.class);
        when(blockedCatalogProductService.currentFilter())
                .thenReturn(new BlockedCatalogProductService.BlockedCatalogFilter(blockedExternalProductIds, blockedNormalizedModelKeys));
        when(blockedCatalogProductService.isBlockedTiendaPorteProduct(any(TiendaPorteExternalProduct.class), anySet()))
                .thenCallRealMethod();
        when(blockedCatalogProductService.normalizedModelKey(any(), any()))
                .thenCallRealMethod();
        return blockedCatalogProductService;
    }

    private TiendaPorteExternalProduct product(
            Long id,
            String name,
            String referenceCategory,
            String productCategory,
            String priceUsd
    ) {
        return product(id, name, referenceCategory, productCategory, priceUsd, null);
    }

    private TiendaPorteExternalProduct product(
            Long id,
            String name,
            String referenceCategory,
            String productCategory,
            String priceUsd,
            String sourceCategory
    ) {
        return product(
                id,
                name,
                referenceCategory,
                productCategory,
                priceUsd,
                sourceCategory,
                Map.of("Black", 1),
                null
        );
    }

    private TiendaPorteExternalProduct product(
            Long id,
            String name,
            String referenceCategory,
            String productCategory,
            String priceUsd,
            String sourceCategory,
            Map<String, Integer> colorStock,
            Map<String, Object> colorPrices
    ) {
        TiendaPorteProductReference reference = new TiendaPorteProductReference();
        reference.setId(id);
        reference.setName(name);
        reference.setPriceUsd(priceUsd);
        reference.setCategory(category(referenceCategory));
        reference.setColorPrices(colorPrices);

        TiendaPorteExternalProduct product = new TiendaPorteExternalProduct();
        product.setName(name);
        product.setProductReference(reference);
        product.setCategory(category(productCategory));
        product.setSourceCategory(sourceCategory);
        product.setColorStock(colorStock);
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

    private PriceConfiguration priceConfiguration(String dolarBillete) {
        PriceConfiguration configuration = new PriceConfiguration();
        configuration.setDolarBillete(new BigDecimal(dolarBillete));
        return configuration;
    }

    private SupplierSheetProduct supplierProduct(String originalBrand, String responseBrand, String modelName, String priceUsd) {
        return new SupplierSheetProduct(
                originalBrand,
                responseBrand,
                modelName,
                List.of("Black"),
                new BigDecimal(priceUsd)
        );
    }
}
