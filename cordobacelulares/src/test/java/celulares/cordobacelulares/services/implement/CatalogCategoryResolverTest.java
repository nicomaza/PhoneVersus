package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCategoryResolverTest {

    private final CatalogCategoryResolver resolver = new CatalogCategoryResolver();

    @Test
    void resolvesRealmeFromStrongNameWhenExternalCategoriesAreGeneric() {
        assertResolves("REALME C75X 8GB 256GB", "OTRO", "OTRO", null, "REALME");
    }

    @Test
    void resolvesRealmeFromStrongNameBeforeDirectCategory() {
        assertResolves("REALME C85 PRO 8GB 256GB", "OTRO", "REALME", null, "REALME");
    }

    @Test
    void resolvesInfinixFromStrongName() {
        assertResolves("INFINIX HOT 60I 8GB 256GB", "OTRO", "OTRO", null, "INFINIX");
    }

    @Test
    void resolvesHonorFromStrongName() {
        assertResolves("HONOR MAGIC 7 LITE 12GB 512GB", "OTRO", "OTRO", null, "HONOR");
    }

    @Test
    void resolvesMacbookAsAppleProducts() {
        assertResolves("MACBOOK PRO M5 10CPU 16GB 1TB 14", "APPLE", "APPLE", null, "PRODUCTOS APPLE");
    }

    @Test
    void resolvesIphoneBeforeAppleExternalCategory() {
        assertResolves("IPHONE 17 PRO MAX 256GB", "APPLE", "APPLE", null, "IPHONE");
    }

    @Test
    void resolvesRedmiAsXiaomi() {
        assertResolves("REDMI NOTE 15 PRO 12GB 512GB", "OTRO", "XIAOMI", null, "XIAOMI");
    }

    @Test
    void resolvesPocoAsXiaomi() {
        assertResolves("POCO C85 8GB 256GB", "POCO", "OTRO", null, "XIAOMI");
    }

    @Test
    void resolvesMotoAsMotorolaByToken() {
        assertResolves("MOTO G85 5G", "OTRO", "OTRO", null, "MOTOROLA");
    }

    @Test
    void fallsBackToOtrosForUnknownProduct() {
        assertResolves("PRODUCTO DESCONOCIDO XYZ", "OTRO", null, null, "OTROS");
    }

    @Test
    void resolvesPerfumesFromExternalCategory() {
        assertResolves("PERFUME IMPORTADO 100ML", "PERFUMES", "PERFUMES", null, "PERFUMES");
    }

    @Test
    void resolvesSonyFromTheAuthoritativeExternalCategory() {
        assertResolves("EA Sports FC 26 PS5", "SONY", "SONY", null, "SONY");
    }

    @Test
    void resolvesArticulosVariosFromExternalCategory() {
        assertResolves("TABLET GENERICA 10", "ARTICULOS VARIOS", "ARTICULOS VARIOS", null, "ARTICULOS VARIOS");
    }

    @Test
    void resolvesMacbookAirFromNameBeforeRequestedCategoryFallback() {
        assertResolves("MACBOOK AIR M4", "OTRO", "APPLE", "PRODUCTOS APPLE", "PRODUCTOS APPLE");
    }

    @Test
    void resolvesIphoneBeforeProductosAppleRequestedCategory() {
        assertResolves("IPHONE 16", "PRODUCTOS APPLE", "APPLE", "PRODUCTOS APPLE", "IPHONE");
    }

    @Test
    void resolvesHonorBeforeIncorrectRealmeExternalAndRequestedCategories() {
        assertResolves("HONOR MAGIC 7 LITE", "REALME", "REALME", "REALME", "HONOR");
    }

    @Test
    void resolvesSupplierSheetProductFromModelNameInsteadOfResponseBrand() {
        CatalogCategoryResolver.ResolveResult result = resolver.resolve(new CatalogCategoryResolver.ResolveRequest(
                "REALME C75X 8GB 256GB",
                "REALME C75X 8GB 256GB",
                "OTRO",
                null,
                null,
                CatalogProductOrigin.GOOGLE_SHEET,
                null
        ));

        assertThat(result.category()).isEqualTo("REALME");
    }

    private void assertResolves(
            String name,
            String referenceCategory,
            String productCategory,
            String requestedCategory,
            String expected
    ) {
        CatalogCategoryResolver.ResolveResult result = resolver.resolve(new CatalogCategoryResolver.ResolveRequest(
                name,
                name,
                referenceCategory,
                productCategory,
                requestedCategory,
                CatalogProductOrigin.TIENDA_PORTE,
                "test"
        ));

        assertThat(result.category()).isEqualTo(expected);
    }
}
