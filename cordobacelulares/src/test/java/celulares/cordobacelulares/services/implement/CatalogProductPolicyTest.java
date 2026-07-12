package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogProductPolicyTest {

    private final CatalogProductPolicy policy = new CatalogProductPolicy(new CatalogCategoryResolver());

    @Test
    void excludesBatteriesModulesAndVapersByClearProductTokens() {
        assertThat(policy.isExcludedProduct("BATERIA IPHONE 6 ORIGINAL")).isTrue();
        assertThat(policy.isExcludedProduct("BATERIA SAMSUNG A54")).isTrue();
        assertThat(policy.isExcludedProduct("BATTERY IPHONE 14 PRO")).isTrue();
        assertThat(policy.isExcludedProduct("MODULO IPHONE 6 PLUS")).isTrue();
        assertThat(policy.isExcludedProduct("MODULO SAMSUNG A15")).isTrue();
        assertThat(policy.isExcludedProduct("VAPER 5000 PUFF")).isTrue();
        assertThat(policy.isExcludedProduct("VAPE DESCARTABLE")).isTrue();
        assertThat(policy.isExcludedProduct("VAPORIZADOR ELECTRONICO")).isTrue();
        assertThat(policy.isExcludedProduct("CIGARRILLO ELECTRONICO")).isTrue();
    }

    @Test
    void excludesCajaManchadaVariantsIgnoringPunctuationAndAccents() {
        assertThat(policy.isExcludedProduct("IPHONE 16 128GB CAJA MANCHADA")).isTrue();
        assertThat(policy.isExcludedProduct("SAMSUNG A56 - CAJA MANCHADA")).isTrue();
        assertThat(policy.isExcludedProduct("XIAOMI 15T (CAJA MANCHADA)")).isTrue();
        assertThat(policy.isExcludedProduct("MOTOROLA EDGE 60 CAJA CON MANCHAS")).isTrue();
        assertThat(policy.isExcludedProduct("REDMI NOTE 15 CAJA MANCHADO")).isTrue();
    }

    @Test
    void doesNotExcludeDangerousPartialMatches() {
        assertThat(policy.isExcludedProduct("SOPORTE MODULAR PARA CELULAR")).isFalse();
        assertThat(policy.isExcludedProduct("CELULAR CON BATERIA 5000 MAH")).isFalse();
    }

    @Test
    void resolvesNonAppleTabletsAsArticulosVariosBeforeBrand() {
        assertTablet("TABLET XIAOMI REDMI PAD 2 PRO", "XIAOMI");
        assertTablet("TABLET XIAOMI POCO PAD M1", "XIAOMI");
        assertTablet("REDMI PAD PRO", "XIAOMI");
        assertTablet("POCO PAD 8GB 256GB", "XIAOMI");
        assertTablet("TABLET SAMSUNG X620 TAB S10 FE", "SAMSUNG");
        assertTablet("SAMSUNG GALAXY TAB S10", "SAMSUNG");
        assertTablet("HUAWEI MATEPAD 11", "HUAWEI");
        assertTablet("HONOR PAD X9", "HONOR");
        assertTablet("REALME PAD 2", "REALME");
        assertTablet("INFINIX XPAD", "INFINIX");
    }

    @Test
    void preservesAuthoritativeArticulosVariosBeforeBrandOrProductType() {
        assertThat(policy.resolveCanonicalCategory(product("MI BAND 9", "ARTICULOS VARIOS", "XIAOMI"), null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(policy.resolveCanonicalCategory(product("REDMI BUDS 6 PLAY", "ARTICULOS VARIOS", "XIAOMI"), null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(policy.resolveCanonicalCategory(product("REDMI NOTE 15 PRO", "ARTICULOS VARIOS", "XIAOMI"), null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(policy.resolveCanonicalCategory(product("SAMSUNG A56", null, "SAMSUNG"), "ARTICULOS VARIOS"))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(policy.resolveCanonicalCategory(product("XIAOMI OUTDOOR CAMERA", null, "XIAOMI", "ARTICULOS VARIOS", null), null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
    }

    @Test
    void keepsNormalBrandClassificationWhenProductDoesNotComeFromArticulosVarios() {
        assertThat(policy.resolveCanonicalCategory(product("REDMI NOTE 15 PRO", "XIAOMI", "XIAOMI"), null))
                .isEqualTo(CatalogCategoryResolver.XIAOMI);
        assertThat(policy.resolveCanonicalCategory(product("SAMSUNG A56", "SAMSUNG", "SAMSUNG"), null))
                .isEqualTo(CatalogCategoryResolver.SAMSUNG);
    }

    @Test
    void exclusionsBeatAuthoritativeArticulosVarios() {
        assertThat(policy.resolveCanonicalCategory(product("XIAOMI 15T CAJA MANCHADA", "ARTICULOS VARIOS", "XIAOMI"), null))
                .isNull();
        assertThat(policy.resolveCanonicalCategory(product("BATERIA IPHONE 16", "ARTICULOS VARIOS", "IPHONE"), null))
                .isNull();
        assertThat(policy.resolveCanonicalCategory(product("MODULO SAMSUNG A56", "ARTICULOS VARIOS", "SAMSUNG"), null))
                .isNull();
        assertThat(policy.resolveCanonicalCategory(product("VAPER 5000 PUFF", "ARTICULOS VARIOS", "XIAOMI"), null))
                .isNull();
    }

    @Test
    void supplierSheetArticulosVariosIsAuthoritativeUnlessExcluded() {
        assertThat(policy.resolveCanonicalCategory(sheetProduct("SAMSUNG", "ARTICULOS VARIOS", "SAMSUNG A56"), null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(policy.resolveCanonicalCategory(sheetProduct("XIAOMI", "ARTICULOS VARIOS", "REDMI NOTE 15 PRO"), null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(policy.resolveCanonicalCategory(sheetProduct("XIAOMI", "ARTICULOS VARIOS", "VAPER DESCARTABLE"), null))
                .isNull();
    }

    @Test
    void excludesWhenAnyOriginalNameContainsCajaManchada() {
        TiendaPorteExternalProduct product = product("REDMI NOTE 15", "ARTICULOS VARIOS", "XIAOMI");
        product.getProductReference().setName("REDMI NOTE 15 CAJA MANCHADA");

        assertThat(policy.resolveCanonicalCategory(product, null)).isNull();
    }

    @Test
    void keepsAppleProductsInAppleCategories() {
        assertThat(policy.resolveProductType("IPAD AIR M3 11 128GB", "APPLE")).isEqualTo(ProductType.APPLE_PRODUCT);
        assertThat(policy.resolveCanonicalCategory("IPAD AIR M3 11 128GB", "APPLE"))
                .isEqualTo(CatalogCategoryResolver.PRODUCTOS_APPLE);
        assertThat(policy.resolveCanonicalCategory("IPAD PRO M4", "APPLE"))
                .isEqualTo(CatalogCategoryResolver.PRODUCTOS_APPLE);
        assertThat(policy.resolveCanonicalCategory("IPHONE 16 PRO", "APPLE"))
                .isEqualTo(CatalogCategoryResolver.IPHONE);
        assertThat(policy.resolveCanonicalCategory("MACBOOK PRO M5", "APPLE"))
                .isEqualTo(CatalogCategoryResolver.PRODUCTOS_APPLE);
    }

    @Test
    void keepsPhonesInTheirCanonicalBrand() {
        assertThat(policy.resolveProductType("XIAOMI REDMI NOTE 14", "XIAOMI")).isEqualTo(ProductType.PHONE);
        assertThat(policy.resolveCanonicalCategory("REDMI NOTE 15 PRO", "XIAOMI"))
                .isEqualTo(CatalogCategoryResolver.XIAOMI);
        assertThat(policy.resolveCanonicalCategory("POCO X7 5G", "POCO"))
                .isEqualTo(CatalogCategoryResolver.XIAOMI);
        assertThat(policy.resolveCanonicalCategory("SAMSUNG A56 5G", "SAMSUNG"))
                .isEqualTo(CatalogCategoryResolver.SAMSUNG);
        assertThat(policy.resolveCanonicalCategory("REALME C75X", "REALME"))
                .isEqualTo(CatalogCategoryResolver.REALME);
        assertThat(policy.resolveCanonicalCategory("INFINIX HOT 60I", "INFINIX"))
                .isEqualTo(CatalogCategoryResolver.INFINIX);
        assertThat(policy.resolveCanonicalCategory("HONOR MAGIC 7 LITE", "HONOR"))
                .isEqualTo(CatalogCategoryResolver.HONOR);
        assertThat(policy.resolveCanonicalCategory("MOTO G85", "MOTOROLA"))
                .isEqualTo(CatalogCategoryResolver.MOTOROLA);
        assertThat(policy.resolveCanonicalCategory(new CatalogProductPolicy.ResolveRequest(
                "XIAOMI REDMI NOTE 14",
                "XIAOMI REDMI NOTE 14",
                "XIAOMI",
                "XIAOMI",
                null,
                CatalogProductOrigin.TIENDA_PORTE,
                "test"
        ))).isEqualTo(CatalogCategoryResolver.XIAOMI);
        assertThat(policy.resolveProductType(new CatalogProductPolicy.ResolveRequest(
                "A56 5G 256GB",
                "A56 5G 256GB",
                "SAMSUNG",
                "SAMSUNG",
                null,
                CatalogProductOrigin.TIENDA_PORTE,
                "test"
        ))).isEqualTo(ProductType.PHONE);
    }

    private void assertTablet(String modelName, String brandName) {
        assertThat(policy.resolveProductType(modelName, brandName)).isEqualTo(ProductType.TABLET);
        assertThat(policy.resolveCanonicalCategory(modelName, brandName))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
    }

    private TiendaPorteExternalProduct product(String name, String productCategory, String referenceCategory) {
        return product(name, productCategory, referenceCategory, null, null);
    }

    private TiendaPorteExternalProduct product(
            String name,
            String productCategory,
            String referenceCategory,
            String sourceCategory,
            String originalCategory
    ) {
        TiendaPorteProductReference reference = new TiendaPorteProductReference();
        reference.setId(1L);
        reference.setName(name);
        reference.setPriceUsd("100");
        reference.setCategory(category(referenceCategory));

        TiendaPorteExternalProduct product = new TiendaPorteExternalProduct();
        product.setName(name);
        product.setProductReference(reference);
        product.setCategory(category(productCategory));
        product.setSourceCategory(sourceCategory);
        product.setOriginalCategory(originalCategory);
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

    private SupplierSheetProduct sheetProduct(String originalBrand, String responseBrand, String modelName) {
        return new SupplierSheetProduct(
                originalBrand,
                responseBrand,
                modelName,
                List.of("Black"),
                BigDecimal.ONE
        );
    }
}
