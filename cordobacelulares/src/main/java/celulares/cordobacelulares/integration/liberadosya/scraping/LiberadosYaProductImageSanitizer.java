package celulares.cordobacelulares.integration.liberadosya.scraping;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiberadosYaProductImageSanitizer {

    public static final int SCHEMA_VERSION = 2;

    private static final int MIN_DIMENSION_PX = 100;
    private static final Pattern SIZE_SUFFIX = Pattern.compile("-(\\d{2,4})-(\\d{1,4})(?=\\.[a-z0-9]+$)");
    private static final Pattern SIZE_X_SUFFIX = Pattern.compile("[-_](\\d{2,4})x(\\d{2,4})(?=\\.[a-z0-9]+$)");
    private static final List<String> BLOCKED_TERMS = List.of(
            "pinterest",
            "pinext",
            "facebook",
            "instagram",
            "whatsapp",
            "twitter",
            "tiktok",
            "logo",
            "logos",
            "favicon",
            "icon",
            "icons",
            "sprite",
            "flag",
            "flags",
            "bandera",
            "argentina",
            "usa",
            "united-states",
            "currency",
            "country",
            "locale",
            "language",
            "payment",
            "mercadopago",
            "visa",
            "mastercard",
            "placeholder",
            "loading",
            "spinner",
            "avatar",
            "banner",
            "shipping",
            "trust",
            "badge",
            "social"
    );
    private static final List<String> GALLERY_ROOT_SELECTORS = List.of(
            "[data-store=\"product-image\"]",
            "[data-store^=\"product-image-\"]",
            "[data-store=\"product-gallery\"]",
            ".js-product-gallery",
            ".product-gallery",
            ".js-product-image-container",
            ".product-img-col"
    );
    private static final List<String> GALLERY_ELEMENT_SELECTORS = List.of(
            ".js-swiper-product .js-product-slide",
            ".js-product-slide",
            ".product-slide",
            ".js-product-thumb",
            ".product-thumb",
            ".product-slider-image",
            ".thumbnail-image",
            "[data-zoom-url]",
            "[data-desktop-zoom]",
            "source",
            "img"
    );

    private LiberadosYaProductImageSanitizer() {
    }

    static ExtractionResult fromGallery(Document document) {
        ImageAccumulator accumulator = new ImageAccumulator("galeria-html");
        if (document == null) {
            return accumulator.result();
        }

        for (Element root : galleryRoots(document)) {
            for (Element element : galleryElements(root)) {
                if (hasExcludedAncestor(element)) {
                    continue;
                }
                addElementCandidates(accumulator, element, document.baseUri());
            }
        }
        return accumulator.result();
    }

    static ExtractionResult fromUrls(List<String> rawUrls, String baseUri, String source) {
        ImageAccumulator accumulator = new ImageAccumulator(source);
        if (rawUrls == null) {
            return accumulator.result();
        }
        for (String rawUrl : rawUrls) {
            accumulator.add(rawUrl, baseUri, null);
        }
        return accumulator.result();
    }

    public static SanitizedImages sanitizeStoredProductImages(String primaryImage, List<String> images, String baseUri) {
        List<String> candidates = new ArrayList<>();
        if (!isBlank(primaryImage)) {
            candidates.add(primaryImage);
        }
        if (images != null) {
            candidates.addAll(images);
        }
        ExtractionResult result = fromUrls(candidates, baseUri, "snapshot");
        return new SanitizedImages(result.images().isEmpty() ? null : result.images().get(0), result.images());
    }

    static String dedupeKey(String rawUrl) {
        String normalized = normalizeUrl(rawUrl, null);
        if (normalized == null) {
            return "";
        }
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            path = SIZE_SUFFIX.matcher(path).replaceAll("");
            path = SIZE_X_SUFFIX.matcher(path).replaceAll("");
            path = path.replaceAll("/+", "/");
            return (uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT)) + path;
        } catch (RuntimeException ex) {
            return normalized.toLowerCase(Locale.ROOT)
                    .replaceAll("-(\\d{2,4})-(\\d{1,4})(?=\\.[a-z0-9]+$)", "")
                    .replaceAll("[-_](\\d{2,4})x(\\d{2,4})(?=\\.[a-z0-9]+$)", "");
        }
    }

    private static List<Element> galleryRoots(Document document) {
        Set<Element> roots = new LinkedHashSet<>();
        for (String selector : GALLERY_ROOT_SELECTORS) {
            for (Element element : document.select(selector)) {
                if (!hasExcludedAncestor(element) && !looksLikeRelatedProductBlock(element)) {
                    roots.add(element);
                }
            }
        }
        return List.copyOf(roots);
    }

    private static List<Element> galleryElements(Element root) {
        Set<Element> elements = new LinkedHashSet<>();
        elements.add(root);
        for (String selector : GALLERY_ELEMENT_SELECTORS) {
            elements.addAll(root.select(selector));
        }
        return List.copyOf(elements);
    }

    private static void addElementCandidates(ImageAccumulator accumulator, Element element, String baseUri) {
        accumulator.add(element.attr("data-zoom-url"), baseUri, element);
        accumulator.add(element.attr("data-desktop-zoom"), baseUri, element);
        accumulator.add(element.attr("data-src"), baseUri, element);
        accumulator.add(element.attr("data-original"), baseUri, element);
        accumulator.add(element.attr("src"), baseUri, element);

        for (String srcsetCandidate : srcsetCandidates(element.attr("srcset"))) {
            accumulator.add(srcsetCandidate, baseUri, element);
        }
        for (String srcsetCandidate : srcsetCandidates(element.attr("data-srcset"))) {
            accumulator.add(srcsetCandidate, baseUri, element);
        }
    }

    private static List<String> srcsetCandidates(String srcset) {
        if (isBlank(srcset)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String entry : srcset.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length > 0 && !parts[0].isBlank()) {
                values.add(parts[0]);
            }
        }
        return values;
    }

    private static ValidationResult validate(String rawUrl, String baseUri, Element context) {
        String normalized = normalizeUrl(rawUrl, baseUri);
        if (normalized == null) {
            return ValidationResult.rejected("invalidUrl");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (BLOCKED_TERMS.stream().anyMatch(lower::contains)) {
            return ValidationResult.rejected(blockedReason(lower));
        }
        if (context != null && hasExcludedAncestor(context)) {
            return ValidationResult.rejected("excludedContext");
        }
        if (context != null && hasTinyDimensions(context)) {
            return ValidationResult.rejected("tinyDimensions");
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (RuntimeException ex) {
            return ValidationResult.rejected("invalidUrl");
        }
        if (!isAllowedProductCdn(uri)) {
            return ValidationResult.rejected("notProductCdn");
        }
        String dedupeKey = dedupeKey(normalized);
        if (dedupeKey.isBlank()) {
            return ValidationResult.rejected("invalidDedupeKey");
        }
        return ValidationResult.accepted(normalized, dedupeKey, quality(normalized, context));
    }

    private static String blockedReason(String lowerUrl) {
        for (String term : BLOCKED_TERMS) {
            if (lowerUrl.contains(term)) {
                String normalized = term.replace("-", "");
                return "blocked" + Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
            }
        }
        return "blocked";
    }

    private static boolean isAllowedProductCdn(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        boolean allowedHost = host.equals("acdn-us.mitiendanube.com")
                || host.endsWith(".mitiendanube.com")
                || host.endsWith(".nuvemshop.com");
        boolean productPath = path.matches("^/(tmp/)?stores/[^/]+/[^/]+/[^/]+/products/.+");
        return allowedHost && productPath && !path.contains("/themes/");
    }

    private static boolean hasExcludedAncestor(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            String tag = current.tagName();
            if ("header".equalsIgnoreCase(tag) || "footer".equalsIgnoreCase(tag) || "nav".equalsIgnoreCase(tag)) {
                return true;
            }
            String dataStore = current.attr("data-store").toLowerCase(Locale.ROOT);
            if (dataStore.equals("navigation")
                    || dataStore.equals("footer")
                    || dataStore.equals("related-products")
                    || dataStore.equals("banner-services")
                    || dataStore.equals("shipping-calculator")
                    || dataStore.equals("newsletter-form")
                    || dataStore.startsWith("product-item-")) {
                return true;
            }
            String className = current.className().toLowerCase(Locale.ROOT);
            if (containsAny(className,
                    "related-products",
                    "recommended",
                    "cross-selling",
                    "social",
                    "currency",
                    "locale",
                    "language",
                    "country",
                    "newsletter",
                    "banner")) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeRelatedProductBlock(Element element) {
        String dataStore = element.attr("data-store").toLowerCase(Locale.ROOT);
        String className = element.className().toLowerCase(Locale.ROOT);
        return dataStore.startsWith("product-item-")
                || dataStore.equals("related-products")
                || containsAny(className, "related-products", "recommended", "cross-selling");
    }

    private static boolean hasTinyDimensions(Element element) {
        int width = firstPositiveInt(
                element.attr("width"),
                element.attr("data-width"),
                element.attr("data-original-width")
        );
        int height = firstPositiveInt(
                element.attr("height"),
                element.attr("data-height"),
                element.attr("data-original-height")
        );
        return (width > 0 && width < MIN_DIMENSION_PX) || (height > 0 && height < MIN_DIMENSION_PX);
    }

    private static int quality(String normalizedUrl, Element element) {
        int fromUrl = qualityFromUrl(normalizedUrl);
        int width = element == null ? 0 : firstPositiveInt(
                element.attr("width"),
                element.attr("data-width"),
                element.attr("data-original-width")
        );
        int height = element == null ? 0 : firstPositiveInt(
                element.attr("height"),
                element.attr("data-height"),
                element.attr("data-original-height")
        );
        return Math.max(fromUrl, Math.max(width, height));
    }

    private static int qualityFromUrl(String normalizedUrl) {
        try {
            String path = URI.create(normalizedUrl).getPath();
            if (path == null) {
                return 0;
            }
            Matcher suffix = SIZE_SUFFIX.matcher(path.toLowerCase(Locale.ROOT));
            int best = 0;
            while (suffix.find()) {
                best = Math.max(best, Math.max(parseInt(suffix.group(1)), parseInt(suffix.group(2))));
            }
            Matcher sizeX = SIZE_X_SUFFIX.matcher(path.toLowerCase(Locale.ROOT));
            while (sizeX.find()) {
                best = Math.max(best, Math.max(parseInt(sizeX.group(1)), parseInt(sizeX.group(2))));
            }
            return best;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static int firstPositiveInt(String... values) {
        for (String value : values) {
            int parsed = parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        }
        return 0;
    }

    private static int parseInt(String value) {
        if (isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String normalizeUrl(String raw, String baseUri) {
        if (isBlank(raw)) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.startsWith("//")) {
            candidate = "https:" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            if (!uri.isAbsolute() && !isBlank(baseUri)) {
                uri = URI.create(baseUri).resolve(uri);
            }
            if (!uri.isAbsolute()) {
                return null;
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            URI clean = new URI(
                    "https",
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    cleanQuery(uri.getQuery()),
                    null
            );
            return clean.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String cleanQuery(String query) {
        if (isBlank(query)) {
            return null;
        }
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            String key = pair.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (!Set.of("w", "width", "h", "height", "size", "resize", "fit", "crop", "q", "quality").contains(key)) {
                kept.add(pair);
            }
        }
        return kept.isEmpty() ? null : String.join("&", kept);
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static final class ExtractionResult {
        private final String source;
        private final int candidates;
        private final List<String> images;
        private final Map<String, Integer> rejectedByReason;

        private ExtractionResult(String source, int candidates, List<String> images, Map<String, Integer> rejectedByReason) {
            this.source = source;
            this.candidates = candidates;
            this.images = images;
            this.rejectedByReason = rejectedByReason;
        }

        String source() {
            return source;
        }

        int candidates() {
            return candidates;
        }

        List<String> images() {
            return images;
        }

        int accepted() {
            return images.size();
        }

        int rejected() {
            return rejectedByReason.values().stream().mapToInt(Integer::intValue).sum();
        }

        Map<String, Integer> rejectedByReason() {
            return rejectedByReason;
        }
    }

    public record SanitizedImages(String primaryImage, List<String> images) {
    }

    private record AcceptedImage(String url, int quality) {
    }

    private record ValidationResult(boolean accepted, String url, String dedupeKey, int quality, String reason) {
        private static ValidationResult accepted(String url, String dedupeKey, int quality) {
            return new ValidationResult(true, url, dedupeKey, quality, null);
        }

        private static ValidationResult rejected(String reason) {
            return new ValidationResult(false, null, null, 0, reason);
        }
    }

    private static final class ImageAccumulator {
        private final String source;
        private final Map<String, AcceptedImage> imagesByKey = new LinkedHashMap<>();
        private final Map<String, Integer> rejectedByReason = new LinkedHashMap<>();
        private int candidates;

        private ImageAccumulator(String source) {
            this.source = source;
        }

        private void add(String rawUrl, String baseUri, Element context) {
            if (isBlank(rawUrl)) {
                return;
            }
            candidates++;
            ValidationResult validation = validate(rawUrl, baseUri, context);
            if (!validation.accepted()) {
                reject(validation.reason());
                return;
            }
            AcceptedImage existing = imagesByKey.get(validation.dedupeKey());
            if (existing == null) {
                imagesByKey.put(validation.dedupeKey(), new AcceptedImage(validation.url(), validation.quality()));
                return;
            }
            if (validation.quality() > existing.quality()) {
                imagesByKey.put(validation.dedupeKey(), new AcceptedImage(validation.url(), validation.quality()));
            }
            reject("duplicateVariant");
        }

        private void reject(String reason) {
            String safeReason = isBlank(reason) ? "rejected" : reason;
            rejectedByReason.merge(safeReason, 1, Integer::sum);
        }

        private ExtractionResult result() {
            List<String> images = imagesByKey.values().stream()
                    .map(AcceptedImage::url)
                    .toList();
            return new ExtractionResult(
                    source,
                    candidates,
                    List.copyOf(images),
                    Map.copyOf(rejectedByReason)
            );
        }
    }
}
