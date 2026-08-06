package celulares.cordobacelulares.integration.liberadosya.scraping;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaMoney;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LiberadosYaPriceParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaPriceParser.class);

    private static final Pattern MONEY_PATTERN = Pattern.compile("\\$\\s*([0-9][0-9.,]*)(?:\\s*USD)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASH_DISCOUNT_PATTERN = Pattern.compile("(\\d{1,2}(?:[,.]\\d{1,2})?)\\s*%\\s+de\\s+descuento\\s+pagando\\s+con\\s+Efectivo", Pattern.CASE_INSENSITIVE);

    public LiberadosYaMoney parse(String text, CurrencyMode mode) {
        return parse(text, mode, null, "precios", ignored -> {
        });
    }

    public LiberadosYaMoney parse(
            String text,
            CurrencyMode mode,
            String productUrl,
            String field,
            Consumer<String> inconsistencyConsumer
    ) {
        if (text == null || text.isBlank()) {
            return new LiberadosYaMoney();
        }
        Consumer<String> inconsistencies = inconsistencyConsumer == null ? ignored -> {
        } : inconsistencyConsumer;
        String mainText = limitToMainProductText(text);
        int cashIndex = indexOfIgnoreCase(mainText, "con Efectivo");
        List<BigDecimal> allAmounts = amounts(mainText, mode, productUrl, field, inconsistencies);
        List<BigDecimal> beforeCash = cashIndex < 0 ? allAmounts : amounts(mainText.substring(0, cashIndex), mode, productUrl, field, inconsistencies);
        BigDecimal efectivo = cashIndex < 0 ? null : lastPositive(beforeCash);
        List<BigDecimal> regularAmounts = efectivo == null ? beforeCash : beforeCash.subList(0, Math.max(0, beforeCash.size() - 1));

        BigDecimal lista = null;
        BigDecimal actual = null;
        List<BigDecimal> positiveRegular = regularAmounts.stream()
                .filter(this::positive)
                .toList();
        if (positiveRegular.size() >= 2 && positiveRegular.get(0).compareTo(positiveRegular.get(1)) > 0) {
            lista = positiveRegular.get(0);
            actual = positiveRegular.get(1);
        } else if (!positiveRegular.isEmpty()) {
            actual = positiveRegular.get(positiveRegular.size() - 1);
        } else if (efectivo != null) {
            actual = efectivo;
        }

        BigDecimal descuento = percentageDifference(lista, actual);
        BigDecimal cashDiscount = cashDiscount(mainText, productUrl, field, inconsistencies);
        if (cashDiscount == null) {
            cashDiscount = percentageDifference(actual, efectivo);
        }
        BigDecimal cashDifference = difference(actual, efectivo);

        return new LiberadosYaMoney(
                zeroToNull(lista),
                zeroToNull(actual),
                zeroToNull(efectivo),
                descuento,
                cashDiscount,
                cashDifference
        );
    }

    private List<BigDecimal> amounts(
            String text,
            CurrencyMode mode,
            String productUrl,
            String field,
            Consumer<String> inconsistencyConsumer
    ) {
        List<BigDecimal> values = new ArrayList<>();
        Matcher matcher = MONEY_PATTERN.matcher(text);
        while (matcher.find()) {
            BigDecimal value = parseAmount(matcher.group(1), mode, productUrl, field, inconsistencyConsumer);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private BigDecimal parseAmount(
            String raw,
            CurrencyMode mode,
            String productUrl,
            String field,
            Consumer<String> inconsistencyConsumer
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        if (mode == CurrencyMode.ARS) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else {
            normalized = normalized.replace(",", "");
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            logNumericParseFailure(productUrl, field, raw, "BigDecimal");
            inconsistencyConsumer.accept(field + "-monto-invalido");
            return null;
        }
    }

    private BigDecimal cashDiscount(
            String text,
            String productUrl,
            String field,
            Consumer<String> inconsistencyConsumer
    ) {
        Matcher matcher = CASH_DISCOUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String raw = matcher.group(1);
        try {
            return new BigDecimal(raw.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            logNumericParseFailure(productUrl, field + ".descuento_efectivo", raw, "BigDecimal");
            inconsistencyConsumer.accept(field + "-descuento-efectivo-invalido");
            return null;
        }
    }

    private BigDecimal percentageDifference(BigDecimal base, BigDecimal discounted) {
        if (base == null || discounted == null || !positive(base) || discounted.compareTo(base) >= 0) {
            return null;
        }
        return base.subtract(discounted)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal difference(BigDecimal base, BigDecimal discounted) {
        if (base == null || discounted == null || base.compareTo(discounted) < 0) {
            return null;
        }
        BigDecimal difference = base.subtract(discounted).setScale(2, RoundingMode.HALF_UP);
        return difference.compareTo(BigDecimal.ZERO) == 0 ? null : difference;
    }

    private BigDecimal lastPositive(List<BigDecimal> amounts) {
        for (int index = amounts.size() - 1; index >= 0; index--) {
            BigDecimal value = zeroToNull(amounts.get(index));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal zeroToNull(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0 ? null : value;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String limitToMainProductText(String text) {
        String limited = text;
        for (String marker : List.of("Productos similares", "Medios de pago")) {
            int index = indexOfIgnoreCase(limited, marker);
            if (index >= 0) {
                limited = limited.substring(0, index);
            }
        }
        return limited;
    }

    private int indexOfIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }

    private void logNumericParseFailure(String productUrl, String field, String value, String expectedType) {
        LOGGER.warn(
                "LiberadosYa numeric parse failed. cid={} url={} field={} value={} expectedType={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                productUrl,
                field,
                safeLogValue(value),
                expectedType
        );
    }

    private String safeLogValue(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }

    public enum CurrencyMode {
        ARS,
        USD
    }
}
