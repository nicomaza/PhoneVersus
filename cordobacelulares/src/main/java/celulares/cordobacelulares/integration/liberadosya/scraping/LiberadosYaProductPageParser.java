package celulares.cordobacelulares.integration.liberadosya.scraping;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaMoney;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaPrices;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.matching.LiberadosYaProductIdentityParser;
import celulares.cordobacelulares.integration.liberadosya.matching.ProductIdentity;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LiberadosYaProductPageParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaProductPageParser.class);

    private final LiberadosYaPriceParser priceParser;
    private final LiberadosYaSpecificationParser specificationParser;
    private final LiberadosYaProductIdentityParser identityParser;
    private final ObjectMapper objectMapper;

    public LiberadosYaProductPageParser(
            LiberadosYaPriceParser priceParser,
            LiberadosYaSpecificationParser specificationParser,
            LiberadosYaProductIdentityParser identityParser,
            ObjectMapper objectMapper
    ) {
        this.priceParser = priceParser;
        this.specificationParser = specificationParser;
        this.identityParser = identityParser;
        this.objectMapper = objectMapper;
    }

    public LiberadosYaProduct parse(String slug, String arsUrl, String arsHtml, String usdUrl, String usdHtml, Instant extractedAt) {
        Document arsRaw = Jsoup.parse(arsHtml == null ? "" : arsHtml, arsUrl);
        Document usdRaw = Jsoup.parse(usdHtml == null ? "" : usdHtml, usdUrl);
        Document ars = arsRaw.clone();
        Document usd = usdRaw.clone();
        ars.select("script:not([type=application/ld+json]),style,iframe,form,button,noscript").remove();
        usd.select("script:not([type=application/ld+json]),style,iframe,form,button,noscript").remove();

        String canonical = canonical(ars);
        String usdCanonical = canonical(usd);
        JsonNode arsProductJson = productJsonLd(ars, firstNonBlank(canonical, arsUrl));
        String title = firstNonBlank(
                meta(ars, "meta[property=og:title]", "content"),
                productH1(ars, slug),
                jsonText(arsProductJson, "name"),
                cleanTitle(ars.title())
        );
        JsonNode usdProductJson = productJsonLd(usd, firstNonBlank(usdCanonical, usdUrl));

        Map<String, Object> validation = validation(slug, arsUrl, canonical, usdUrl, usdCanonical, title, arsProductJson);
        String mainArsText = mainProductText(ars, title);
        String mainUsdText = mainProductText(usd, title);
        LiberadosYaMoney arsPrices = priceParser.parse(
                mainArsText,
                LiberadosYaPriceParser.CurrencyMode.ARS,
                arsUrl,
                "precios.ars",
                issue -> addValidationInconsistency(validation, issue)
        );
        LiberadosYaMoney usdPrices = priceParser.parse(
                mainUsdText,
                LiberadosYaPriceParser.CurrencyMode.USD,
                usdUrl,
                "precios.usd",
                issue -> addValidationInconsistency(validation, issue)
        );
        Map<String, Map<String, Object>> specs = specificationParser.parse(ars);
        Map<String, Object> fichaResumida = fichaResumida(ars);
        List<String> ventajas = ventajas(ars);
        String description = description(ars, arsProductJson);
        ProductIdentity identity = identityParser.parseText(title, null);
        List<String> images = images(arsRaw, arsProductJson, canonical, arsUrl, title);
        String availability = availability(mainArsText, arsProductJson);

        return new LiberadosYaProduct(
                title,
                slug,
                arsUrl,
                usdUrl,
                identity.brand(),
                identity.modelKey(),
                title,
                new LiberadosYaPrices(arsPrices, usdPrices),
                availability,
                hasStock(mainArsText, availability, arsPrices),
                colores(ars, mainArsText),
                variants(ars),
                description,
                tituloFichaTecnica(ars),
                fichaResumida,
                ventajas,
                specs,
                images.isEmpty() ? null : images.get(0),
                images,
                validation,
                extractedAt
        );
    }

    private String productH1(Document document, String slug) {
        List<String> slugTokens = List.of(slug.replace('-', ' ').toUpperCase(Locale.ROOT).split("\\s+"));
        String fallback = null;
        for (Element h1 : document.select("h1")) {
            String text = clean(h1.text());
            if (text.isBlank() || "LIBERADOSYA".equalsIgnoreCase(text)) {
                continue;
            }
            if (fallback == null) {
                fallback = text;
            }
            String upper = text.toUpperCase(Locale.ROOT);
            long hits = slugTokens.stream().filter(token -> token.length() > 1 && upper.contains(token)).count();
            if (hits >= Math.min(2, slugTokens.size())) {
                return text;
            }
        }
        return fallback;
    }

    private String mainProductText(Document document, String title) {
        String text = clean(document.body() == null ? document.text() : document.body().text());
        if (!isBlank(title)) {
            int titleIndex = indexOfIgnoreCase(text, title);
            if (titleIndex >= 0) {
                text = text.substring(titleIndex);
            }
        }
        for (String marker : List.of("Productos similares", "Medios de pago")) {
            int markerIndex = indexOfIgnoreCase(text, marker);
            if (markerIndex >= 0) {
                text = text.substring(0, markerIndex);
            }
        }
        return text;
    }

    private Map<String, Object> fichaResumida(Document document) {
        Map<String, Object> ficha = new LinkedHashMap<>();
        Element heading = headingContaining(document, "Ficha tecnica");
        if (heading == null) {
            return ficha;
        }
        for (Element sibling = heading.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
            if (sibling.tagName().matches("h[1-6]") && containsAny(sibling.text(), "Ventajas", "Caracteristicas tecnicas")) {
                break;
            }
            for (String line : lines(sibling.text())) {
                int split = firstSpace(line);
                if (split > 0) {
                    ficha.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            }
        }
        return ficha;
    }

    private List<String> ventajas(Document document) {
        Element heading = headingContaining(document, "Ventajas");
        if (heading == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Element sibling = heading.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
            if (sibling.tagName().matches("h[1-6]")) {
                break;
            }
            for (Element li : sibling.select("li")) {
                addUnique(values, clean(li.text()));
            }
            if (!sibling.select("li").isEmpty()) {
                continue;
            }
            for (String line : lines(sibling.text())) {
                addUnique(values, line);
            }
        }
        return List.copyOf(values);
    }

    private String tituloFichaTecnica(Document document) {
        Element heading = headingContaining(document, "Ficha tecnica");
        return heading == null ? null : clean(heading.text());
    }

    private String description(Document document, JsonNode productJson) {
        String jsonDescription = jsonText(productJson, "description");
        if (!isBlank(jsonDescription)) {
            return clean(jsonDescription);
        }
        Element productDescription = headingAfterProductBlock(document);
        if (productDescription != null) {
            StringBuilder description = new StringBuilder();
            for (Element sibling = productDescription.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
                if (sibling.tagName().matches("h[1-6]") && containsAny(sibling.text(), "Ficha tecnica", "Caracteristicas tecnicas")) {
                    break;
                }
                String text = clean(sibling.text());
                if (!text.isBlank()) {
                    description.append(text).append(' ');
                }
            }
            if (!description.isEmpty()) {
                return clean(description.toString());
            }
        }
        return firstNonBlank(
                meta(document, "meta[property=og:description]", "content"),
                meta(document, "meta[name=description]", "content")
        );
    }

    private Element headingAfterProductBlock(Document document) {
        for (Element heading : document.select("h1,h2")) {
            String text = clean(heading.text());
            if (!text.isBlank() && !"LiberadosYa".equalsIgnoreCase(text) && !containsAny(text, "Ficha tecnica")) {
                return heading;
            }
        }
        return null;
    }

    private List<String> images(Document document, JsonNode productJson, String canonical, String arsUrl, String title) {
        LiberadosYaProductImageSanitizer.ExtractionResult galleryImages =
                LiberadosYaProductImageSanitizer.fromGallery(document);
        if (!galleryImages.images().isEmpty()) {
            logImageSummary(title, galleryImages);
            return galleryImages.images();
        }

        LiberadosYaProductImageSanitizer.ExtractionResult jsonImages =
                LiberadosYaProductImageSanitizer.fromUrls(jsonImageValues(productJson), document.baseUri(), "json-ld");
        if (!jsonImages.images().isEmpty()) {
            logImageSummary(title, jsonImages);
            return jsonImages.images();
        }

        boolean canonicalMatches = canonical == null || samePath(canonical, arsUrl);
        String ogCandidate = meta(document, "meta[property=og:image]", "content");
        LiberadosYaProductImageSanitizer.ExtractionResult ogImage = canonicalMatches
                ? LiberadosYaProductImageSanitizer.fromUrls(
                isBlank(ogCandidate) ? List.of() : List.of(ogCandidate),
                document.baseUri(),
                "og-image"
        )
                : LiberadosYaProductImageSanitizer.fromUrls(List.of(), document.baseUri(), "og-image");
        logImageSummary(title, ogImage);
        return ogImage.images();
    }

    private void logImageSummary(String title, LiberadosYaProductImageSanitizer.ExtractionResult result) {
        LOGGER.debug(
                "LiberadosYa product image extraction. cid={} product={} source={} candidates={} accepted={} rejected={} rejectedByReason={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                title,
                result.source(),
                result.candidates(),
                result.accepted(),
                result.rejected(),
                result.rejectedByReason()
        );
    }

    private List<String> jsonImageValues(JsonNode productJson) {
        if (productJson == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        collectJsonImageValues(productJson.get("image"), values);
        return values;
    }

    private void collectJsonImageValues(JsonNode value, List<String> values) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(child -> collectJsonImageValues(child, values));
            return;
        }
        if (value.isObject()) {
            collectJsonImageValues(value.get("url"), values);
            collectJsonImageValues(value.get("contentUrl"), values);
            collectJsonImageValues(value.get("thumbnailUrl"), values);
            return;
        }
        String text = clean(value.asText());
        if (!text.isBlank()) {
            values.add(text);
        }
    }

    private List<String> colores(Document document, String mainText) {
        Set<String> colors = new LinkedHashSet<>();
        for (Element element : document.select("select option, [data-option], .variant, .js-product-variants label")) {
            String text = clean(element.text());
            if (looksLikeColor(text)) {
                colors.add(text);
            }
        }
        int colorIndex = indexOfIgnoreCase(mainText, "Color:");
        if (colorIndex >= 0) {
            String after = mainText.substring(colorIndex + "Color:".length());
            String color = clean(after.split(" ")[0]);
            if (looksLikeColor(color)) {
                colors.add(color);
            }
        }
        return List.copyOf(colors);
    }

    private List<String> variants(Document document) {
        Set<String> variants = new LinkedHashSet<>();
        for (Element element : document.select("select option, [data-variant], .js-product-variants option")) {
            String text = clean(element.text());
            if (!text.isBlank() && text.length() <= 80) {
                variants.add(text);
            }
        }
        return List.copyOf(variants);
    }

    private String availability(String text, JsonNode productJson) {
        JsonNode offers = productJson == null ? null : productJson.path("offers");
        String jsonAvailability = jsonText(offers, "availability");
        if (!isBlank(jsonAvailability)) {
            int slash = jsonAvailability.lastIndexOf('/');
            return slash >= 0 ? jsonAvailability.substring(slash + 1) : jsonAvailability;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("sin stock") || lower.contains("no tenemos mas stock") || lower.contains("no tenemos mas stock")) {
            return "OutOfStock";
        }
        return "InStock";
    }

    private Boolean hasStock(String text, String availability, LiberadosYaMoney arsPrices) {
        String lower = text.toLowerCase(Locale.ROOT);
        if ("OutOfStock".equalsIgnoreCase(availability)
                || lower.contains("sin stock")
                || lower.contains("no tenemos mas stock")) {
            return false;
        }
        return arsPrices == null || arsPrices.getActual() != null || lower.contains("stock") || "InStock".equalsIgnoreCase(availability);
    }

    private Map<String, Object> validation(String slug, String arsUrl, String canonical, String usdUrl, String usdCanonical, String title, JsonNode productJson) {
        Map<String, Object> validation = new LinkedHashMap<>();
        List<String> inconsistencies = new ArrayList<>();
        validation.put("canonical", canonical);
        validation.put("canonicalDolares", usdCanonical);
        boolean canonicalMatches = canonical == null || samePath(canonical, arsUrl);
        boolean usdCanonicalMatches = usdCanonical == null || samePath(usdCanonical, usdUrl) || samePath(usdCanonical, arsUrl);
        validation.put("canonicalCoincide", canonicalMatches);
        validation.put("canonicalDolaresCoincide", usdCanonicalMatches);
        if (!canonicalMatches) {
            inconsistencies.add("canonical-no-coincide");
        }
        if (!usdCanonicalMatches) {
            inconsistencies.add("canonical-dolares-no-coincide");
        }
        if (!slugCompatible(slug, title)) {
            inconsistencies.add("slug-y-nombre-con-baja-compatibilidad");
        }
        validation.put("jsonLdProductoPrincipal", productJson != null);
        validation.put("posiblesInconsistencias", inconsistencies);
        return validation;
    }

    private void addValidationInconsistency(Map<String, Object> validation, String inconsistency) {
        if (validation == null || isBlank(inconsistency)) {
            return;
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

    private boolean samePath(String first, String second) {
        try {
            URI a = URI.create(first);
            URI b = URI.create(second);
            return cleanPath(a.getPath()).equals(cleanPath(b.getPath()));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String cleanPath(String path) {
        String clean = path == null ? "" : path;
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.replaceFirst("^/us", "");
    }

    private boolean slugCompatible(String slug, String title) {
        if (isBlank(slug) || isBlank(title)) {
            return true;
        }
        String upperTitle = title.toUpperCase(Locale.ROOT);
        String[] tokens = slug.replace('-', ' ').toUpperCase(Locale.ROOT).split("\\s+");
        long hits = 0;
        long considered = 0;
        for (String token : tokens) {
            if (token.length() < 2 || token.matches("[A-Z0-9]{4,}")) {
                continue;
            }
            considered++;
            if (upperTitle.contains(token)) {
                hits++;
            }
        }
        return considered == 0 || hits >= Math.max(1, considered / 2);
    }

    private JsonNode productJsonLd(Document document, String expectedUrl) {
        List<JsonNode> products = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode node = objectMapper.readTree(script.html());
                collectProductNodes(node, products);
            } catch (Exception ignored) {
                // Broken JSON-LD is treated as unavailable and recorded through validation.
            }
        }
        for (JsonNode product : products) {
            if (jsonProductMatchesUrl(product, expectedUrl, document.baseUri())) {
                return product;
            }
        }
        return null;
    }

    private void collectProductNodes(JsonNode node, List<JsonNode> products) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectProductNodes(child, products);
            }
            return;
        }
        if (node.has("@graph")) {
            collectProductNodes(node.get("@graph"), products);
        }
        JsonNode type = node.get("@type");
        if (type != null && type.toString().toLowerCase(Locale.ROOT).contains("product")) {
            products.add(node);
        }
        for (JsonNode child : node) {
            if (child.isContainerNode()) {
                collectProductNodes(child, products);
            }
        }
    }

    private boolean jsonProductMatchesUrl(JsonNode product, String expectedUrl, String baseUri) {
        if (isBlank(expectedUrl)) {
            return false;
        }
        List<String> urls = new ArrayList<>();
        if (product != null) {
            addJsonUrl(urls, product.get("@id"), baseUri);
            addJsonUrl(urls, product.get("url"), baseUri);
            addJsonUrl(urls, product.get("mainEntityOfPage"), baseUri);
        }
        for (String url : urls) {
            if (samePath(url, expectedUrl)) {
                return true;
            }
        }
        return false;
    }

    private void addJsonUrl(List<String> urls, JsonNode value, String baseUri) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(child -> addJsonUrl(urls, child, baseUri));
            return;
        }
        if (value.isObject()) {
            addJsonUrl(urls, value.get("@id"), baseUri);
            addJsonUrl(urls, value.get("url"), baseUri);
            return;
        }
        String url = normalizeUrl(value.asText(), baseUri);
        if (url != null) {
            urls.add(url);
        }
    }

    private String canonical(Document document) {
        return normalizeUrl(document.select("link[rel=canonical]").attr("href"), document.baseUri());
    }

    private String meta(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        return element == null ? null : clean(element.attr(attribute));
    }

    private String jsonText(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || !node.has(field)) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : clean(value.asText());
    }

    private Element headingContaining(Document document, String value) {
        for (Element heading : document.select("h1,h2,h3,h4,h5,h6")) {
            if (containsAny(heading.text(), value)) {
                return heading;
            }
        }
        return null;
    }

    private boolean containsAny(String value, String... needles) {
        String normalized = stripAccents(value).toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (normalized.contains(stripAccents(needle).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeUrl(String raw, String baseUri) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!uri.isAbsolute()) {
                uri = URI.create(baseUri).resolve(uri);
            }
            String value = uri.toString();
            return value.startsWith("http://") ? "https://" + value.substring("http://".length()) : value;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String cleanTitle(String title) {
        if (title == null) {
            return null;
        }
        return clean(title.replace("- LiberadosYa", ""));
    }

    private List<String> lines(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        String[] lines = value.replace('\u00A0', ' ').split("\\r?\\n| {2,}");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String clean = clean(line);
            if (!clean.isBlank()) {
                cleaned.add(clean);
            }
        }
        return cleaned;
    }

    private void addUnique(List<String> values, String value) {
        if (!isBlank(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private boolean looksLikeColor(String text) {
        return !isBlank(text) && text.length() <= 40 && text.matches("[\\p{L} ]+");
    }

    private int firstSpace(String line) {
        return line == null ? -1 : line.indexOf(' ');
    }

    private int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private String specsText(Map<String, Map<String, Object>> specs) {
        return specs == null ? "" : specs.toString();
    }

    private String stripAccents(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return clean(value);
            }
        }
        return null;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
