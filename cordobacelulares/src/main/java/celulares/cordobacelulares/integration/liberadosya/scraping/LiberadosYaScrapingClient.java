package celulares.cordobacelulares.integration.liberadosya.scraping;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaCatalogSnapshot;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaRateLimitException;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@Component
public class LiberadosYaScrapingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaScrapingClient.class);
    private static final int MAX_HTML_LENGTH = 2_000_000;
    private static final int MAX_SCRIPT_LENGTH = 4_000_000;
    private static final String USER_AGENT = "CordobaCelulares-LiberadosYaCatalog/2.0";

    private final LiberadosYaProperties properties;
    private final LiberadosYaCatalogScriptParser scriptParser;
    private final Clock clock;
    private final HttpClient httpClient;
    private final Object throttleLock = new Object();
    private long lastRequestStartedAtMs;

    @Autowired
    public LiberadosYaScrapingClient(
            LiberadosYaProperties properties,
            LiberadosYaCatalogScriptParser scriptParser
    ) {
        this(properties, scriptParser, Clock.systemUTC(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    LiberadosYaScrapingClient(
            LiberadosYaProperties properties,
            LiberadosYaCatalogScriptParser scriptParser,
            Clock clock,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.scriptParser = scriptParser;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    public LiberadosYaCatalogSnapshot fetchCatalog() {
        return fetchCatalog(ProgressListener.noop());
    }

    public LiberadosYaCatalogSnapshot fetchCatalog(ProgressListener progressListener) {
        ProgressListener progress = progressListener == null ? ProgressListener.noop() : progressListener;
        Instant generatedAt = clock.instant();
        long startNanos = System.nanoTime();
        URI baseUri = URI.create(properties.normalizedBaseUrl() + "/");

        progress.phase("DISCOVERING");
        String html = fetch(baseUri, "text/html,*/*", MAX_HTML_LENGTH);
        Map<Asset, URI> assets = locateAssets(baseUri, html);

        progress.phase("SCRAPING");
        String catalogScript = fetch(assets.get(Asset.CATALOG), javascriptAccept(), MAX_SCRIPT_LENGTH);
        String fichasScript = fetch(assets.get(Asset.FICHAS), javascriptAccept(), MAX_SCRIPT_LENGTH);
        String pricingScript = fetch(assets.get(Asset.PRICING), javascriptAccept(), MAX_SCRIPT_LENGTH);
        String appScript = fetch(assets.get(Asset.APP), javascriptAccept(), MAX_SCRIPT_LENGTH);

        LiberadosYaCatalogScriptParser.ParsedCatalog parsed = scriptParser.parse(
                baseUri,
                assets.get(Asset.CATALOG), catalogScript,
                assets.get(Asset.FICHAS), fichasScript,
                assets.get(Asset.PRICING), pricingScript,
                assets.get(Asset.APP), appScript,
                generatedAt
        );
        progress.productsDiscovered(parsed.sourceProducts());
        parsed.products().forEach(product -> {
            progress.productStarted(product.getNombre());
            progress.productFinished(product.getNombre());
        });

        LiberadosYaCatalogSnapshot snapshot = new LiberadosYaCatalogSnapshot(
                generatedAt,
                LiberadosYaProductImageSanitizer.SCHEMA_VERSION,
                properties.normalizedBaseUrl(),
                parsed.sourceProducts(),
                parsed.products().size(),
                0,
                parsed.products(),
                java.util.List.of()
        );
        LOGGER.info(
                "LiberadosYa script catalog parsed. cid={} sourceProducts={} products={} retired={} unpublished={} exchange={} durationMs={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                parsed.sourceProducts(),
                parsed.products().size(),
                parsed.retiredProducts(),
                parsed.unpublishedProducts(),
                parsed.exchange(),
                (System.nanoTime() - startNanos) / 1_000_000
        );
        return snapshot;
    }

    Map<Asset, URI> locateAssets(URI baseUri, String html) {
        Document document = Jsoup.parse(html == null ? "" : html, baseUri.toString());
        Map<Asset, URI> selected = new EnumMap<>(Asset.class);
        for (Element script : document.select("script[src]")) {
            URI candidate;
            try {
                candidate = baseUri.resolve(script.attr("src"));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Asset asset = Asset.fromPath(candidate.getPath());
            if (asset == null) {
                continue;
            }
            if (!sameOrigin(baseUri, candidate)) {
                throw new LiberadosYaIntegrationException("El asset " + asset.fileName + " apunta a un origen no permitido");
            }
            URI previous = selected.putIfAbsent(asset, candidate);
            if (previous != null && !previous.equals(candidate)) {
                throw new LiberadosYaIntegrationException("Se encontraron multiples versiones de " + asset.fileName);
            }
        }
        for (Asset asset : Asset.values()) {
            if (!selected.containsKey(asset)) {
                throw new LiberadosYaIntegrationException("No se encontro la version vigente de " + asset.fileName);
            }
        }
        return Map.copyOf(selected);
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

    private String javascriptAccept() {
        return "application/javascript,text/javascript,*/*";
    }

    private String fetch(URI uri, String accept, int maximumLength) {
        int attempts = Math.max(0, properties.getMaxRetries()) + 1;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            throttle();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                    .header("Accept", accept)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String body = response.body() == null ? "" : response.body();
                    if (body.length() > maximumLength) {
                        throw new LiberadosYaIntegrationException("Respuesta demasiado grande desde LiberadosYa");
                    }
                    return body;
                }
                if (response.statusCode() == 429) {
                    Instant retryAfter = retryAfter(response);
                    lastFailure = new LiberadosYaRateLimitException("LiberadosYa limito temporalmente las solicitudes", retryAfter);
                    sleepBeforeRetry(attempt, retryAfter);
                    continue;
                }
                lastFailure = new LiberadosYaIntegrationException("LiberadosYa respondio " + response.statusCode());
                sleepBeforeRetry(attempt, null);
            } catch (HttpTimeoutException ex) {
                lastFailure = new LiberadosYaIntegrationException("Timeout al consultar LiberadosYa", ex);
                sleepBeforeRetry(attempt, null);
            } catch (IOException ex) {
                lastFailure = new LiberadosYaIntegrationException("No se pudo consultar LiberadosYa", ex);
                sleepBeforeRetry(attempt, null);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new LiberadosYaIntegrationException("Consulta a LiberadosYa interrumpida", ex);
            }
        }
        throw lastFailure == null ? new LiberadosYaIntegrationException("No se pudo consultar LiberadosYa") : lastFailure;
    }

    private void throttle() {
        long delayMs = Math.max(0, properties.getRequestDelayMs());
        if (delayMs == 0) {
            return;
        }
        synchronized (throttleLock) {
            long now = System.currentTimeMillis();
            long waitMs = lastRequestStartedAtMs + delayMs - now;
            if (waitMs > 0) {
                sleep(waitMs);
            }
            lastRequestStartedAtMs = System.currentTimeMillis();
        }
    }

    private void sleepBeforeRetry(int attempt, Instant retryAfter) {
        if (attempt > Math.max(0, properties.getMaxRetries())) {
            return;
        }
        long waitMs = retryAfter == null
                ? Math.min(5000, (long) Math.pow(2, attempt - 1) * 500L)
                : Math.max(0, Duration.between(clock.instant(), retryAfter).toMillis());
        sleep(waitMs);
    }

    private void sleep(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LiberadosYaIntegrationException("Consulta a LiberadosYa interrumpida", ex);
        }
    }

    private Instant retryAfter(HttpResponse<String> response) {
        Instant now = clock.instant();
        return response.headers().firstValue("Retry-After")
                .map(value -> parseRetryAfter(value, now))
                .orElse(now.plusSeconds(60));
    }

    private Instant parseRetryAfter(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return now.plusSeconds(60);
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? now.plusSeconds(60) : now.plusSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // Retry-After also accepts an HTTP date.
        }
        try {
            return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ex) {
            return now.plusSeconds(60);
        }
    }

    enum Asset {
        CATALOG("catalogo.js"),
        FICHAS("fichas.js"),
        PRICING("pricing.js"),
        APP("app.js");

        private final String fileName;

        Asset(String fileName) {
            this.fileName = fileName;
        }

        private static Asset fromPath(String path) {
            String normalized = path == null ? "" : path.toLowerCase(Locale.ROOT);
            for (Asset asset : values()) {
                if (normalized.equals("/" + asset.fileName) || normalized.endsWith("/" + asset.fileName)) {
                    return asset;
                }
            }
            return null;
        }
    }

    public interface ProgressListener {

        default void phase(String phase) {
        }

        default void productsDiscovered(int totalProducts) {
        }

        default void productStarted(String productUrl) {
        }

        default void productFinished(String productUrl) {
        }

        static ProgressListener noop() {
            return new ProgressListener() {
            };
        }
    }
}
