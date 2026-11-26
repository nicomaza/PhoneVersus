package celulares.cordobacelulares.integration.liberadosya.matching;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LiberadosYaProductMatcherTest {

    private final LiberadosYaTextNormalizer normalizer = new LiberadosYaTextNormalizer();
    private final LiberadosYaProductIdentityParser identityParser = new LiberadosYaProductIdentityParser(normalizer);
    private final LiberadosYaProductMatcher matcher = new LiberadosYaProductMatcher(properties(), identityParser);

    @Test
    void selectsOnlyTheExactStorageRamNetworkAndCommercialVariant() {
        LiberadosYaProduct expected = product("Xiaomi Redmi Note 14 Pro 5G 8GB 256GB");
        LiberadosYaCatalogIndex index = index(
                expected,
                product("Xiaomi Redmi Note 14 Pro 5G 8GB 512GB"),
                product("Xiaomi Redmi Note 14 Pro Plus 5G 8GB 256GB"),
                product("Xiaomi Redmi Note 14 Pro 4G 8GB 256GB"),
                product("Xiaomi Redmi Note 14 Pro 5G 12GB 256GB")
        );

        LiberadosYaMatchResult result = matcher.match(
                "Xiaomi",
                "Redmi Note 14 Pro 5G 8GB 256GB",
                index
        );

        assertThat(result.status()).isEqualTo(LiberadosYaMatchResult.Status.MATCHED);
        assertThat(result.product()).isSameAs(expected);
    }

    @Test
    void doesNotCrossOneTerabyteAnd512GbOrNewAndUsedProducts() {
        LiberadosYaProduct oneTerabyte = product("iPhone 16 Pro Max 1TB");
        LiberadosYaProduct used = product("iPhone 16 Pro Max 512GB USADO Grado A");
        LiberadosYaCatalogIndex index = index(
                oneTerabyte,
                product("iPhone 16 Pro Max 512GB"),
                used
        );

        LiberadosYaMatchResult storage = matcher.match("Apple", "iPhone 16 Pro Max 1TB", index);
        LiberadosYaMatchResult condition = matcher.match("Apple", "iPhone 16 Pro Max 512GB usado", index);

        assertThat(storage.status()).isEqualTo(LiberadosYaMatchResult.Status.MATCHED);
        assertThat(storage.product()).isSameAs(oneTerabyte);
        assertThat(condition.status()).isEqualTo(LiberadosYaMatchResult.Status.MATCHED);
        assertThat(condition.product()).isSameAs(used);
    }

    @Test
    void usesPhysicalRamAndIgnoresVirtualRamAsAProductVariant() {
        LiberadosYaProduct physicalEight = product("Infinix Note 50 5G 8GB 256GB");
        Map<String, Map<String, Object>> specs = new LinkedHashMap<>();
        specs.put("Ficha tecnica", Map.of(
                "Memoria RAM fisica", "8 GB",
                "Ampliacion de RAM virtual", "Hasta 16 GB"
        ));
        physicalEight.setEspecificaciones(specs);
        LiberadosYaCatalogIndex index = index(physicalEight);

        assertThat(matcher.match("Infinix", "Note 50 5G 8GB 256GB", index).status())
                .isEqualTo(LiberadosYaMatchResult.Status.MATCHED);
        assertThat(matcher.match("Infinix", "Note 50 5G 16GB 256GB", index).status())
                .isEqualTo(LiberadosYaMatchResult.Status.NOT_FOUND);
    }

    private LiberadosYaCatalogIndex index(LiberadosYaProduct... products) {
        return LiberadosYaCatalogIndex.from(List.of(products), identityParser);
    }

    private LiberadosYaProduct product(String name) {
        LiberadosYaProduct product = new LiberadosYaProduct();
        product.setNombre(name);
        product.setVersionComercial(name);
        product.setModelo(name);
        product.setSlug(normalizer.normalize(name).toLowerCase().replace(' ', '-'));
        return product;
    }

    private LiberadosYaProperties properties() {
        LiberadosYaProperties properties = new LiberadosYaProperties();
        properties.setMinimumMatchScore(80);
        properties.setAmbiguityThreshold(5);
        return properties;
    }
}
