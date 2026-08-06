package celulares.cordobacelulares.integration.liberadosya.scraping;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaCatalogError;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaCatalogSnapshot;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaRateLimitException;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class LiberadosYaScrapingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaScrapingClient.class);
    private static final int MAX_SITEMAPS = 100;
    private static final String USER_AGENT = "CordobaCelulares-LiberadosYaCatalog/1.0";

    private final LiberadosYaProperties properties;
    private final LiberadosYaProductPageParser productPageParser;
    private final Clock clock;
    private final HttpClient httpClient;
    private final Object throttleLock = new Object();
    private long lastRequestStartedAtMs;

    @Autowired
    public LiberadosYaScrapingClient(LiberadosYaProperties properties, LiberadosYaProductPageParser productPageParser) {
        this(properties, productPageParser, Clock.systemUTC(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    LiberadosYaScrapingClient(
            LiberadosYaProperties properties,
            LiberadosYaProductPageParser productPageParser,
            Clock clock,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.productPageParser = productPageParser;
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
        progress.phase("DISCOVERING");
        Map<String, String> productUrls = productUrlsFromSitemap();
        progress.productsDiscovered(productUrls.size());
        LOGGER.info(
                "LiberadosYa catalog refresh discovered URLs. cid={} totalUrls={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                productUrls.size()
        );
        List<LiberadosYaProduct> products = new ArrayList<>();
        List<LiberadosYaCatalogError> errors = new ArrayList<>();
        if (!productUrls.isEmpty()) {
            progress.phase("SCRAPING");
            scrapeProducts(productUrls, generatedAt, products, errors, progress);
        }
        products.sort(Comparator.comparing(product -> product.getSlug() == null ? "" : product.getSlug()));
        LiberadosYaCatalogSnapshot snapshot = new LiberadosYaCatalogSnapshot(
                generatedAt,
                LiberadosYaProductImageSanitizer.SCHEMA_VERSION,
                properties.normalizedBaseUrl(),
                productUrls.size(),
                products.size(),
                errors.size(),
                List.copyOf(products),
                List.copyOf(errors)
        );
        LOGGER.info(
                "LiberadosYa catalog refresh parsed. cid={} urls={} products={} errors={} durationMs={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                snapshot.getTotalUrlsFound(),
                snapshot.getTotalProducts(),
                snapshot.getErrorsCount(),
                (System.nanoTime() - startNanos) / 1_000_000
        );
        return snapshot;
    }

    private Map<String, String> productUrlsFromSitemap() {
        Map<String, String> products = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        collectSitemap(URI.create(properties.getSitemapUrl()), visited, products);
        return products;
    }

    private void collectSitemap(URI sitemapUrl, Set<String> visited, Map<String, String> products) {
        if (visited.size() >= MAX_SITEMAPS) {
            throw new LiberadosYaIntegrationException("Demasiados sitemaps al recorrer LiberadosYa");
        }
        String normalized = sitemapUrl.toString();
        if (!visited.add(normalized)) {
            return;
        }
        String xml = fetch(sitemapUrl, "application/xml,text/xml,*/*");
        Document document = Jsoup.parse(xml, normalized, Parser.xmlParser());
        for (Element sitemapLoc : document.select("sitemap > loc")) {
            URI child = sitemapUrl.resolve(sitemapLoc.text().trim());
            collectSitemap(child, visited, products);
        }
        for (Element urlLoc : document.select("url > loc")) {
            String url = urlLoc.text().trim();
            String slug = productSlug(url);
            if (slug != null) {
                products.putIfAbsent(slug, normalizeProductUrl(url));
            }
        }
    }

    private void scrapeProducts(
            Map<String, String> productUrls,
            Instant extractedAt,
            List<LiberadosYaProduct> products,
            List<LiberadosYaCatalogError> errors,
            ProgressListener progress
    ) {
        int concurrency = Math.max(1, properties.getMaxConcurrency());
        ExecutorService executor = Executors.newFixedThreadPool(concurrency, runnable -> {
            Thread thread = new Thread(runnable, "liberadosya-scraper");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<CompletableFuture<ProductScrapeResult>> futures = productUrls.entrySet().stream()
                    .map(entry -> CompletableFuture.supplyAsync(() -> scrapeProduct(entry.getKey(), entry.getValue(), extractedAt, progress), executor))
                    .toList();
            for (CompletableFuture<ProductScrapeResult> future : futures) {
                try {
                    ProductScrapeResult result = future.join();
                    if (result.product() != null) {
                        products.add(result.product());
                    }
                    errors.addAll(result.errors());
                } catch (CompletionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    errors.add(error(null, null, cause));
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    private ProductScrapeResult scrapeProduct(String slug, String arsUrl, Instant extractedAt, ProgressListener progress) {
        List<LiberadosYaCatalogError> errors = new ArrayList<>();
        progress.productStarted(arsUrl);
        try {
            String usdUrl = properties.normalizedBaseUrl() + "/us/productos/" + slug + "/";
            String arsHtml = fetch(URI.create(arsUrl), "text/html,*/*");
            String usdHtml = fetch(URI.create(usdUrl), "text/html,*/*");
            LiberadosYaProduct product = productPageParser.parse(slug, arsUrl, arsHtml, usdUrl, usdHtml, extractedAt);
            return new ProductScrapeResult(product, List.copyOf(errors));
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "LiberadosYa product scrape failed. cid={} slug={} url={} cause={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    slug,
                    TiendaPorteDiagnostics.safeUriHostAndPath(arsUrl),
                    safeErrorLabel(ex)
            );
            errors.add(error(arsUrl, slug, ex));
            return new ProductScrapeResult(null, List.copyOf(errors));
        } finally {
            progress.productFinished(arsUrl);
        }
    }

    private String fetch(URI uri, String accept) {
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
            long startNanos = System.nanoTime();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                LOGGER.debug(
                        "LiberadosYa HTTP response. cid={} target={} status={} durationMs={} bodySize={}",
                        TiendaPorteDiagnostics.currentCorrelationId(),
                        TiendaPorteDiagnostics.safeUriHostAndPath(uri),
                        response.statusCode(),
                        durationMs,
                        TiendaPorteDiagnostics.bodySize(response.body())
                );
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                if (response.statusCode() == 429) {
                    Instant retryAfter = retryAfter(response);
                    lastFailure = new LiberadosYaRateLimitException("LiberadosYa limito temporalmente las solicitudes", retryAfter);
                    sleepBeforeRetry(attempt, retryAfter);
                    continue;
                }
                if (response.statusCode() == 404 || response.statusCode() == 410) {
                    throw new LiberadosYaIntegrationException("LiberadosYa respondio " + response.statusCode() + " para " + TiendaPorteDiagnostics.safeUriHostAndPath(uri));
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
        return response.headers()
                .firstValue("Retry-After")
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
            // HTTP-date is allowed by Retry-After.
        }
        try {
            return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ex) {
            return now.plusSeconds(60);
        }
    }

    private String productSlug(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.startsWith("/us/")) {
                return null;
            }
            String[] parts = path.split("/");
            if (parts.length != 3 || !"productos".equals(parts[1]) || parts[2].isBlank()) {
                return null;
            }
            return parts[2];
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String normalizeProductUrl(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String path = uri.getPath();
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return properties.normalizedBaseUrl() + path;
    }

    private LiberadosYaCatalogError error(String url, String slug, Throwable throwable) {
        String code = throwable instanceof LiberadosYaRateLimitException ? "HTTP_429" : throwable.getClass().getSimpleName();
        String message = safeErrorLabel(throwable);
        if (message.length() > 300) {
            message = message.substring(0, 300);
        }
        return new LiberadosYaCatalogError(url, slug, code, message, clock.instant());
    }

    private String safeErrorLabel(Throwable throwable) {
        Throwable rootCause = TiendaPorteDiagnostics.rootCause(throwable);
        if (rootCause == null) {
            return "-";
        }
        String className = rootCause.getClass().getSimpleName();
        String message = rootCause.getMessage();
        if (looksLikeHtml(message)) {
            return className + ": html-redacted";
        }
        String sanitized = message == null || message.isBlank() ? className : className + ": " + message;
        return sanitized
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private boolean looksLikeHtml(String value) {
        return value != null && value.matches("(?is).*<\\s*/?\\s*[a-z][a-z0-9:-]*(\\s|>|/).*");
    }

    private record ProductScrapeResult(LiberadosYaProduct product, List<LiberadosYaCatalogError> errors) {
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
