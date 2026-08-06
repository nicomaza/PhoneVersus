package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CatalogCategoryResolver {

    public static final String IPHONE = "IPHONE";
    public static final String XIAOMI = "XIAOMI";
    public static final String SAMSUNG = "SAMSUNG";
    public static final String MOTOROLA = "MOTOROLA";
    public static final String REALME = "REALME";
    public static final String INFINIX = "INFINIX";
    public static final String PRODUCTOS_APPLE = "PRODUCTOS APPLE";
    public static final String ARTICULOS_VARIOS = "ARTICULOS VARIOS";
    public static final String PERFUMES = "PERFUMES";
    public static final String HUAWEI = "HUAWEI";
    public static final String HONOR = "HONOR";
    public static final String OPPO = "OPPO";
    public static final String SONY = "SONY";
    public static final String OTROS = "OTROS";

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogCategoryResolver.class);
    private static final Map<String, String> CATEGORY_ALIASES = categoryAliases();
    private static final Set<String> GENERIC_CATEGORY_KEYS = Set.of(
            "otro",
            "otros",
            "other",
            "others",
            "general",
            "sincategoria",
            "sinmarca"
    );
    private static final Set<String> XIAOMI_TOKENS = Set.of("xiaomi", "redmi", "poco");
    private static final List<String> DIRECT_BRANDS = List.of(
            REALME,
            INFINIX,
            SAMSUNG,
            HUAWEI,
            HONOR,
            OPPO,
            SONY
    );

    public ResolveResult resolve(ResolveRequest request) {
        ResolveRequest safeRequest = request == null ? ResolveRequest.empty() : request;
        String requestedCategory = canonicalizeRequestedCategory(safeRequest.requestedCategory());
        boolean authoritativeArticulosVarios = ARTICULOS_VARIOS.equals(canonicalizeRequestedCategory(safeRequest.productCategory()))
                || ARTICULOS_VARIOS.equals(requestedCategory);

        ResolveResult result;
        if (authoritativeArticulosVarios) {
            result = new ResolveResult(ARTICULOS_VARIOS, ResolutionReason.EXTERNAL_CATEGORY);
        } else {
            String strongNameCategory = resolveStrongNameCategory(safeRequest);
            String externalCategory = resolveExternalCategory(safeRequest);
            if (strongNameCategory != null) {
                result = new ResolveResult(strongNameCategory, ResolutionReason.STRONG_NAME_MATCH);
            } else if (externalCategory != null) {
                result = new ResolveResult(externalCategory, ResolutionReason.EXTERNAL_CATEGORY);
            } else if (requestedCategory != null) {
                result = new ResolveResult(requestedCategory, ResolutionReason.REQUESTED_CATEGORY);
            } else {
                result = new ResolveResult(OTROS, ResolutionReason.FALLBACK_OTROS);
            }
        }

        logResolutionIfNeeded(safeRequest, result);
        return result;
    }

    public static String canonicalizeRequestedCategory(String category) {
        if (isBlank(category)) {
            return null;
        }
        String key = TiendaPorteTextUtils.normalize(category);
        if (GENERIC_CATEGORY_KEYS.contains(key)) {
            return OTROS;
        }
        return CATEGORY_ALIASES.get(key);
    }

    public static boolean isCanonicalCategory(String category) {
        String canonical = canonicalizeRequestedCategory(category);
        return canonical != null && canonical.equals(category);
    }

    private String resolveStrongNameCategory(ResolveRequest request) {
        List<String> tokens = TiendaPorteTextUtils.normalizedTokens(
                clean(request.productReferenceName()) + " " + clean(request.productName())
        );
        if (tokens.isEmpty()) {
            return null;
        }

        if (tokens.contains("iphone")) {
            return IPHONE;
        }
        if (containsAppleProduct(tokens)) {
            return PRODUCTOS_APPLE;
        }
        if (tokens.stream().anyMatch(XIAOMI_TOKENS::contains)) {
            return XIAOMI;
        }
        if (containsMotorola(tokens)) {
            return MOTOROLA;
        }

        for (String brand : DIRECT_BRANDS) {
            if (tokens.contains(brand.toLowerCase(Locale.ROOT))) {
                return brand;
            }
        }
        return null;
    }

    private boolean containsAppleProduct(List<String> tokens) {
        return tokens.contains("macbook")
                || tokens.contains("imac")
                || tokens.contains("ipad")
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

    private boolean containsMotorola(List<String> tokens) {
        return tokens.contains("motorola")
                || tokens.contains("moto");
    }

    private boolean containsSequence(List<String> tokens, String first, String second) {
        for (int index = 0; index < tokens.size() - 1; index++) {
            if (first.equals(tokens.get(index)) && second.equals(tokens.get(index + 1))) {
                return true;
            }
        }
        return false;
    }

    private String resolveExternalCategory(ResolveRequest request) {
        String referenceCategory = canonicalizeExternalCategory(request.productReferenceCategory());
        String productCategory = canonicalizeExternalCategory(request.productCategory());

        if (referenceCategory == null) {
            return productCategory;
        }
        if (productCategory == null || referenceCategory.equals(productCategory)) {
            return referenceCategory;
        }
        return productCategory;
    }

    private String canonicalizeExternalCategory(String category) {
        if (isBlank(category)) {
            return null;
        }
        String key = TiendaPorteTextUtils.normalize(category);
        if (GENERIC_CATEGORY_KEYS.contains(key)) {
            return null;
        }
        return CATEGORY_ALIASES.get(key);
    }

    private void logResolutionIfNeeded(ResolveRequest request, ResolveResult result) {
        String referenceCanonical = canonicalizeAnyCategory(request.productReferenceCategory());
        String productCanonical = canonicalizeAnyCategory(request.productCategory());
        boolean referenceConflict = isRealConflict(request.productReferenceCategory(), referenceCanonical, result.category());
        boolean productConflict = isRealConflict(request.productCategory(), productCanonical, result.category());
        boolean corrected = result.reason() != ResolutionReason.EXTERNAL_CATEGORY
                || referenceConflict
                || productConflict
                || isGenericCategory(request.productReferenceCategory())
                || isGenericCategory(request.productCategory());

        if (!corrected) {
            return;
        }

        String message = "Catalog category corrected: productId={} name={} referenceCategory={} productCategory={} requestedCategory={} resolvedCategory={} reason={}";
        if (referenceConflict || productConflict) {
            LOGGER.warn(
                    message,
                    clean(request.externalProductId()),
                    firstNonBlank(request.productReferenceName(), request.productName()),
                    clean(request.productReferenceCategory()),
                    clean(request.productCategory()),
                    clean(request.requestedCategory()),
                    result.category(),
                    result.reason()
            );
            return;
        }

        LOGGER.debug(
                message,
                clean(request.externalProductId()),
                firstNonBlank(request.productReferenceName(), request.productName()),
                clean(request.productReferenceCategory()),
                clean(request.productCategory()),
                clean(request.requestedCategory()),
                result.category(),
                result.reason()
        );
    }

    private boolean isRealConflict(String rawExternalCategory, String externalCategory, String resolvedCategory) {
        if (externalCategory == null || resolvedCategory == null || externalCategory.equals(resolvedCategory)) {
            return false;
        }
        if (isGenericCategory(rawExternalCategory) || OTROS.equals(externalCategory)) {
            return false;
        }
        return !(PRODUCTOS_APPLE.equals(externalCategory) && IPHONE.equals(resolvedCategory));
    }

    private String canonicalizeAnyCategory(String category) {
        if (isBlank(category)) {
            return null;
        }
        String key = TiendaPorteTextUtils.normalize(category);
        if (GENERIC_CATEGORY_KEYS.contains(key)) {
            return OTROS;
        }
        return CATEGORY_ALIASES.get(key);
    }

    private boolean isGenericCategory(String category) {
        return !isBlank(category) && GENERIC_CATEGORY_KEYS.contains(TiendaPorteTextUtils.normalize(category));
    }

    private static Map<String, String> categoryAliases() {
        Map<String, String> aliases = new HashMap<>();
        putAlias(aliases, IPHONE, IPHONE);
        putAlias(aliases, XIAOMI, XIAOMI);
        putAlias(aliases, "REDMI", XIAOMI);
        putAlias(aliases, "POCO", XIAOMI);
        putAlias(aliases, SAMSUNG, SAMSUNG);
        putAlias(aliases, MOTOROLA, MOTOROLA);
        putAlias(aliases, "MOTO", MOTOROLA);
        putAlias(aliases, REALME, REALME);
        putAlias(aliases, INFINIX, INFINIX);
        putAlias(aliases, "APPLE", PRODUCTOS_APPLE);
        putAlias(aliases, "PRODUCTO APPLE", PRODUCTOS_APPLE);
        putAlias(aliases, PRODUCTOS_APPLE, PRODUCTOS_APPLE);
        putAlias(aliases, ARTICULOS_VARIOS, ARTICULOS_VARIOS);
        putAlias(aliases, PERFUMES, PERFUMES);
        putAlias(aliases, HUAWEI, HUAWEI);
        putAlias(aliases, HONOR, HONOR);
        putAlias(aliases, OPPO, OPPO);
        putAlias(aliases, SONY, SONY);
        putAlias(aliases, OTROS, OTROS);
        return Map.copyOf(aliases);
    }

    private static void putAlias(Map<String, String> aliases, String alias, String category) {
        aliases.put(TiendaPorteTextUtils.normalize(alias), category);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ResolveRequest(
            String productReferenceName,
            String productName,
            String productReferenceCategory,
            String productCategory,
            String requestedCategory,
            CatalogProductOrigin origin,
            String externalProductId
    ) {
        private static ResolveRequest empty() {
            return new ResolveRequest(null, null, null, null, null, null, null);
        }
    }

    public record ResolveResult(String category, ResolutionReason reason) {
    }

    public enum ResolutionReason {
        STRONG_NAME_MATCH,
        EXTERNAL_CATEGORY,
        REQUESTED_CATEGORY,
        FALLBACK_OTROS
    }
}
