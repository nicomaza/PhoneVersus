package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.utils.TiendaPorteTextUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CatalogVariantIdentity {

    private static final Pattern MEMORY_PATTERN = Pattern.compile("\\b(\\d{1,4})\\s*(gb|tb)\\b");
    private static final Pattern RAM_AFTER_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*gb\\s*ram\\b");
    private static final Pattern RAM_BEFORE_PATTERN = Pattern.compile("\\bram\\s*(\\d{1,2})\\s*(?:gb)?\\b");
    private static final Pattern FIVE_G_PATTERN = Pattern.compile("\\b5\\s*g\\b");
    private static final Pattern FOUR_G_PATTERN = Pattern.compile("\\b4\\s*g\\b|\\blte\\b");
    private static final Set<String> MODEL_NOISE = Set.of(
            "gb", "tb", "ram", "rom", "memoria", "almacenamiento", "interno", "interna",
            "celular", "smartphone", "liberado", "liberada"
    );

    private CatalogVariantIdentity() {
    }

    static VariantKey from(String brand, String model) {
        String brandKey = canonicalBrandKey(brand);
        String normalizedModel = normalizeWords(model);
        if (brandKey.isBlank() || normalizedModel.isBlank()) {
            return null;
        }

        Memory memory = parseMemory(normalizedModel);
        String network = parseNetwork(normalizedModel);
        String structuralModel = MEMORY_PATTERN.matcher(normalizedModel).replaceAll(" ");
        structuralModel = FIVE_G_PATTERN.matcher(structuralModel).replaceAll(" ");
        structuralModel = FOUR_G_PATTERN.matcher(structuralModel).replaceAll(" ");

        Set<String> brandTokens = brandTokens(brandKey, brand);
        List<String> modelTokens = new ArrayList<>();
        for (String token : structuralModel.split("\\s+")) {
            if (token.isBlank() || MODEL_NOISE.contains(token) || brandTokens.contains(token)) {
                continue;
            }
            modelTokens.add(token);
        }
        modelTokens.sort(Comparator.naturalOrder());
        if (modelTokens.isEmpty()) {
            return null;
        }

        return new VariantKey(
                brandKey,
                String.join("|", modelTokens),
                network,
                memory.ramGb(),
                memory.storageGb()
        );
    }

    private static String canonicalBrandKey(String brand) {
        String key = TiendaPorteTextUtils.normalize(brand);
        return switch (key) {
            case "moto", "motorola" -> "motorola";
            case "xiaomi", "redmi", "poco" -> "xiaomi";
            case "apple", "productosapple", "productoapple" -> "productosapple";
            default -> key;
        };
    }

    private static Set<String> brandTokens(String brandKey, String rawBrand) {
        Set<String> tokens = new LinkedHashSet<>(TiendaPorteTextUtils.normalizedTokens(rawBrand));
        switch (brandKey) {
            case "motorola" -> {
                tokens.add("moto");
                tokens.add("motorola");
            }
            case "xiaomi" -> {
                tokens.add("xiaomi");
                tokens.add("redmi");
                tokens.add("poco");
            }
            case "productosapple" -> {
                tokens.add("apple");
                tokens.add("producto");
                tokens.add("productos");
            }
            default -> tokens.add(brandKey);
        }
        return tokens;
    }

    private static Memory parseMemory(String normalizedModel) {
        Long explicitRam = firstRam(RAM_AFTER_PATTERN.matcher(normalizedModel));
        if (explicitRam == null) {
            explicitRam = firstRam(RAM_BEFORE_PATTERN.matcher(normalizedModel));
        }

        List<Long> capacities = new ArrayList<>();
        Matcher matcher = MEMORY_PATTERN.matcher(normalizedModel);
        while (matcher.find()) {
            long value = Long.parseLong(matcher.group(1));
            if ("tb".equals(matcher.group(2))) {
                value *= 1024;
            }
            capacities.add(value);
        }

        if (capacities.isEmpty()) {
            return new Memory(explicitRam, null);
        }

        Long storage;
        Long ram = explicitRam;
        if (capacities.size() == 1) {
            Long only = capacities.get(0);
            storage = only.equals(explicitRam) ? null : only;
        } else {
            Long detectedRam = explicitRam;
            storage = capacities.stream()
                    .filter(value -> !value.equals(detectedRam))
                    .max(Long::compareTo)
                    .orElse(null);
            if (ram == null) {
                ram = capacities.stream()
                        .filter(value -> !value.equals(storage))
                        .min(Long::compareTo)
                        .orElse(null);
            }
        }
        return new Memory(ram, storage);
    }

    private static Long firstRam(Matcher matcher) {
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private static String parseNetwork(String normalizedModel) {
        if (FIVE_G_PATTERN.matcher(normalizedModel).find()) {
            return "5g";
        }
        if (FOUR_G_PATTERN.matcher(normalizedModel).find()) {
            return "4g";
        }
        return "";
    }

    private static String normalizeWords(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    record VariantKey(String brand, String model, String network, Long ramGb, Long storageGb) {
    }

    private record Memory(Long ramGb, Long storageGb) {
    }
}
