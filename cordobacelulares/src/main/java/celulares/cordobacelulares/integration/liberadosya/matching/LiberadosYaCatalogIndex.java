package celulares.cordobacelulares.integration.liberadosya.matching;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LiberadosYaCatalogIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaCatalogIndex.class);

    private final List<IndexedLiberadosYaProduct> allProducts;
    private final Map<String, List<IndexedLiberadosYaProduct>> byBrandKey;
    private final Map<String, List<IndexedLiberadosYaProduct>> byBrandAndMemory;

    private LiberadosYaCatalogIndex(
            List<IndexedLiberadosYaProduct> allProducts,
            Map<String, List<IndexedLiberadosYaProduct>> byBrandKey,
            Map<String, List<IndexedLiberadosYaProduct>> byBrandAndMemory
    ) {
        this.allProducts = List.copyOf(allProducts);
        this.byBrandKey = copyMap(byBrandKey);
        this.byBrandAndMemory = copyMap(byBrandAndMemory);
    }

    public static LiberadosYaCatalogIndex from(List<LiberadosYaProduct> products, LiberadosYaProductIdentityParser identityParser) {
        List<IndexedLiberadosYaProduct> all = new ArrayList<>();
        Map<String, List<IndexedLiberadosYaProduct>> byBrand = new LinkedHashMap<>();
        Map<String, List<IndexedLiberadosYaProduct>> byBrandAndMemory = new LinkedHashMap<>();
        int received = products == null ? 0 : products.size();
        int discarded = 0;
        if (products != null) {
            for (LiberadosYaProduct product : products) {
                if (product == null) {
                    discarded++;
                    continue;
                }
                ProductIdentity identity = parseIdentity(product, identityParser);
                if (identity == null) {
                    discarded++;
                    continue;
                }
                IndexedLiberadosYaProduct indexed = new IndexedLiberadosYaProduct(product, identity);
                all.add(indexed);
                for (String brandKey : indexed.identity().brandKeys()) {
                    byBrand.computeIfAbsent(brandKey, ignored -> new ArrayList<>()).add(indexed);
                    String memoryKey = memoryKey(brandKey, indexed.identity());
                    if (memoryKey != null) {
                        byBrandAndMemory.computeIfAbsent(memoryKey, ignored -> new ArrayList<>()).add(indexed);
                    }
                }
            }
        }
        LOGGER.info(
                "LiberadosYa catalog index built. cid={} receivedProducts={} indexedProducts={} discardedProducts={} indexedBrands={} productsByBrand={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                received,
                all.size(),
                discarded,
                byBrand.size(),
                brandSizes(byBrand)
        );
        return new LiberadosYaCatalogIndex(all, byBrand, byBrandAndMemory);
    }

    private static ProductIdentity parseIdentity(LiberadosYaProduct product, LiberadosYaProductIdentityParser identityParser) {
        try {
            return identityParser.parseProduct(product);
        } catch (RuntimeException ex) {
            addInconsistency(product, "identity-parse-error");
            LOGGER.warn(
                    "LiberadosYa product identity parse failed. cid={} url={} field={} value={} expectedType={} cause={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    product.getUrl(),
                    "product.identity",
                    safeValue(product.getNombre()),
                    "ProductIdentity",
                    TiendaPorteDiagnostics.rootCauseLabel(ex)
            );
            return identityParser.parseText(product.getNombre(), product.getMarca());
        }
    }

    public List<IndexedLiberadosYaProduct> candidates(ProductIdentity query) {
        if (query == null || query.brandKeys().isEmpty()) {
            return allProducts;
        }
        List<IndexedLiberadosYaProduct> brandMatches = new ArrayList<>();
        for (String brandKey : query.brandKeys()) {
            brandMatches.addAll(byBrandKey.getOrDefault(brandKey, List.of()));
        }
        return brandMatches.isEmpty() ? allProducts : unique(brandMatches);
    }

    public List<IndexedLiberadosYaProduct> allProducts() {
        return allProducts;
    }

    public int indexedProductCount() {
        return allProducts.size();
    }

    public int indexedBrandCount() {
        return byBrandKey.size();
    }

    private static String memoryKey(String brandKey, ProductIdentity identity) {
        if (identity.ramGb() == null || identity.storageGb() == null) {
            return null;
        }
        return brandKey + "|" + identity.ramGb() + "|" + identity.storageGb();
    }

    private static Map<String, List<IndexedLiberadosYaProduct>> copyMap(Map<String, List<IndexedLiberadosYaProduct>> source) {
        Map<String, List<IndexedLiberadosYaProduct>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static List<IndexedLiberadosYaProduct> unique(List<IndexedLiberadosYaProduct> source) {
        Map<String, IndexedLiberadosYaProduct> unique = new LinkedHashMap<>();
        for (IndexedLiberadosYaProduct indexed : source) {
            unique.putIfAbsent(productKey(indexed), indexed);
        }
        return List.copyOf(unique.values());
    }

    private static String productKey(IndexedLiberadosYaProduct indexed) {
        if (indexed == null || indexed.product() == null) {
            return "null";
        }
        String slug = indexed.product().getSlug();
        if (slug != null && !slug.isBlank()) {
            return "slug:" + slug;
        }
        String url = indexed.product().getUrl();
        if (url != null && !url.isBlank()) {
            return "url:" + url;
        }
        return "identity:" + System.identityHashCode(indexed.product());
    }

    private static Map<String, Integer> brandSizes(Map<String, List<IndexedLiberadosYaProduct>> byBrand) {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        byBrand.forEach((key, value) -> sizes.put(key, value.size()));
        return sizes;
    }

    private static void addInconsistency(LiberadosYaProduct product, String inconsistency) {
        if (product == null || inconsistency == null || inconsistency.isBlank()) {
            return;
        }
        Map<String, Object> validation = product.getValidacion();
        if (validation == null) {
            validation = new LinkedHashMap<>();
            product.setValidacion(validation);
        }
        List<String> inconsistencies = new ArrayList<>();
        Object existing = validation.get("posiblesInconsistencias");
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    inconsistencies.add(String.valueOf(item));
                }
            }
        } else if (existing != null) {
            inconsistencies.add(String.valueOf(existing));
        }
        if (!inconsistencies.contains(inconsistency)) {
            inconsistencies.add(inconsistency);
        }
        validation.put("posiblesInconsistencias", inconsistencies);
    }

    private static String safeValue(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }
}
