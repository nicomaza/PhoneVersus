package celulares.cordobacelulares.integration.liberadosya.service;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaQuotationResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaQuotationResponse.MarkupByTransferScheme;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaQuotationResponse.UpdateStatus;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LiberadosYaQuotationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaQuotationService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int MAX_HTML_LENGTH = 2_000_000;
    private static final int MAX_SCRIPT_LENGTH = 2_000_000;
    private static final String USER_AGENT = "CordobaCelulares-LiberadosYaQuotations/1.0";
    private static final String NUMBER = "(?:\\d+(?:\\.\\d*)?|\\.\\d+)";
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Pattern OPTIONS_PATTERN = Pattern.compile(
            "const\\s+options\\s*=\\s*\\[(.*?)]\\s*;",
            Pattern.DOTALL
    );
    private static final Pattern OPTION_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern OPTION_NAME_PATTERN = Pattern.compile("name\\s*:\\s*(['\"])(.*?)\\1");
    private static final Pattern OPTION_KIND_PATTERN = Pattern.compile("kind\\s*:\\s*(['\"])(.*?)\\1");
    private static final Pattern OPTION_COUNT_PATTERN = Pattern.compile("count\\s*:\\s*(\\d+)");
    private static final Pattern OPTION_NET_PATTERN = Pattern.compile("net\\s*:\\s*([^,}]+)");
    private static final Pattern QUOTE_FUNCTION_PATTERN = Pattern.compile("function\\s+quote\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern SPECIAL_TRANSFER_PATTERN = Pattern.compile(
            "consttransferArs=\\(p\\.net7Pricing\\|\\|p\\.net10Pricing\\)\\?Math\\.ceil\\(cashArs\\*(" + NUMBER
                    + ")/(" + NUMBER + ")\\):Math\\.round\\(cashArs\\*transferMultiplier\\);"
    );

    private final LiberadosYaProperties properties;
    private final HttpClient httpClient;
    private final Object refreshLock = new Object();

    private volatile Snapshot lastValid;
    private volatile Instant lastAttemptAt;

    public LiberadosYaQuotationService(LiberadosYaProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public LiberadosYaQuotationResponse getQuotations() {
        Instant now = Instant.now();
        Snapshot current = lastValid;
        if (isFresh(current, now)) {
            return response(current, UpdateStatus.CURRENT, null);
        }

        synchronized (refreshLock) {
            now = Instant.now();
            current = lastValid;
            if (isFresh(current, now)) {
                return response(current, UpdateStatus.CURRENT, null);
            }

            lastAttemptAt = now;
            try {
                Snapshot refreshed = fetchAndParse(now);
                lastValid = refreshed;
                return response(refreshed, UpdateStatus.CURRENT, null);
            } catch (RuntimeException ex) {
                String error = safeError(ex);
                LOGGER.warn(
                        "No se pudieron actualizar las cotizaciones de LiberadosYa. cid={} lastValidAt={} cause={}",
                        TiendaPorteDiagnostics.currentCorrelationId(),
                        current == null ? null : current.obtainedAt(),
                        error
                );
                return current == null
                        ? response(null, UpdateStatus.UNAVAILABLE, error)
                        : response(current, UpdateStatus.STALE, error);
            }
        }
    }

    private Snapshot fetchAndParse(Instant obtainedAt) {
        URI baseUri = URI.create(properties.normalizedBaseUrl() + "/");
        String html = fetch(baseUri, "text/html,*/*", MAX_HTML_LENGTH);
        URI appJsUri = locateAppJs(baseUri, html);
        String script = fetch(appJsUri, "application/javascript,text/javascript,*/*", MAX_SCRIPT_LENGTH);
        ParsedQuotation parsed = parseScript(script);
        return new Snapshot(obtainedAt, appJsUri.toString(), parsed);
    }

    private URI locateAppJs(URI baseUri, String html) {
        Document document = Jsoup.parse(html, baseUri.toString());
        URI selected = null;
        for (Element script : document.select("script[src]")) {
            URI candidate;
            try {
                candidate = baseUri.resolve(script.attr("src"));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String path = candidate.getPath() == null ? "" : candidate.getPath();
            if (!(path.equals("/app.js") || path.endsWith("/app.js"))) {
                continue;
            }
            if (!sameOrigin(baseUri, candidate)) {
                throw new LiberadosYaIntegrationException("El app.js de LiberadosYa apunta a un origen no permitido");
            }
            if (selected != null && !selected.equals(candidate)) {
                throw new LiberadosYaIntegrationException("Se encontraron multiples versiones de app.js en LiberadosYa");
            }
            selected = candidate;
        }
        if (selected == null) {
            throw new LiberadosYaIntegrationException("No se encontro la version vigente de app.js en LiberadosYa");
        }
        return selected;
    }

    private boolean sameOrigin(URI baseUri, URI candidate) {
        return baseUri.getScheme().equalsIgnoreCase(candidate.getScheme())
                && baseUri.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(baseUri) == effectivePort(candidate);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private ParsedQuotation parseScript(String script) {
        BigDecimal exchange = numericConstant(script, "exchange");
        BigDecimal transferMultiplier = numericConstant(script, "transferMultiplier");
        BigDecimal settlement = settlement(script);
        validatePositive("exchange", exchange);
        validateMultiplier("transferMultiplier", transferMultiplier);
        validateNet("settlement", settlement);

        Map<String, PaymentOption> options = paymentOptions(script, settlement);
        requireKind(options, "transferencia en pesos", "transfer", 1);
        requireKind(options, "usdt criptomoneda", "usdt", 1);
        BigDecimal tarjeta1Net = requireNet(options, "tarjeta 1 pago", 1);
        BigDecimal qr1Net = requireNet(options, "qr 1 pago", 1);
        BigDecimal planZ3Net = requireNet(options, "plan z 3 pagos", 3);
        BigDecimal tarjeta3Net = requireNet(options, "tarjeta 3 cuotas", 3);
        BigDecimal tarjeta6Net = requireNet(options, "tarjeta 6 cuotas", 6);

        BigDecimal specialTransferMultiplier = validateQuoteFormula(script);
        return new ParsedQuotation(
                exchange,
                markupByScheme(transferMultiplier, specialTransferMultiplier, BigDecimal.ONE),
                markupByScheme(transferMultiplier, specialTransferMultiplier, tarjeta1Net),
                markupByScheme(transferMultiplier, specialTransferMultiplier, qr1Net),
                markupByScheme(transferMultiplier, specialTransferMultiplier, planZ3Net),
                markupByScheme(transferMultiplier, specialTransferMultiplier, tarjeta3Net),
                markupByScheme(transferMultiplier, specialTransferMultiplier, tarjeta6Net)
        );
    }

    private BigDecimal numericConstant(String script, String name) {
        Pattern pattern = Pattern.compile(
                "const\\s+" + Pattern.quote(name) + "\\s*=\\s*(" + NUMBER + ")\\s*;"
        );
        Matcher matcher = pattern.matcher(script);
        if (!matcher.find()) {
            throw unsupported("No se pudo interpretar la constante " + name);
        }
        return new BigDecimal(matcher.group(1));
    }

    private BigDecimal settlement(String script) {
        Pattern pattern = Pattern.compile(
                "const\\s+settlement\\s*=\\s*1\\s*-\\s*(" + NUMBER + ")\\s*;"
        );
        Matcher matcher = pattern.matcher(script);
        if (!matcher.find()) {
            throw unsupported("No se pudo interpretar la formula settlement");
        }
        return BigDecimal.ONE.subtract(new BigDecimal(matcher.group(1)));
    }

    private Map<String, PaymentOption> paymentOptions(String script, BigDecimal settlement) {
        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(script);
        if (!optionsMatcher.find()) {
            throw unsupported("No se pudo interpretar el arreglo options");
        }
        Map<String, PaymentOption> options = new LinkedHashMap<>();
        Matcher optionMatcher = OPTION_PATTERN.matcher(optionsMatcher.group(1));
        while (optionMatcher.find()) {
            String body = optionMatcher.group(1);
            String name = requiredGroup(OPTION_NAME_PATTERN, body, "nombre de medio de pago");
            String key = normalizeName(name);
            String kind = optionalGroup(OPTION_KIND_PATTERN, body);
            String countValue = requiredGroup(OPTION_COUNT_PATTERN, body, "cantidad de pagos para " + name);
            String netExpression = optionalGroup(OPTION_NET_PATTERN, body);
            BigDecimal net = netExpression == null ? null : parseNet(netExpression, settlement, name);
            PaymentOption previous = options.put(key, new PaymentOption(kind, Integer.parseInt(countValue), net));
            if (previous != null) {
                throw unsupported("Medio de pago duplicado en options: " + name);
            }
        }
        return options;
    }

    private BigDecimal parseNet(String expression, BigDecimal settlement, String optionName) {
        String normalized = expression.replaceAll("\\s+", "");
        Pattern pattern = Pattern.compile("^(" + NUMBER + ")(\\*settlement)?$");
        Matcher matcher = pattern.matcher(normalized);
        if (!matcher.matches()) {
            throw unsupported("Formula net no soportada para " + optionName);
        }
        BigDecimal net = new BigDecimal(matcher.group(1));
        if (matcher.group(2) != null) {
            net = net.multiply(settlement, CALCULATION_CONTEXT);
        }
        validateNet("net de " + optionName, net);
        return net;
    }

    private BigDecimal validateQuoteFormula(String script) {
        String quoteBody = quoteFunctionBody(script)
                .replaceAll("\\s+", "")
                .replace('"', '\'');
        requireFormula(quoteBody, "constmode=options[index];");
        requireFormula(quoteBody, "constactive=currentCash(p,color);constcashArs=p.currency==='USD'?active*exchange:active;");
        requireFormula(quoteBody, "constrounding=(p.net7Pricing||p.net10Pricing)?Math.ceil:Math.round;");
        Matcher specialMatcher = SPECIAL_TRANSFER_PATTERN.matcher(quoteBody);
        if (!specialMatcher.find()) {
            throw unsupported("Cambio no soportado en la formula de transferencia net7Pricing/net10Pricing");
        }
        requireFormula(
                quoteBody,
                "consttotal=mode.net?rounding(transferArs/mode.net):mode.kind==='dollar'?rounding(cashArs/exchange):mode.kind==='usdt'?rounding(transferArs/exchange):mode.kind==='transfer'?transferArs:cashArs;"
        );
        BigDecimal numerator = new BigDecimal(specialMatcher.group(1));
        BigDecimal denominator = new BigDecimal(specialMatcher.group(2));
        validatePositive("numerador de transferencia especial", numerator);
        validatePositive("denominador de transferencia especial", denominator);
        return numerator.divide(denominator, CALCULATION_CONTEXT);
    }

    private String quoteFunctionBody(String script) {
        Matcher matcher = QUOTE_FUNCTION_PATTERN.matcher(script);
        if (!matcher.find()) {
            throw unsupported("No se encontro la funcion quote()");
        }
        int start = matcher.end() - 1;
        int depth = 0;
        for (int index = start; index < script.length(); index++) {
            char current = script.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return script.substring(start + 1, index);
                }
            }
        }
        throw unsupported("La funcion quote() esta incompleta");
    }

    private void requireFormula(String body, String expected) {
        if (!body.contains(expected)) {
            throw unsupported("Cambio no soportado en la funcion quote()");
        }
    }

    private void requireKind(Map<String, PaymentOption> options, String name, String expectedKind, int expectedCount) {
        PaymentOption option = requireOption(options, name);
        if (!expectedKind.equals(option.kind()) || option.count() != expectedCount || option.net() != null) {
            throw unsupported("Definicion no soportada para " + name);
        }
    }

    private BigDecimal requireNet(Map<String, PaymentOption> options, String name, int expectedCount) {
        PaymentOption option = requireOption(options, name);
        if (option.kind() != null || option.count() != expectedCount || option.net() == null) {
            throw unsupported("Definicion no soportada para " + name);
        }
        return option.net();
    }

    private PaymentOption requireOption(Map<String, PaymentOption> options, String name) {
        PaymentOption option = options.get(normalizeName(name));
        if (option == null) {
            throw unsupported("No se encontro el medio de pago " + name);
        }
        return option;
    }

    private MarkupByTransferScheme markupByScheme(
            BigDecimal generalTransfer,
            BigDecimal specialTransfer,
            BigDecimal net
    ) {
        return new MarkupByTransferScheme(
                markup(generalTransfer, net),
                markup(specialTransfer, net)
        );
    }

    private BigDecimal markup(BigDecimal transferMultiplier, BigDecimal net) {
        return transferMultiplier
                .divide(net, CALCULATION_CONTEXT)
                .subtract(BigDecimal.ONE)
                .multiply(ONE_HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private LiberadosYaQuotationResponse response(Snapshot snapshot, UpdateStatus status, String errorMessage) {
        ParsedQuotation quotation = snapshot == null ? null : snapshot.quotation();
        return new LiberadosYaQuotationResponse(
                snapshot == null ? null : snapshot.obtainedAt(),
                lastAttemptAt,
                status,
                status != UpdateStatus.CURRENT,
                snapshot == null ? null : snapshot.sourceAppJsUrl(),
                quotation == null ? null : quotation.dolarBillete(),
                null,
                quotation == null ? null : quotation.transferencia(),
                quotation == null ? null : quotation.tarjeta1Pago(),
                quotation == null ? null : quotation.qr1Pago(),
                quotation == null ? null : quotation.planZ3Pagos(),
                quotation == null ? null : quotation.tarjeta3Pagos(),
                quotation == null ? null : quotation.tarjeta6Pagos(),
                errorMessage
        );
    }

    private String fetch(URI uri, String accept, int maxLength) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                .header("Accept", accept)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LiberadosYaIntegrationException("LiberadosYa respondio " + response.statusCode());
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new LiberadosYaIntegrationException("LiberadosYa devolvio una respuesta vacia");
            }
            if (body.length() > maxLength) {
                throw new LiberadosYaIntegrationException("La respuesta de LiberadosYa supera el limite permitido");
            }
            return body;
        } catch (HttpTimeoutException ex) {
            throw new LiberadosYaIntegrationException("Timeout al consultar cotizaciones de LiberadosYa", ex);
        } catch (IOException ex) {
            throw new LiberadosYaIntegrationException("No se pudieron consultar las cotizaciones de LiberadosYa", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LiberadosYaIntegrationException("Consulta de cotizaciones de LiberadosYa interrumpida", ex);
        }
    }

    private boolean isFresh(Snapshot snapshot, Instant now) {
        return snapshot != null && now.isBefore(snapshot.obtainedAt().plus(CACHE_TTL));
    }

    private String normalizeName(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String requiredGroup(Pattern pattern, String value, String label) {
        String result = optionalGroup(pattern, value);
        if (result == null) {
            throw unsupported("No se pudo interpretar " + label);
        }
        return result;
    }

    private String optionalGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(matcher.groupCount()) : null;
    }

    private void validatePositive(String label, BigDecimal value) {
        if (value.signum() <= 0) {
            throw unsupported(label + " debe ser positivo");
        }
    }

    private void validateMultiplier(String label, BigDecimal value) {
        if (value.compareTo(BigDecimal.ONE) < 0 || value.compareTo(new BigDecimal("3")) > 0) {
            throw unsupported(label + " esta fuera del rango permitido");
        }
    }

    private void validateNet(String label, BigDecimal value) {
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw unsupported(label + " esta fuera del rango permitido");
        }
    }

    private LiberadosYaIntegrationException unsupported(String message) {
        return new LiberadosYaIntegrationException(message);
    }

    private String safeError(RuntimeException ex) {
        Throwable root = TiendaPorteDiagnostics.rootCause(ex);
        String message = root == null || root.getMessage() == null || root.getMessage().isBlank()
                ? ex.getMessage()
                : root.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        message = message.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private record PaymentOption(String kind, int count, BigDecimal net) {
    }

    private record ParsedQuotation(
            BigDecimal dolarBillete,
            MarkupByTransferScheme transferencia,
            MarkupByTransferScheme tarjeta1Pago,
            MarkupByTransferScheme qr1Pago,
            MarkupByTransferScheme planZ3Pagos,
            MarkupByTransferScheme tarjeta3Pagos,
            MarkupByTransferScheme tarjeta6Pagos
    ) {
    }

    private record Snapshot(
            Instant obtainedAt,
            String sourceAppJsUrl,
            ParsedQuotation quotation
    ) {
    }
}
