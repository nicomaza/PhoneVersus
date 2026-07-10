package celulares.cordobacelulares.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TiendaPorteTextUtils {

    private static final List<String> UNWANTED_PRODUCT_PATTERNS = List.of("aboll", "usad", "roto", "sinactivar", "activad");

    private TiendaPorteTextUtils() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutDiacritics.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
    }

    public static boolean containsUnwantedProductPattern(String value) {
        String normalized = normalize(value);
        return UNWANTED_PRODUCT_PATTERNS.stream().anyMatch(normalized::contains);
    }

    public static List<String> normalizedTokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String withoutDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String[] rawTokens = withoutDiacritics.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        List<String> tokens = new ArrayList<>();
        for (String rawToken : rawTokens) {
            String token = normalize(rawToken);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static Set<String> approximateCandidates(String brand, String model, String query) {
        Set<String> candidates = new LinkedHashSet<>();
        String normalizedBrand = normalize(brand);
        String normalizedModel = normalize(model);
        addCandidate(candidates, normalizedBrand);
        addCandidate(candidates, normalizedModel);

        int maxDistance = maxAllowedDistance(query);
        List<String> tokens = normalizedTokens((brand == null ? "" : brand) + " " + (model == null ? "" : model));
        for (String token : tokens) {
            if (token.length() >= Math.max(2, query.length() - maxDistance)) {
                addCandidate(candidates, token);
            }
        }

        for (int start = 0; start < tokens.size(); start++) {
            StringBuilder combined = new StringBuilder();
            for (int end = start; end < tokens.size(); end++) {
                combined.append(tokens.get(end));
                int length = combined.length();
                if (length >= query.length() - maxDistance && length <= query.length() + maxDistance) {
                    addCandidate(candidates, combined.toString());
                }
                if (length > query.length() + maxDistance) {
                    break;
                }
            }
        }

        addWindows(candidates, normalizedBrand, query.length(), maxDistance);
        addWindows(candidates, normalizedModel, query.length(), maxDistance);
        return candidates;
    }

    public static int maxAllowedDistance(String query) {
        if (query == null) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(query.length() * 0.2));
    }

    public static boolean isWithinLevenshteinDistance(String left, String right, int maxDistance) {
        if (left == null || right == null) {
            return false;
        }
        if (Math.abs(left.length() - right.length()) > maxDistance) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }

        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()] <= maxDistance;
    }

    private static void addWindows(Set<String> candidates, String value, int queryLength, int maxDistance) {
        if (value == null || value.isBlank()) {
            return;
        }
        int minLength = Math.max(1, queryLength - maxDistance);
        int maxLength = Math.min(value.length(), queryLength + maxDistance);
        for (int length = minLength; length <= maxLength; length++) {
            for (int start = 0; start + length <= value.length(); start++) {
                addCandidate(candidates, value.substring(start, start + length));
            }
        }
    }

    private static void addCandidate(Set<String> candidates, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            candidates.add(candidate);
        }
    }
}
