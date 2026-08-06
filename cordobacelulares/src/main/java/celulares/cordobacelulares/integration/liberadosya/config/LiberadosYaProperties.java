package celulares.cordobacelulares.integration.liberadosya.config;

import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "liberadosya")
public class LiberadosYaProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaProperties.class);

    private boolean enabled = true;
    private String baseUrl = "https://liberadosya.com";
    private String sitemapUrl = "https://liberadosya.com/sitemap.xml";
    private String refreshCron = "0 0 */6 * * *";
    private boolean initialRefresh = true;
    private long requestDelayMs = 400;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 25;
    private int maxRetries = 2;
    private int maxConcurrency = 4;
    private int ambiguityThreshold = 5;
    private int minimumMatchScore = 80;
    private String storagePath = "./data/liberadosya/catalog-current.json";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSitemapUrl() {
        return sitemapUrl;
    }

    public void setSitemapUrl(String sitemapUrl) {
        this.sitemapUrl = sitemapUrl;
    }

    public String getRefreshCron() {
        return refreshCron;
    }

    public void setRefreshCron(String refreshCron) {
        this.refreshCron = refreshCron;
    }

    public boolean isInitialRefresh() {
        return initialRefresh;
    }

    public void setInitialRefresh(boolean initialRefresh) {
        this.initialRefresh = initialRefresh;
    }

    public long getRequestDelayMs() {
        return requestDelayMs;
    }

    public void setRequestDelayMs(long requestDelayMs) {
        this.requestDelayMs = requestDelayMs;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getAmbiguityThreshold() {
        return ambiguityThreshold;
    }

    public void setAmbiguityThreshold(int ambiguityThreshold) {
        this.ambiguityThreshold = ambiguityThreshold;
    }

    public int getMinimumMatchScore() {
        return minimumMatchScore;
    }

    public void setMinimumMatchScore(int minimumMatchScore) {
        this.minimumMatchScore = minimumMatchScore;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public Path storagePath() {
        return Path.of(storagePath);
    }

    public String normalizedBaseUrl() {
        return stripTrailingSlash(baseUrl);
    }

    @PostConstruct
    public void validateAndLog() {
        validateUrl("liberadosya.base-url", baseUrl);
        validateUrl("liberadosya.sitemap-url", sitemapUrl);
        validatePositive("liberadosya.connect-timeout-seconds", connectTimeoutSeconds);
        validatePositive("liberadosya.read-timeout-seconds", readTimeoutSeconds);
        validatePositiveOrZero("liberadosya.request-delay-ms", requestDelayMs);
        validatePositiveOrZero("liberadosya.max-retries", maxRetries);
        validatePositive("liberadosya.max-concurrency", maxConcurrency);
        validatePositive("liberadosya.minimum-match-score", minimumMatchScore);
        validatePositive("liberadosya.ambiguity-threshold", ambiguityThreshold);
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalStateException("liberadosya.storage-path no puede estar vacio");
        }

        LOGGER.info(
                "LiberadosYa configurado. cid={} enabled={} baseUrl={} sitemapUrl={} requestDelayMs={} connectTimeoutSeconds={} readTimeoutSeconds={} maxRetries={} maxConcurrency={} storagePath={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                enabled,
                TiendaPorteDiagnostics.safeUriHostAndPath(baseUrl),
                TiendaPorteDiagnostics.safeUriHostAndPath(sitemapUrl),
                requestDelayMs,
                connectTimeoutSeconds,
                readTimeoutSeconds,
                maxRetries,
                maxConcurrency,
                storagePath
        );
    }

    private void validateUrl(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " no puede estar vacia");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(propertyName + " debe ser una URL valida", ex);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null || (!"http".equals(scheme) && !"https".equals(scheme))) {
            throw new IllegalStateException(propertyName + " debe ser absoluta y usar http o https");
        }
    }

    private void validatePositive(String propertyName, long value) {
        if (value <= 0) {
            throw new IllegalStateException(propertyName + " debe ser mayor que cero");
        }
    }

    private void validatePositiveOrZero(String propertyName, long value) {
        if (value < 0) {
            throw new IllegalStateException(propertyName + " no puede ser negativo");
        }
    }

    private String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
