package celulares.cordobacelulares.config;

import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "tiendaporte")
public class TiendaPorteProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiendaPorteProperties.class);

    private String authBaseUrl = "https://sistema.tiendaporte.com";
    private String apiBaseUrl = "https://api.tiendaporte.com";
    private int productsLimit = 5000;
    private int connectTimeoutSeconds = 10;
    private int requestTimeoutSeconds = 30;
    private CatalogCache catalogCache = new CatalogCache();
    private ProductSync productSync = new ProductSync();

    public String getAuthBaseUrl() {
        return authBaseUrl;
    }

    public void setAuthBaseUrl(String authBaseUrl) {
        this.authBaseUrl = authBaseUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getProductsLimit() {
        return productsLimit;
    }

    public void setProductsLimit(int productsLimit) {
        this.productsLimit = productsLimit;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public CatalogCache getCatalogCache() {
        return catalogCache;
    }

    public void setCatalogCache(CatalogCache catalogCache) {
        this.catalogCache = catalogCache == null ? new CatalogCache() : catalogCache;
    }

    public ProductSync getProductSync() {
        return productSync;
    }

    public void setProductSync(ProductSync productSync) {
        this.productSync = productSync == null ? new ProductSync() : productSync;
    }

    @PostConstruct
    public void validateAndLog() {
        validateBaseUrl("tiendaporte.auth-base-url", authBaseUrl);
        validateBaseUrl("tiendaporte.api-base-url", apiBaseUrl);
        if (productsLimit <= 0) {
            throw new IllegalStateException("tiendaporte.products-limit debe ser mayor que cero");
        }
        if (connectTimeoutSeconds <= 0) {
            throw new IllegalStateException("tiendaporte.connect-timeout-seconds debe ser mayor que cero");
        }
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalStateException("tiendaporte.request-timeout-seconds debe ser mayor que cero");
        }
        validatePositive("tiendaporte.catalog-cache.ttl-ms", catalogCache.getTtlMs());
        validatePositive("tiendaporte.catalog-cache.refresh-ms", catalogCache.getRefreshMs());
        validatePositive("tiendaporte.catalog-cache.initial-delay-ms", catalogCache.getInitialDelayMs());
        validatePositive("tiendaporte.product-sync.interval-ms", productSync.getIntervalMs());
        validatePositive("tiendaporte.product-sync.initial-delay-ms", productSync.getInitialDelayMs());
        validatePositive("tiendaporte.product-sync.batch-size", productSync.getBatchSize());
        validatePositive("tiendaporte.product-sync.max-concurrency", productSync.getMaxConcurrency());
        if (productSync.getMaxRetries() < 0) {
            throw new IllegalStateException("tiendaporte.product-sync.max-retries no puede ser negativo");
        }
        if (productSync.getAllowedImageHosts() == null || productSync.getAllowedImageHosts().isEmpty()) {
            throw new IllegalStateException("tiendaporte.product-sync.allowed-image-hosts no puede estar vacio");
        }

        LOGGER.info(
                "Tienda Porte configurado. cid={} authBase={} apiBase={} productsLimit={} connectTimeoutSeconds={} requestTimeoutSeconds={} catalogCacheTtlMs={} catalogCacheRefreshMs={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                TiendaPorteDiagnostics.safeUriHostAndPath(authBaseUrl),
                TiendaPorteDiagnostics.safeUriHostAndPath(apiBaseUrl),
                productsLimit,
                connectTimeoutSeconds,
                requestTimeoutSeconds,
                catalogCache.getTtlMs(),
                catalogCache.getRefreshMs()
        );
    }

    private void validateBaseUrl(String propertyName, String value) {
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

    public static class CatalogCache {

        private long ttlMs = 180000;
        private long refreshMs = 180000;
        private long initialDelayMs = 5000;

        public long getTtlMs() {
            return ttlMs;
        }

        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }

        public long getRefreshMs() {
            return refreshMs;
        }

        public void setRefreshMs(long refreshMs) {
            this.refreshMs = refreshMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }
    }

    public static class ProductSync {
        private boolean enabled = true;
        private long intervalMs = 86_400_000L;
        private long initialDelayMs = 300_000L;
        private int batchSize = 25;
        private int maxConcurrency = 3;
        private int maxRetries = 2;
        private List<String> allowedImageHosts = new ArrayList<>(List.of(
                "api.tiendaporte.com",
                "tiendaporte.com",
                "www.tiendaporte.com"
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public List<String> getAllowedImageHosts() { return allowedImageHosts; }
        public void setAllowedImageHosts(List<String> allowedImageHosts) {
            this.allowedImageHosts = allowedImageHosts == null ? new ArrayList<>() : new ArrayList<>(allowedImageHosts);
        }
    }
}
