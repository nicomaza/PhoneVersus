package celulares.cordobacelulares.integration.liberadosya.matching;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LiberadosYaTextNormalizer {

    public String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace("+", " PLUS ")
                .replaceAll("(?i)(\\d+(?:[.,]\\d+)?)\\s*(TB|GB|RAM)\\b", "$1 $2")
                .replaceAll("(?i)\\bRAM\\s*(\\d+)\\s*GB\\b", "RAM $1 GB")
                .replaceAll("(?i)\\b(\\d+)\\s*GB\\s*RAM\\b", "$1 GB RAM")
                .replaceAll("[\\\\/()\\[\\]{}_,.;:|]+", " ")
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public List<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        String[] parts = normalized.split(" ");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(normalizeRoman(part));
            }
        }
        return List.copyOf(tokens);
    }

    public String key(String value) {
        return normalize(value).replace(" ", "");
    }

    private String normalizeRoman(String token) {
        return switch (token) {
            case "I" -> "1";
            case "II" -> "2";
            case "III" -> "3";
            case "IV" -> "4";
            case "V" -> "5";
            case "VI" -> "6";
            case "VII" -> "7";
            case "VIII" -> "8";
            case "IX" -> "9";
            case "X" -> "10";
            case "XI" -> "11";
            case "XII" -> "12";
            default -> token;
        };
    }
}
