package celulares.cordobacelulares.integration.liberadosya.scraping;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaMoney;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaPrices;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LiberadosYaCatalogScriptParser {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TRANSFER_DENOMINATOR = new BigDecimal("98");
    private static final Pattern EXCHANGE_PATTERN = Pattern.compile("const\\s+exchange\\s*=\\s*(\\d+(?:\\.\\d+)?)\\s*;");
    private static final Pattern STOREFRONT_TITLES_PATTERN = Pattern.compile(
            "const\\s+storefrontPhoneTitles\\s*=\\s*(\\{.*?})\\s*;",
            Pattern.DOTALL
    );

    private final ObjectMapper objectMapper;

    public LiberadosYaCatalogScriptParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedCatalog parse(
            URI baseUri,
            URI catalogUri,
            String catalogScript,
            URI fichasUri,
            String fichasScript,
            URI pricingUri,
            String pricingScript,
            URI appUri,
            String appScript,
            Instant extractedAt
    ) {
        JsonNode catalog = assignedArray(catalogScript, "catalogo");
        JsonNode fichas = assignedArray(fichasScript, "fichas");
        if (catalog.size() != fichas.size()) {
            throw unsupported("catalogo.js y fichas.js no tienen la misma cantidad de elementos");
        }
        if (catalog.isEmpty()) {
            throw unsupported("El catalogo publicado esta vacio");
        }

        BigDecimal exchange = exchange(appScript);
        validatePricingFormula(pricingScript);
        validateAppFormula(appScript);
        Map<Integer, String> storefrontTitles = storefrontTitles(appScript);

        List<LiberadosYaProduct> products = new ArrayList<>();
        int retiredProducts = 0;
        int unpublishedProducts = 0;
        for (int index = 0; index < catalog.size(); index++) {
            JsonNode entry = catalog.get(index);
            JsonNode ficha = fichas.get(index);
            validateCatalogEntry(entry, index);
            if (ficha != null && !ficha.isObject() && !ficha.isNull()) {
                throw unsupported("Ficha invalida en el indice " + index);
            }
            if (ficha != null && ficha.path("retiredFromStorefront").asBoolean(false)) {
                retiredProducts++;
                continue;
            }
            JsonNode cashNode = entry.get(2);
            if (!isPublicCash(cashNode)) {
                unpublishedProducts++;
                continue;
            }

            String category = entry.get(0).textValue().trim();
            String sourceName = entry.get(1).textValue().trim();
            String currency = entry.get(3).textValue().trim().toUpperCase(Locale.ROOT);
            if (!"ARS".equals(currency) && !"USD".equals(currency)) {
                throw unsupported("Moneda no soportada en el indice " + index + ": " + currency);
            }
            String displayName = storefrontTitles.getOrDefault(index, sourceName);
            products.add(product(
                    baseUri,
                    catalogUri,
                    fichasUri,
                    pricingUri,
                    appUri,
                    index,
                    category,
                    sourceName,
                    displayName,
                    cashNode.decimalValue(),
                    currency,
                    ficha == null || ficha.isNull() ? objectMapper.createObjectNode() : ficha,
                    exchange,
                    extractedAt
            ));
        }
        return new ParsedCatalog(List.copyOf(products), catalog.size(), retiredProducts, unpublishedProducts, exchange);
    }

    private LiberadosYaProduct product(
            URI baseUri,
            URI catalogUri,
            URI fichasUri,
            URI pricingUri,
            URI appUri,
            int index,
            String category,
            String sourceName,
            String displayName,
            BigDecimal cashCents,
            String currency,
            JsonNode ficha,
            BigDecimal exchange,
            Instant extractedAt
    ) {
        String slug = slug(sourceName);
        LiberadosYaPrices prices = prices(cashCents, currency, exchange);
        Map<String, LiberadosYaPrices> variantPrices = variantPrices(ficha.path("colorPrices"), currency, exchange);
        List<String> colors = colors(ficha, variantPrices);
        Map<String, Map<String, Object>> specifications = specifications(ficha.path("attributes"));
        Map<String, Object> summary = specifications.isEmpty()
                ? Map.of()
                : Map.copyOf(specifications.get("Ficha tecnica"));
        List<String> images = images(baseUri, ficha);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("catalogIndex", index);
        validation.put("sourceName", sourceName);
        validation.put("category", category);
        validation.put("currency", currency);
        validation.put("catalogScript", catalogUri.toString());
        validation.put("fichasScript", fichasUri.toString());
        validation.put("pricingScript", pricingUri.toString());
        validation.put("appScript", appUri.toString());

        return new LiberadosYaProduct(
                displayName,
                slug,
                baseUri.resolve("/#" + slug).toString(),
                null,
                brand(category),
                displayName,
                displayName,
                prices,
                variantPrices,
                "DISPONIBLE",
                true,
                colors,
                List.copyOf(variantPrices.keySet()),
                firstText(ficha, "summaryDescription", "description", "spec"),
                null,
                summary,
                stringList(ficha.path("usageBenefits")),
                specifications,
                images.isEmpty() ? null : images.get(0),
                images,
                validation,
                extractedAt
        );
    }

    private JsonNode assignedArray(String script, String variable) {
        if (script == null) {
            throw unsupported(variable + ".js no fue descargado");
        }
        Pattern pattern = Pattern.compile(
                "^\\s*window\\." + Pattern.quote(variable) + "\\s*=\\s*(\\[.*])\\s*;?\\s*$",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(script);
        if (!matcher.matches()) {
            throw unsupported("No se pudo interpretar window." + variable);
        }
        try {
            JsonNode value = objectMapper.readTree(matcher.group(1));
            if (value == null || !value.isArray()) {
                throw unsupported("window." + variable + " no es un arreglo");
            }
            return value;
        } catch (JsonProcessingException ex) {
            throw new LiberadosYaIntegrationException("window." + variable + " no contiene JSON valido", ex);
        }
    }

    private Map<Integer, String> storefrontTitles(String appScript) {
        Matcher matcher = STOREFRONT_TITLES_PATTERN.matcher(appScript == null ? "" : appScript);
        if (!matcher.find()) {
            throw unsupported("No se pudo interpretar storefrontPhoneTitles");
        }
        try {
            JsonNode titles = objectMapper.readTree(matcher.group(1));
            if (!titles.isObject()) {
                throw unsupported("storefrontPhoneTitles no es un objeto");
            }
            Map<Integer, String> result = new LinkedHashMap<>();
            titles.fields().forEachRemaining(entry -> {
                try {
                    int index = Integer.parseInt(entry.getKey());
                    if (!entry.getValue().isTextual() || entry.getValue().textValue().isBlank()) {
                        throw unsupported("Titulo comercial invalido para el indice " + index);
                    }
                    result.put(index, entry.getValue().textValue().trim());
                } catch (NumberFormatException ex) {
                    throw unsupported("Indice invalido en storefrontPhoneTitles");
                }
            });
            return Map.copyOf(result);
        } catch (JsonProcessingException ex) {
            throw new LiberadosYaIntegrationException("storefrontPhoneTitles no contiene JSON valido", ex);
        }
    }

    private BigDecimal exchange(String appScript) {
        Matcher matcher = EXCHANGE_PATTERN.matcher(appScript == null ? "" : appScript);
        if (!matcher.find()) {
            throw unsupported("No se pudo interpretar la constante exchange");
        }
        BigDecimal exchange = new BigDecimal(matcher.group(1));
        if (exchange.signum() <= 0) {
            throw unsupported("La constante exchange no es positiva");
        }
        return exchange;
    }

    private void validatePricingFormula(String pricingScript) {
        String normalized = normalizedScript(pricingScript);
        require(normalized, "functioncurrentCash(product,color){returnproduct.colorPrices?.[color]??product.cash;}");
    }

    private void validateAppFormula(String appScript) {
        String normalized = normalizedScript(appScript).replace('"', '\'');
        require(normalized, "constproducts=window.catalogo.map(([category,sourceName,cash,currency],i)=>({");
        require(normalized, "id:i,category,sourceName");
        require(normalized, "cash,currency");
        require(normalized, "...window.fichas[i]");
        require(normalized, "...(window.fichas[i]?.retiredFromStorefront===true?{cash:null}:{})");
        require(normalized, "...(storefrontPhoneTitles[i]?{name:storefrontPhoneTitles[i]");
        require(normalized, "consthasPublicPrice=product=>Number.isFinite(product.cash)&&product.cash>0;");
        require(normalized, "functionquote(p,index,color){");
        require(normalized, "constactive=currentCash(p,color);constcashArs=p.currency==='USD'?active*exchange:active;");
        require(normalized, "constrounding=(p.net7Pricing||p.net10Pricing)?Math.ceil:Math.round;");
        require(normalized, "consttransferArs=Math.ceil(cashArs*100/98);");
        require(normalized, "mode.kind==='dollar'?rounding(cashArs/exchange)");
        require(normalized, "mode.kind==='usdt'?rounding(transferArs/exchange)");
    }

    private void require(String script, String expected) {
        if (!script.contains(expected)) {
            throw unsupported("Cambio no reconocido en las formulas del catalogo: " + expected);
        }
    }

    private String normalizedScript(String script) {
        return (script == null ? "" : script).replaceAll("\\s+", "");
    }

    private void validateCatalogEntry(JsonNode entry, int index) {
        if (entry == null || !entry.isArray() || entry.size() != 4
                || !entry.get(0).isTextual() || entry.get(0).textValue().isBlank()
                || !entry.get(1).isTextual() || entry.get(1).textValue().isBlank()
                || !(entry.get(2).isNumber() || entry.get(2).isNull())
                || !entry.get(3).isTextual() || entry.get(3).textValue().isBlank()) {
            throw unsupported("Entrada invalida en catalogo.js, indice " + index);
        }
    }

    private boolean isPublicCash(JsonNode cash) {
        return cash != null && cash.isNumber() && cash.decimalValue().signum() > 0;
    }

    private LiberadosYaPrices prices(BigDecimal cashCents, String currency, BigDecimal exchange) {
        BigDecimal arsCents = "USD".equals(currency) ? cashCents.multiply(exchange) : cashCents;
        BigDecimal cashArs = amountFromCents(arsCents);
        BigDecimal transferArs = amountFromCents(arsCents
                .multiply(ONE_HUNDRED)
                .divide(TRANSFER_DENOMINATOR, 0, RoundingMode.CEILING));
        BigDecimal cashUsd = amountFromCents(arsCents.divide(exchange, 0, RoundingMode.HALF_UP));
        return new LiberadosYaPrices(
                new LiberadosYaMoney(null, transferArs, cashArs, null, null, null),
                new LiberadosYaMoney(null, null, cashUsd, null, null, null)
        );
    }

    private Map<String, LiberadosYaPrices> variantPrices(JsonNode colorPrices, String currency, BigDecimal exchange) {
        if (colorPrices == null || colorPrices.isMissingNode() || colorPrices.isNull()) {
            return Map.of();
        }
        if (!colorPrices.isObject()) {
            throw unsupported("colorPrices no es un objeto");
        }
        Map<String, LiberadosYaPrices> result = new LinkedHashMap<>();
        colorPrices.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (!entry.getKey().isBlank() && value.isNumber() && value.decimalValue().signum() > 0) {
                result.put(entry.getKey(), prices(value.decimalValue(), currency, exchange));
            }
        });
        return Map.copyOf(result);
    }

    private BigDecimal amountFromCents(BigDecimal cents) {
        return cents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> colors(JsonNode ficha, Map<String, LiberadosYaPrices> variantPrices) {
        LinkedHashSet<String> result = new LinkedHashSet<>(stringList(ficha.path("colors")));
        result.addAll(variantPrices.keySet());
        return List.copyOf(result);
    }

    private Map<String, Map<String, Object>> specifications(JsonNode attributes) {
        if (attributes == null || !attributes.isArray()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (JsonNode attribute : attributes) {
            if (attribute.isArray() && attribute.size() >= 2 && attribute.get(0).isTextual()) {
                Object value = jsonValue(attribute.get(1));
                if (value != null) {
                    values.put(attribute.get(0).textValue(), value);
                }
            }
        }
        return values.isEmpty() ? Map.of() : Map.of("Ficha tecnica", Map.copyOf(values));
    }

    private Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.textValue();
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.toString();
    }

    private List<String> images(URI baseUri, JsonNode ficha) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addImage(result, baseUri, ficha.path("image"));
        addImageValues(result, baseUri, ficha.path("colorImages"));
        addImageValues(result, baseUri, ficha.path("officialColorImages"));
        return List.copyOf(result);
    }

    private void addImageValues(LinkedHashSet<String> images, URI baseUri, JsonNode object) {
        if (object != null && object.isObject()) {
            object.elements().forEachRemaining(value -> addImage(images, baseUri, value));
        }
    }

    private void addImage(LinkedHashSet<String> images, URI baseUri, JsonNode value) {
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return;
        }
        try {
            URI image = baseUri.resolve(value.textValue().trim());
            if ("http".equalsIgnoreCase(image.getScheme()) || "https".equalsIgnoreCase(image.getScheme())) {
                images.add(image.toString());
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid optional images do not invalidate product pricing.
        }
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (value.isTextual() && !value.textValue().isBlank()) {
                result.add(value.textValue().trim());
            }
        }
        return List.copyOf(result);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.textValue().isBlank()) {
                return value.textValue().trim();
            }
        }
        return null;
    }

    private String brand(String category) {
        if (category != null && category.toLowerCase(Locale.ROOT).startsWith("iphone")) {
            return "iPhone";
        }
        return category;
    }

    private String slug(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("+", " plus ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private LiberadosYaIntegrationException unsupported(String message) {
        return new LiberadosYaIntegrationException(message);
    }

    public record ParsedCatalog(
            List<LiberadosYaProduct> products,
            int sourceProducts,
            int retiredProducts,
            int unpublishedProducts,
            BigDecimal exchange
    ) {
    }
}
