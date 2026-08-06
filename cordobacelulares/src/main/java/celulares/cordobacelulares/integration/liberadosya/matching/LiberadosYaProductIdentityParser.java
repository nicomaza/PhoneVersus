package celulares.cordobacelulares.integration.liberadosya.matching;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LiberadosYaProductIdentityParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaProductIdentityParser.class);

    private static final int MAX_MEMORY_TOKEN_DIGITS = 4;
    private static final Set<String> RAM_VALUES = Set.of("2", "3", "4", "6", "8", "12", "16", "18", "24", "32");
    private static final Set<String> STORAGE_VALUES = Set.of("16", "32", "64", "128", "256", "512", "1024", "2048");
    private static final Set<String> NOISE_TOKENS = Set.of(
            "GB", "TB", "RAM", "ROM", "ALMACENAMIENTO", "INTERNO", "MEMORIA",
            "CELULAR", "SMARTPHONE", "LIBERADO", "LIBERADOSYA", "DUAL", "SIM"
    );
    private static final List<List<String>> VARIANT_SEQUENCES = List.of(
            List.of("PRO", "MAX"),
            List.of("PRO", "PLUS"),
            List.of("T", "PRO"),
            List.of("PRO"),
            List.of("PLUS"),
            List.of("ULTRA"),
            List.of("LITE"),
            List.of("FE"),
            List.of("SE"),
            List.of("MAX"),
            List.of("T")
    );
    private static final Pattern RAM_PATTERN = Pattern.compile("\\b(?:RAM\\s*)?(\\d{1,2})\\s*(?:GB\\s*)?RAM\\b|\\bRAM\\s*(\\d{1,2})(?:\\s*GB)?\\b");
    private static final Pattern GB_PATTERN = Pattern.compile("\\b(\\d{1,4})\\s*GB\\b");
    private static final Pattern STORAGE_TB_PATTERN = Pattern.compile("\\b(0[.,]25|0[.,]5|1|2)\\s*TB\\b");
    private static final Pattern LONG_NUMERIC_TOKEN = Pattern.compile("\\b\\d{5,}\\b");

    private final LiberadosYaTextNormalizer normalizer;

    public LiberadosYaProductIdentityParser(LiberadosYaTextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public ProductIdentity parseQuery(String brand, String model) {
        String safeBrand = clean(brand);
        String safeModel = clean(model);
        String original = (safeBrand + " " + safeModel).trim();
        ProductIdentity identity = parseText(original, safeBrand);
        String queryBrand = queryBrand(safeBrand, safeModel, identity.brand());
        if (queryBrand != null && !Objects.equals(queryBrand, identity.brand())) {
            identity = withBrand(identity, queryBrand);
        }
        return identity;
    }

    public ProductIdentity parseProduct(LiberadosYaProduct product) {
        String specsText = specsText(product);
        String displaySource = String.join(" ",
                clean(product == null ? null : product.getNombre()),
                clean(product == null ? null : product.getVersionComercial()),
                clean(product == null ? null : product.getModelo()),
                clean(product == null ? null : product.getMarca())
        );
        String preferredBrand = product == null ? null : product.getMarca();
        ProductIdentity displayIdentity = parseText(displaySource, preferredBrand);
        ProductIdentity slugIdentity = parseText(identitySlug(product == null ? null : product.getSlug()), null);
        ProductIdentity specsIdentity = parseText(specsText, null);
        return mergeProductIdentity(displayIdentity, slugIdentity, specsIdentity);
    }

    public ProductIdentity parseText(String value, String preferredBrand) {
        String normalized = normalizer.normalize(value);
        List<String> tokens = normalizer.tokens(value);
        String brand = canonicalBrand(tokens);
        if (brand == null && !isBlank(preferredBrand)) {
            brand = canonicalBrand(normalizer.tokens(preferredBrand));
        }

        Memory memory = parseMemory(normalized);
        String network = parseNetwork(tokens);
        List<String> variants = parseVariants(tokens);
        Set<Integer> consumed = consumedIndexes(tokens, brand, memory, network, variants);
        List<String> modelTokens = new ArrayList<>();
        Set<String> remaining = new LinkedHashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            if (consumed.contains(index)) {
                continue;
            }
            String token = tokens.get(index);
            if (isNoise(token)) {
                continue;
            }
            remaining.add(token);
            if (isModelToken(token)) {
                modelTokens.add(token);
            }
        }

        return new ProductIdentity(
                clean(value),
                normalized,
                brand,
                brand == null ? Set.of() : compatibleBrandKeys(brand),
                List.copyOf(modelTokens),
                List.copyOf(variants),
                network,
                memory.ramGb(),
                memory.storageGb(),
                Set.copyOf(remaining)
        );
    }

    public Set<String> compatibleBrandKeys(String brand) {
        if (brand == null || brand.isBlank()) {
            return Set.of();
        }
        return switch (brand.toUpperCase(Locale.ROOT)) {
            case "REDMI" -> Set.of("REDMI", "XIAOMI_REDMI");
            case "POCO" -> Set.of("POCO", "XIAOMI_POCO");
            case "IPHONE" -> Set.of("IPHONE", "APPLE_IPHONE", "APPLE");
            case "XIAOMI" -> Set.of("XIAOMI");
            default -> Set.of(brand.toUpperCase(Locale.ROOT));
        };
    }

    public boolean brandCompatible(ProductIdentity query, ProductIdentity candidate) {
        if (query == null || candidate == null || query.brand() == null || candidate.brand() == null) {
            return false;
        }
        Set<String> intersection = new HashSet<>(query.brandKeys());
        intersection.retainAll(candidate.brandKeys());
        return !intersection.isEmpty() || Objects.equals(query.brand(), candidate.brand());
    }

    private ProductIdentity mergeProductIdentity(ProductIdentity displayIdentity, ProductIdentity slugIdentity, ProductIdentity specsIdentity) {
        boolean useSlugIdentity = preferSlugIdentity(displayIdentity, slugIdentity);
        ProductIdentity structural = useSlugIdentity ? slugIdentity : displayIdentity;
        Long ram = useSlugIdentity
                ? firstNonNull(slugIdentity.ramGb(), specsIdentity.ramGb())
                : firstNonNull(displayIdentity.ramGb(), slugIdentity.ramGb(), specsIdentity.ramGb());
        Long storage = useSlugIdentity
                ? firstNonNull(slugIdentity.storageGb(), specsIdentity.storageGb())
                : firstNonNull(displayIdentity.storageGb(), slugIdentity.storageGb(), specsIdentity.storageGb());
        String network = useSlugIdentity
                ? slugIdentity.networkGeneration()
                : firstNonBlank(displayIdentity.networkGeneration(), slugIdentity.networkGeneration());
        List<String> variants = structural.variantTokens();
        if (variants.isEmpty() && !useSlugIdentity) {
            variants = displayIdentity.variantTokens().isEmpty() ? slugIdentity.variantTokens() : displayIdentity.variantTokens();
        }
        return new ProductIdentity(
                displayIdentity.originalName(),
                structural.normalizedName(),
                structural.brand(),
                structural.brandKeys(),
                structural.modelTokens(),
                variants,
                network,
                ram,
                storage,
                structural.remainingTokens()
        );
    }

    private boolean preferSlugIdentity(ProductIdentity displayIdentity, ProductIdentity slugIdentity) {
        if (slugIdentity == null || slugIdentity.brand() == null || slugIdentity.modelTokens().isEmpty()) {
            return false;
        }
        if (displayIdentity == null || displayIdentity.brand() == null || displayIdentity.modelTokens().isEmpty()) {
            return true;
        }
        if (!brandAliasesOverlap(displayIdentity.brand(), slugIdentity.brand())) {
            return true;
        }
        return !displayIdentity.modelTokens().containsAll(slugIdentity.modelTokens());
    }

    private boolean brandAliasesOverlap(String firstBrand, String secondBrand) {
        if (firstBrand == null || secondBrand == null) {
            return false;
        }
        Set<String> first = new HashSet<>(compatibleBrandKeys(firstBrand));
        first.retainAll(compatibleBrandKeys(secondBrand));
        return !first.isEmpty() || Objects.equals(firstBrand, secondBrand);
    }

    private ProductIdentity withBrand(ProductIdentity identity, String brand) {
        return new ProductIdentity(
                identity.originalName(),
                identity.normalizedName(),
                brand,
                compatibleBrandKeys(brand),
                identity.modelTokens(),
                identity.variantTokens(),
                identity.networkGeneration(),
                identity.ramGb(),
                identity.storageGb(),
                identity.remainingTokens()
        );
    }

    private String queryBrand(String brand, String model, String combinedBrand) {
        String brandOnly = canonicalBrand(normalizer.tokens(brand));
        String modelBrand = canonicalBrand(normalizer.tokens(model));
        if ("XIAOMI".equals(brandOnly) && ("REDMI".equals(modelBrand) || "POCO".equals(modelBrand))) {
            return modelBrand;
        }
        if ("APPLE".equals(brandOnly) && "IPHONE".equals(modelBrand)) {
            return modelBrand;
        }
        if (brandOnly != null) {
            return brandOnly;
        }
        return modelBrand == null ? combinedBrand : modelBrand;
    }

    private String identitySlug(String slug) {
        String safeSlug = clean(slug).toLowerCase(Locale.ROOT);
        if (safeSlug.matches(".*(?:gb|ram|tb|5g|4g)-[a-z0-9]{5}$")) {
            safeSlug = safeSlug.replaceFirst("-[a-z0-9]{5}$", "");
        }
        return safeSlug.replace('-', ' ');
    }

    private Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String canonicalBrand(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        if (tokens.contains("IPHONE")) {
            return "IPHONE";
        }
        if (tokens.contains("REDMI")) {
            return "REDMI";
        }
        if (tokens.contains("POCO")) {
            return "POCO";
        }
        if (tokens.contains("XIAOMI")) {
            return "XIAOMI";
        }
        if (tokens.contains("APPLE")) {
            return "IPHONE";
        }
        if (tokens.contains("SAMSUNG")) {
            return "SAMSUNG";
        }
        if (tokens.contains("MOTOROLA") || tokens.contains("MOTO")) {
            return "MOTOROLA";
        }
        for (String direct : List.of("REALME", "INFINIX", "HUAWEI", "HONOR", "OPPO", "NOKIA", "GOOGLE")) {
            if (tokens.contains(direct)) {
                return direct;
            }
        }
        return null;
    }

    private Memory parseMemory(String normalized) {
        Long ram = null;
        Long storage = null;
        Set<String> ramValues = new LinkedHashSet<>();
        Matcher ramMatcher = RAM_PATTERN.matcher(normalized);
        while (ramMatcher.find()) {
            String value = firstNonBlank(ramMatcher.group(1), ramMatcher.group(2));
            if (value != null && RAM_VALUES.contains(value)) {
                ramValues.add(value);
            }
        }
        if (!ramValues.isEmpty()) {
            ram = Long.valueOf(ramValues.iterator().next());
        }

        List<Long> gbValues = new ArrayList<>();
        Matcher gbMatcher = GB_PATTERN.matcher(normalized);
        while (gbMatcher.find()) {
            Long value = Long.valueOf(gbMatcher.group(1));
            if (ram != null && value.equals(ram)) {
                continue;
            }
            gbValues.add(value);
        }
        Matcher storageTbMatcher = STORAGE_TB_PATTERN.matcher(normalized);
        while (storageTbMatcher.find()) {
            long gb = tbToGb(storageTbMatcher.group(1));
            if (gb > 0) {
                gbValues.add(gb);
            }
        }

        for (Long value : gbValues) {
            if (value >= 32) {
                storage = value;
                break;
            }
        }
        if (ram == null) {
            List<Long> smallGb = gbValues.stream()
                    .filter(value -> RAM_VALUES.contains(String.valueOf(value)))
                    .toList();
            if (!smallGb.isEmpty()) {
                ram = smallGb.get(0);
            }
        }
        if (storage == null) {
            for (Long value : gbValues) {
                if (!value.equals(ram) && STORAGE_VALUES.contains(String.valueOf(value))) {
                    storage = value;
                    break;
                }
            }
        }
        return new Memory(ram, storage);
    }

    private long tbToGb(String raw) {
        String normalized = raw.replace(',', '.');
        BigDecimal tb = new BigDecimal(normalized);
        return tb.multiply(BigDecimal.valueOf(1024)).longValue();
    }

    private String parseNetwork(List<String> tokens) {
        if (tokens.contains("5G")) {
            return "5G";
        }
        if (tokens.contains("4G") || tokens.contains("LTE")) {
            return "4G";
        }
        return null;
    }

    private List<String> parseVariants(List<String> tokens) {
        List<String> variants = new ArrayList<>();
        boolean[] used = new boolean[tokens.size()];
        for (List<String> sequence : VARIANT_SEQUENCES) {
            for (int index = 0; index <= tokens.size() - sequence.size(); index++) {
                if (matchesSequence(tokens, used, sequence, index)) {
                    variants.add(String.join(" ", sequence));
                    for (int offset = 0; offset < sequence.size(); offset++) {
                        used[index + offset] = true;
                    }
                }
            }
        }
        return normalizeVariants(variants);
    }

    private List<String> normalizeVariants(List<String> variants) {
        if (variants.contains("PRO MAX")) {
            return List.of("PRO MAX");
        }
        if (variants.contains("PRO PLUS")) {
            return List.of("PRO PLUS");
        }
        if (variants.contains("T PRO")) {
            return List.of("T PRO");
        }
        return variants.stream().distinct().toList();
    }

    private boolean matchesSequence(List<String> tokens, boolean[] used, List<String> sequence, int index) {
        for (int offset = 0; offset < sequence.size(); offset++) {
            if (used[index + offset] || !sequence.get(offset).equals(tokens.get(index + offset))) {
                return false;
            }
        }
        return true;
    }

    private Set<Integer> consumedIndexes(List<String> tokens, String brand, Memory memory, String network, List<String> variants) {
        Set<Integer> consumed = new HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (isBrandToken(token, brand) || "XIAOMI".equals(token) || "APPLE".equals(token)) {
                consumed.add(index);
            }
            if (Objects.equals(token, network) || ("4G".equals(network) && "LTE".equals(token))) {
                consumed.add(index);
            }
            if (isVariantToken(token, variants)) {
                consumed.add(index);
            }
            if ("GB".equals(token) || "TB".equals(token) || "RAM".equals(token)) {
                consumed.add(index);
            }
            if (isTerabyteValue(tokens, index)) {
                consumed.add(index);
            }
            if (isMemoryValue(token, memory)) {
                consumed.add(index);
            }
        }
        return consumed;
    }

    private boolean isBrandToken(String token, String brand) {
        if (brand == null) {
            return false;
        }
        return switch (brand) {
            case "REDMI" -> "REDMI".equals(token);
            case "POCO" -> "POCO".equals(token);
            case "IPHONE" -> "IPHONE".equals(token);
            case "MOTOROLA" -> "MOTOROLA".equals(token) || "MOTO".equals(token);
            default -> brand.equals(token);
        };
    }

    private boolean isVariantToken(String token, List<String> variants) {
        return variants.stream()
                .flatMap(variant -> Arrays.stream(variant.split(" ")))
                .anyMatch(token::equals);
    }

    private boolean isMemoryValue(String token, Memory memory) {
        if (!token.matches("\\d+")) {
            return false;
        }
        if (token.length() > MAX_MEMORY_TOKEN_DIGITS) {
            return false;
        }
        return Objects.equals(stringValue(memory.ramGb()), token) || Objects.equals(stringValue(memory.storageGb()), token);
    }

    private boolean isTerabyteValue(List<String> tokens, int index) {
        String token = tokens.get(index).replace(',', '.');
        if (!token.matches("\\d+(?:\\.\\d+)?")) {
            return false;
        }
        boolean nextIsTb = index + 1 < tokens.size() && "TB".equals(tokens.get(index + 1));
        boolean previousIsTb = index > 0 && "TB".equals(tokens.get(index - 1));
        return nextIsTb || previousIsTb;
    }

    private boolean isModelToken(String token) {
        return !isNoise(token)
                && !"4G".equals(token)
                && !"5G".equals(token)
                && !"LTE".equals(token)
                && !token.matches("\\d{3,}");
    }

    private boolean isNoise(String token) {
        return token == null || token.isBlank() || NOISE_TOKENS.contains(token);
    }

    private String specsText(LiberadosYaProduct product) {
        Map<String, Map<String, Object>> specs = product == null ? null : product.getEspecificaciones();
        if (specs == null || specs.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        specs.forEach((section, values) -> {
            if (values != null) {
                values.forEach((key, value) -> appendIdentitySpec(product, text, section, key, value));
            }
        });
        return text.toString();
    }

    private void appendIdentitySpec(LiberadosYaProduct product, StringBuilder text, String section, String key, Object value) {
        String rawValue = specValue(value);
        if (isBlank(rawValue)) {
            return;
        }
        if (isIdentityRelevantSpec(key, rawValue)) {
            text.append(' ')
                    .append(section)
                    .append(' ')
                    .append(key)
                    .append(' ')
                    .append(rawValue);
            return;
        }
        if (isIdentifierLikeSpec(key) && containsLongNumericToken(rawValue)) {
            logNumericFieldIgnored(product, "especificaciones." + clean(key), rawValue, "memoria/almacenamiento en GB");
            addInconsistency(product, "identity-spec-identifier-ignored:" + clean(key));
        }
    }

    private boolean isIdentityRelevantSpec(String key, String value) {
        String normalizedKey = normalizeForSpec(key);
        String normalizedValue = normalizeForSpec(value);
        return containsAny(normalizedKey, "RAM", "ROM", "MEMORIA", "ALMACENAMIENTO", "CAPACIDAD", "ESPACIO", "DISCO")
                || normalizedValue.matches(".*\\b\\d{1,4}\\s*(GB|TB)\\b.*");
    }

    private boolean isIdentifierLikeSpec(String key) {
        String normalizedKey = normalizeForSpec(key);
        return containsAny(normalizedKey, "MODELO", "ALFANUMERICO", "CODIGO", "SKU", "EAN", "GTIN", "MPN", "IDENTIFICADOR");
    }

    private boolean containsLongNumericToken(String value) {
        return !isBlank(value) && LONG_NUMERIC_TOKEN.matcher(value).find();
    }

    private void logNumericFieldIgnored(LiberadosYaProduct product, String field, String value, String expectedType) {
        LOGGER.warn(
                "LiberadosYa numeric field ignored for identity parsing. cid={} url={} field={} value={} expectedType={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                product == null ? null : product.getUrl(),
                field,
                safeLogValue(value),
                expectedType
        );
    }

    private void addInconsistency(LiberadosYaProduct product, String inconsistency) {
        if (product == null || isBlank(inconsistency)) {
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

    private String specValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right);
        }
        return String.valueOf(value);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForSpec(String value) {
        return java.text.Normalizer.normalize(value == null ? "" : value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT);
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeLogValue(String value) {
        String clean = clean(value).replaceAll("[\\r\\n\\t]+", " ");
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Memory(Long ramGb, Long storageGb) {
    }
}
