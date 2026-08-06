package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CatalogProductPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogProductPolicy.class);
    private static final Set<String> BATTERY_TOKENS = Set.of("bateria", "battery");
    private static final Set<String> MODULE_TOKENS = Set.of("modulo");
    private static final Set<String> VAPE_TOKENS = Set.of(
            "vape",
            "vapes",
            "vaper",
            "vapers",
            "vapeador",
            "vapeadores",
            "vaporizador",
            "vaporizadores"
    );
    private static final Set<String> ROTOS_TOKENS = Set.of("roto", "rota", "rotos", "rotas");
    private static final Set<String> XIAOMI_TOKENS = Set.of("xiaomi", "redmi", "poco");
    private static final Set<String> PHONE_BRAND_TOKENS = Set.of(
            "samsung",
            "motorola",
            "moto",
            "realme",
            "infinix",
            "huawei",
            "honor",
            "oppo"
    );
    private static final Set<String> PHONE_CATEGORIES = Set.of(
            CatalogCategoryResolver.IPHONE,
            CatalogCategoryResolver.XIAOMI,
            CatalogCategoryResolver.SAMSUNG,
            CatalogCategoryResolver.MOTOROLA,
            CatalogCategoryResolver.REALME,
            CatalogCategoryResolver.INFINIX,
            CatalogCategoryResolver.HUAWEI,
            CatalogCategoryResolver.HONOR,
            CatalogCategoryResolver.OPPO
    );

    private final CatalogCategoryResolver categoryResolver;

    public CatalogProductPolicy(CatalogCategoryResolver categoryResolver) {
        this.categoryResolver = categoryResolver;
    }

    public boolean isExcludedProduct(TiendaPorteExternalProduct product) {
        if (product == null) {
            return true;
        }
        TiendaPorteProductReference productReference = product.getProductReference();
        return isExcludedProduct(
                product.getName(),
                productReference == null ? null : productReference.getName()
        );
    }

    public boolean isExcludedProduct(SupplierSheetProduct product) {
        return product == null || isExcludedProduct(product.modelName());
    }

    public boolean isExcludedProduct(String... names) {
        List<String> tokens = tokens(names);
        return startsWithAny(tokens, BATTERY_TOKENS)
                || startsWithAny(tokens, MODULE_TOKENS)
                || containsCajaManchada(tokens, names)
                || containsVape(tokens)
                || containsLegacyUnwanted(tokens);
    }

    public boolean isCajaManchadaProduct(TiendaPorteExternalProduct product) {
        if (product == null) {
            return false;
        }
        TiendaPorteProductReference productReference = product.getProductReference();
        return isCajaManchadaProduct(
                product.getName(),
                productReference == null ? null : productReference.getName()
        );
    }

    public boolean isCajaManchadaProduct(SupplierSheetProduct product) {
        return product != null && isCajaManchadaProduct(product.modelName());
    }

    public boolean isCajaManchadaProduct(String... names) {
        return containsCajaManchada(tokens(names), names);
    }

    public SelectionResult selectPublishableTiendaPorteProducts(
            List<TiendaPorteExternalProduct> products,
            String requestedCategory
    ) {
        if (products == null || products.isEmpty()) {
            return new SelectionResult(List.of(), SelectionStats.empty());
        }

        int totalReceived = 0;
        int excludedProducts = 0;
        int excludedCajaManchada = 0;
        int preservedArticulosVarios = 0;
        int deduplicatedProducts = 0;
        Map<String, ProductSelection> selectionsByKey = new LinkedHashMap<>();

        for (TiendaPorteExternalProduct product : products) {
            totalReceived++;
            if (product == null || isExcludedProduct(product)) {
                excludedProducts++;
                if (isCajaManchadaProduct(product)) {
                    excludedCajaManchada++;
                }
                continue;
            }

            String canonicalCategory = resolveCanonicalCategory(product, requestedCategory);
            if (canonicalCategory == null) {
                continue;
            }
            if (isAuthoritativeArticulosVarios(product, requestedCategory)) {
                preservedArticulosVarios++;
            }

            ProductSelection candidate = new ProductSelection(product, canonicalCategory);
            String key = deduplicationKey(product);
            ProductSelection current = selectionsByKey.get(key);
            if (current == null) {
                selectionsByKey.put(key, candidate);
                continue;
            }

            deduplicatedProducts++;
            selectionsByKey.put(key, preferredSelection(current, candidate));
        }

        List<ProductSelection> selections = List.copyOf(selectionsByKey.values());
        return new SelectionResult(
                selections,
                new SelectionStats(
                        totalReceived,
                        excludedProducts,
                        excludedCajaManchada,
                        preservedArticulosVarios,
                        deduplicatedProducts,
                        selections.size()
                )
        );
    }

    public ProductType resolveProductType(TiendaPorteExternalProduct product, String requestedCategory) {
        if (product == null) {
            return ProductType.EXCLUDED;
        }
        TiendaPorteProductReference productReference = product.getProductReference();
        return resolveProductType(new ResolveRequest(
                productReference == null ? null : productReference.getName(),
                product.getName(),
                categoryName(productReference == null ? null : productReference.getCategory()),
                categoryName(product.getCategory()),
                requestedCategory,
                product.getSourceCategory(),
                product.getOriginalCategory(),
                CatalogProductOrigin.TIENDA_PORTE,
                productReference == null || productReference.getId() == null ? null : productReference.getId().toString()
        ));
    }

    public ProductType resolveProductType(SupplierSheetProduct product, String requestedCategory) {
        if (product == null) {
            return ProductType.EXCLUDED;
        }
        return resolveProductType(new ResolveRequest(
                product.modelName(),
                product.modelName(),
                product.responseBrand(),
                product.originalBrand(),
                requestedCategory,
                product.responseBrand(),
                product.originalBrand(),
                CatalogProductOrigin.GOOGLE_SHEET,
                null
        ));
    }

    public ProductType resolveProductType(String modelName, String brandName) {
        return resolveProductType(new ResolveRequest(
                modelName,
                modelName,
                brandName,
                brandName,
                null,
                null,
                null
        ));
    }

    public ProductType resolveProductType(ResolveRequest request) {
        ResolveRequest safeRequest = request == null ? ResolveRequest.empty() : request;
        List<String> tokens = tokens(safeRequest.productReferenceName(), safeRequest.productName());
        if (isExcludedProduct(safeRequest.productReferenceName(), safeRequest.productName())) {
            return ProductType.EXCLUDED;
        }
        if (isAuthoritativeArticulosVarios(safeRequest)) {
            return ProductType.OTHER;
        }
        if (tokens.contains("iphone")) {
            return ProductType.PHONE;
        }
        if (containsAppleTablet(tokens) || containsAppleAccessory(tokens)) {
            return ProductType.APPLE_PRODUCT;
        }
        if (containsNonAppleTablet(tokens)) {
            return ProductType.TABLET;
        }
        if (containsAny(tokens, XIAOMI_TOKENS) || containsAny(tokens, PHONE_BRAND_TOKENS)) {
            return ProductType.PHONE;
        }
        String resolvedCategory = categoryResolver.resolve(toCategoryRequest(safeRequest)).category();
        if (PHONE_CATEGORIES.contains(resolvedCategory)) {
            return ProductType.PHONE;
        }
        if (CatalogCategoryResolver.PRODUCTOS_APPLE.equals(resolvedCategory)) {
            return ProductType.APPLE_PRODUCT;
        }
        return ProductType.OTHER;
    }

    public String resolveCanonicalCategory(TiendaPorteExternalProduct product, String requestedCategory) {
        if (product == null) {
            return null;
        }
        TiendaPorteProductReference productReference = product.getProductReference();
        return resolveCanonicalCategory(new ResolveRequest(
                productReference == null ? null : productReference.getName(),
                product.getName(),
                categoryName(productReference == null ? null : productReference.getCategory()),
                categoryName(product.getCategory()),
                requestedCategory,
                product.getSourceCategory(),
                product.getOriginalCategory(),
                CatalogProductOrigin.TIENDA_PORTE,
                productReference == null || productReference.getId() == null ? null : productReference.getId().toString()
        ));
    }

    public String resolveCanonicalCategory(SupplierSheetProduct product, String requestedCategory) {
        if (product == null) {
            return null;
        }
        if (isExcludedProduct(product)) {
            return null;
        }
        String sheetCategory = CatalogCategoryResolver.canonicalizeRequestedCategory(product.responseBrand());
        return sheetCategory == null ? CatalogCategoryResolver.ARTICULOS_VARIOS : sheetCategory;
    }

    public String resolveCanonicalCategory(String modelName, String brandName) {
        return resolveCanonicalCategory(new ResolveRequest(
                modelName,
                modelName,
                brandName,
                brandName,
                null,
                null,
                null
        ));
    }

    public String resolveCanonicalCategory(ResolveRequest request) {
        ResolveRequest safeRequest = request == null ? ResolveRequest.empty() : request;
        ProductType productType = resolveProductType(safeRequest);
        if (productType == ProductType.EXCLUDED) {
            return null;
        }
        if (isAuthoritativeArticulosVarios(safeRequest)) {
            logArticulosVariosPreserved(safeRequest);
            return CatalogCategoryResolver.ARTICULOS_VARIOS;
        }
        if (productType == ProductType.TABLET) {
            return CatalogCategoryResolver.ARTICULOS_VARIOS;
        }
        if (productType == ProductType.APPLE_PRODUCT) {
            return CatalogCategoryResolver.PRODUCTOS_APPLE;
        }
        CatalogCategoryResolver.ResolveResult resolved = categoryResolver.resolve(toCategoryRequest(safeRequest));
        return resolved.category();
    }

    public String canonicalizeRequestedCategory(String category) {
        return CatalogCategoryResolver.canonicalizeRequestedCategory(category);
    }

    public boolean isAuthoritativeArticulosVarios(TiendaPorteExternalProduct product, String requestedCategory) {
        if (product == null) {
            return false;
        }
        TiendaPorteProductReference productReference = product.getProductReference();
        return isAuthoritativeArticulosVarios(new ResolveRequest(
                productReference == null ? null : productReference.getName(),
                product.getName(),
                categoryName(productReference == null ? null : productReference.getCategory()),
                categoryName(product.getCategory()),
                requestedCategory,
                product.getSourceCategory(),
                product.getOriginalCategory(),
                CatalogProductOrigin.TIENDA_PORTE,
                productReference == null || productReference.getId() == null ? null : productReference.getId().toString()
        ));
    }

    private CatalogCategoryResolver.ResolveRequest toCategoryRequest(ResolveRequest request) {
        ResolveRequest safeRequest = request == null ? ResolveRequest.empty() : request;
        return new CatalogCategoryResolver.ResolveRequest(
                safeRequest.productReferenceName(),
                safeRequest.productName(),
                safeRequest.productReferenceCategory(),
                safeRequest.productCategory(),
                safeRequest.requestedCategory(),
                safeRequest.origin(),
                safeRequest.externalProductId()
        );
    }

    private boolean isAuthoritativeArticulosVarios(ResolveRequest request) {
        ResolveRequest safeRequest = request == null ? ResolveRequest.empty() : request;
        return isArticulosVariosCategory(safeRequest.productCategory())
                || isArticulosVariosCategory(safeRequest.requestedCategory())
                || isArticulosVariosCategory(safeRequest.sourceCategory())
                || isArticulosVariosCategory(safeRequest.originalCategory());
    }

    private boolean isArticulosVariosCategory(String category) {
        return CatalogCategoryResolver.ARTICULOS_VARIOS.equals(CatalogCategoryResolver.canonicalizeRequestedCategory(category));
    }

    private void logArticulosVariosPreserved(ResolveRequest request) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
                "Product category preserved: id={} name={} productCategory={} referenceCategory={} requestedCategory={} sourceCategory={} originalCategory={} finalCategory={} reason={}",
                clean(request.externalProductId()),
                firstNonBlank(request.productReferenceName(), request.productName()),
                clean(request.productCategory()),
                clean(request.productReferenceCategory()),
                clean(request.requestedCategory()),
                clean(request.sourceCategory()),
                clean(request.originalCategory()),
                CatalogCategoryResolver.ARTICULOS_VARIOS,
                "AUTHORITATIVE_ARTICULOS_VARIOS"
        );
    }

    private String deduplicationKey(TiendaPorteExternalProduct product) {
        TiendaPorteProductReference productReference = product == null ? null : product.getProductReference();
        String priceKey = priceKey(productReference == null ? null : productReference.getPriceUsd());
        if (productReference != null && productReference.getId() != null) {
            return CatalogProductOrigin.TIENDA_PORTE + "|ref:" + productReference.getId() + "|price:" + priceKey;
        }
        return CatalogProductOrigin.TIENDA_PORTE + "|name:" + TiendaPorteTextUtils.normalize(modelName(product)) + "|price:" + priceKey;
    }

    private ProductSelection preferredSelection(ProductSelection current, ProductSelection candidate) {
        boolean currentArticulos = CatalogCategoryResolver.ARTICULOS_VARIOS.equals(current.canonicalCategory());
        boolean candidateArticulos = CatalogCategoryResolver.ARTICULOS_VARIOS.equals(candidate.canonicalCategory());
        ProductSelection winner = candidateArticulos && !currentArticulos ? candidate : current;
        ProductSelection loser = winner == current ? candidate : current;
        mergeProductVariants(winner.product(), loser.product());
        return winner;
    }

    private void mergeProductVariants(TiendaPorteExternalProduct target, TiendaPorteExternalProduct source) {
        mergeColorStock(target, source);
        mergeColorPrices(target, source);
    }

    private void mergeColorStock(TiendaPorteExternalProduct target, TiendaPorteExternalProduct source) {
        if (target == null || source == null || source.getColorStock() == null || source.getColorStock().isEmpty()) {
            return;
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        if (target.getColorStock() != null) {
            merged.putAll(target.getColorStock());
        }
        for (Map.Entry<String, Integer> entry : source.getColorStock().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            int stock = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            merged.merge(entry.getKey(), stock, Math::max);
        }
        target.setColorStock(Map.copyOf(merged));
    }

    private void mergeColorPrices(TiendaPorteExternalProduct target, TiendaPorteExternalProduct source) {
        TiendaPorteProductReference targetReference = target == null ? null : target.getProductReference();
        TiendaPorteProductReference sourceReference = source == null ? null : source.getProductReference();
        Map<String, Object> sourceColorPrices = sourceReference == null ? null : sourceReference.getColorPrices();
        if (targetReference == null || sourceColorPrices == null || sourceColorPrices.isEmpty()) {
            return;
        }

        Map<String, Object> merged = new LinkedHashMap<>();
        if (targetReference.getColorPrices() != null) {
            merged.putAll(targetReference.getColorPrices());
        }
        for (Map.Entry<String, Object> entry : sourceColorPrices.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                merged.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        targetReference.setColorPrices(Map.copyOf(merged));
    }


    private String modelName(TiendaPorteExternalProduct product) {
        if (product == null) {
            return "";
        }
        TiendaPorteProductReference productReference = product.getProductReference();
        return firstNonBlank(productReference == null ? null : productReference.getName(), product.getName());
    }

    private String priceKey(String priceUsd) {
        if (priceUsd == null || priceUsd.isBlank()) {
            return "0.00";
        }
        try {
            BigDecimal price = new BigDecimal(priceUsd.trim());
            if (price.signum() < 0) {
                return "0.00";
            }
            return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException ex) {
            return "0.00";
        }
    }

    private boolean containsVape(List<String> tokens) {
        return containsAny(tokens, VAPE_TOKENS)
                || containsSequence(tokens, "vaporizador", "electronico")
                || containsSequence(tokens, "vaporizadores", "electronicos")
                || containsSequence(tokens, "cigarrillo", "electronico")
                || containsSequence(tokens, "cigarrillos", "electronicos");
    }

    private boolean containsCajaManchada(List<String> tokens, String... names) {
        if (containsSequence(tokens, "caja", "manchada")
                || containsSequence(tokens, "caja", "manchado")
                || containsSequence(tokens, "caja", "con", "manchas")) {
            return true;
        }
        if (names == null) {
            return false;
        }
        for (String name : names) {
            String normalized = TiendaPorteTextUtils.normalize(name);
            if (normalized.contains("cajamanchada")
                    || normalized.contains("cajamanchado")
                    || normalized.contains("cajaconmanchas")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLegacyUnwanted(List<String> tokens) {
        if (containsAny(tokens, ROTOS_TOKENS)
                || containsSequence(tokens, "sin", "activar")
                || tokens.contains("sinactivar")) {
            return true;
        }
        return tokens.stream().anyMatch(token ->
                token.startsWith("aboll")
                        || token.startsWith("usad")
                        || token.startsWith("activad")
        );
    }

    private boolean containsAppleTablet(List<String> tokens) {
        return tokens.contains("ipad");
    }

    private boolean containsAppleAccessory(List<String> tokens) {
        return tokens.contains("macbook")
                || tokens.contains("imac")
                || tokens.contains("airpods")
                || tokens.contains("homepod")
                || containsSequence(tokens, "mac", "mini")
                || containsSequence(tokens, "mac", "studio")
                || containsSequence(tokens, "mac", "pro")
                || containsSequence(tokens, "apple", "watch")
                || containsSequence(tokens, "watch", "series")
                || containsSequence(tokens, "watch", "ultra")
                || containsSequence(tokens, "watch", "se")
                || containsSequence(tokens, "apple", "pencil")
                || containsSequence(tokens, "magic", "keyboard")
                || containsSequence(tokens, "magic", "mouse")
                || containsSequence(tokens, "magic", "trackpad");
    }

    private boolean containsNonAppleTablet(List<String> tokens) {
        return tokens.contains("tablet")
                || containsSequence(tokens, "galaxy", "tab")
                || containsBrandTab(tokens)
                || tokens.contains("matepad")
                || tokens.contains("xpad")
                || containsBrandPadSequence(tokens);
    }

    private boolean containsBrandPadSequence(List<String> tokens) {
        for (String brandToken : List.of("xiaomi", "redmi", "poco", "samsung", "honor", "huawei", "oppo", "realme", "infinix", "lenovo")) {
            if (containsSequence(tokens, brandToken, "pad")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsBrandTab(List<String> tokens) {
        return tokens.contains("tab")
                && tokens.stream().anyMatch(Set.of("samsung", "lenovo", "galaxy", "huawei", "honor", "realme", "oppo", "infinix")::contains);
    }

    private boolean containsAny(List<String> tokens, Set<String> expectedTokens) {
        return tokens.stream().anyMatch(expectedTokens::contains);
    }

    private boolean startsWithAny(List<String> tokens, Set<String> expectedTokens) {
        return !tokens.isEmpty() && expectedTokens.contains(tokens.get(0));
    }

    private boolean containsSequence(List<String> tokens, String first, String second) {
        for (int index = 0; index < tokens.size() - 1; index++) {
            if (first.equals(tokens.get(index)) && second.equals(tokens.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSequence(List<String> tokens, String first, String second, String third) {
        for (int index = 0; index < tokens.size() - 2; index++) {
            if (first.equals(tokens.get(index)) && second.equals(tokens.get(index + 1)) && third.equals(tokens.get(index + 2))) {
                return true;
            }
        }
        return false;
    }

    private List<String> tokens(String... values) {
        List<String> tokens = new ArrayList<>();
        if (values == null) {
            return tokens;
        }
        for (String value : values) {
            tokens.addAll(TiendaPorteTextUtils.normalizedTokens(value));
        }
        return tokens.stream()
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private String categoryName(TiendaPorteCategory category) {
        return category == null ? null : category.getName();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record ProductSelection(TiendaPorteExternalProduct product, String canonicalCategory) {
    }

    public record SelectionResult(List<ProductSelection> selections, SelectionStats stats) {
    }

    public record SelectionStats(
            int totalReceived,
            int excludedProducts,
            int excludedCajaManchada,
            int preservedArticulosVarios,
            int deduplicatedProducts,
            int totalPublished
    ) {
        private static SelectionStats empty() {
            return new SelectionStats(0, 0, 0, 0, 0, 0);
        }
    }

    public record ResolveRequest(
            String productReferenceName,
            String productName,
            String productReferenceCategory,
            String productCategory,
            String requestedCategory,
            String sourceCategory,
            String originalCategory,
            CatalogProductOrigin origin,
            String externalProductId
    ) {
        public ResolveRequest(
                String productReferenceName,
                String productName,
                String productReferenceCategory,
                String productCategory,
                String requestedCategory,
                CatalogProductOrigin origin,
                String externalProductId
        ) {
            this(
                    productReferenceName,
                    productName,
                    productReferenceCategory,
                    productCategory,
                    requestedCategory,
                    null,
                    null,
                    origin,
                    externalProductId
            );
        }

        private static ResolveRequest empty() {
            return new ResolveRequest(null, null, null, null, null, null, null, null, null);
        }
    }
}
