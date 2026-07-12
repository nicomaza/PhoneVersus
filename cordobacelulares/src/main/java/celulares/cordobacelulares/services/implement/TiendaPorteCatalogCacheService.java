package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheRefreshResponse;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheStatusResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteRateLimitException;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TiendaPorteCatalogCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiendaPorteCatalogCacheService.class);
    private static final String PRODUCTS_ERROR_MESSAGE = "No se pudo obtener el catalogo del proveedor externo";

    private final TiendaPorteClient tiendaPorteClient;
    private final TiendaPorteProperties properties;
    private final Clock clock;
    private final ExecutorService refreshExecutor;
    private final boolean ownsExecutor;
    private final Object refreshLock = new Object();
    private final AtomicReference<CatalogCacheSnapshot> snapshot = new AtomicReference<>();
    private final AtomicReference<Instant> lastAttemptAt = new AtomicReference<>();
    private final AtomicReference<Instant> nextAllowedRefreshAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicLong version = new AtomicLong();

    private CompletableFuture<CatalogCacheSnapshot> refreshInFlight;

    @Autowired
    public TiendaPorteCatalogCacheService(TiendaPorteClient tiendaPorteClient, TiendaPorteProperties properties) {
        this(tiendaPorteClient, properties, Clock.systemDefaultZone(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tienda-porte-catalog-cache-refresh");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    TiendaPorteCatalogCacheService(
            TiendaPorteClient tiendaPorteClient,
            TiendaPorteProperties properties,
            Clock clock,
            ExecutorService refreshExecutor,
            boolean ownsExecutor
    ) {
        this.tiendaPorteClient = tiendaPorteClient;
        this.properties = properties;
        this.clock = clock;
        this.refreshExecutor = refreshExecutor;
        this.ownsExecutor = ownsExecutor;
    }

    @PreDestroy
    public void shutdown() {
        if (ownsExecutor) {
            refreshExecutor.shutdown();
        }
    }

    public List<TiendaPorteExternalProduct> getProducts() {
        CatalogCacheSnapshot current = snapshot.get();
        Instant now = now();
        if (current != null && current.isFresh(now, ttl())) {
            return current.productsCopy();
        }
        if (current != null) {
            LOGGER.info(
                    "Catalog cache stale. Returning previous snapshot and scheduling refresh: version={} ageSeconds={}",
                    current.version(),
                    Duration.between(current.lastSuccessfulRefreshAt(), now).toSeconds()
            );
            startRefresh(false);
            return current.productsCopy();
        }

        RefreshStart refreshStart = startRefresh(false);
        if (refreshStart.blockedByCooldown()) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }
        return awaitInitialSnapshot(refreshStart.future()).productsCopy();
    }

    public CatalogCacheStatusResponse status() {
        CatalogCacheSnapshot current = snapshot.get();
        Instant now = now();
        boolean initialized = current != null;
        Instant expiresAt = initialized ? current.expiresAt(ttl()) : null;
        boolean fresh = initialized && now.isBefore(expiresAt);
        boolean stale = initialized && !fresh;
        return new CatalogCacheStatusResponse(
                initialized,
                fresh,
                stale,
                isRefreshing(),
                initialized ? current.version() : 0,
                initialized ? current.productCount() : 0,
                initialized ? current.lastSuccessfulRefreshAt() : null,
                lastAttemptAt.get(),
                expiresAt,
                nextAllowedRefreshAt.get(),
                lastError.get()
        );
    }

    public CatalogCacheRefreshResponse refreshFromAdmin() {
        RefreshStart refreshStart = startRefresh(true);
        return new CatalogCacheRefreshResponse(
                refreshStart.started(),
                refreshStart.alreadyRunning(),
                refreshStart.blockedByCooldown(),
                status()
        );
    }

    @Scheduled(
            fixedDelayString = "${tiendaporte.catalog-cache.refresh-ms:180000}",
            initialDelayString = "${tiendaporte.catalog-cache.initial-delay-ms:5000}"
    )
    public void scheduledRefresh() {
        startRefresh(false);
    }

    private RefreshStart startRefresh(boolean force) {
        synchronized (refreshLock) {
            Instant now = now();
            CompletableFuture<CatalogCacheSnapshot> currentRefresh = activeRefresh();
            if (currentRefresh != null) {
                return RefreshStart.alreadyRunning(currentRefresh);
            }
            if (isCooldownActive(now)) {
                return RefreshStart.blocked();
            }

            CatalogCacheSnapshot current = snapshot.get();
            if (!force && current != null && current.isFresh(now, ttl())) {
                return RefreshStart.skipped(CompletableFuture.completedFuture(current));
            }

            CompletableFuture<CatalogCacheSnapshot> future = CompletableFuture.supplyAsync(this::refreshSnapshot, refreshExecutor);
            refreshInFlight = future;
            future.whenComplete((unused, throwable) -> clearRefresh(future));
            return RefreshStart.started(future);
        }
    }

    private CatalogCacheSnapshot refreshSnapshot() {
        Instant attemptAt = now();
        long startNanos = System.nanoTime();
        lastAttemptAt.set(attemptAt);
        try {
            List<TiendaPorteExternalProduct> products = tiendaPorteClient.getProducts();
            CatalogCacheSnapshot refreshed = new CatalogCacheSnapshot(
                    copyProducts(products),
                    now(),
                    attemptAt,
                    version.incrementAndGet()
            );
            snapshot.set(refreshed);
            nextAllowedRefreshAt.set(null);
            lastError.set(null);
            LOGGER.info(
                    "Catalog cache refreshed: version={} productCount={} durationMs={} expiresAt={}",
                    refreshed.version(),
                    refreshed.productCount(),
                    (System.nanoTime() - startNanos) / 1_000_000,
                    refreshed.expiresAt(ttl())
            );
            return refreshed;
        } catch (TiendaPorteRateLimitException ex) {
            Instant nextAllowed = ex.getNextAllowedRefreshAt() == null ? attemptAt.plus(ttl()) : ex.getNextAllowedRefreshAt();
            nextAllowedRefreshAt.set(nextAllowed);
            markFailure(attemptAt, "Proveedor limitado temporalmente");
            CatalogCacheSnapshot current = snapshot.get();
            LOGGER.warn(
                    "Catalog refresh rate limited: retryAfter={} nextAllowedRefreshAt={} usingVersion={}",
                    ex.getRetryAfter(),
                    nextAllowed,
                    current == null ? 0 : current.version()
            );
            throw ex;
        } catch (RuntimeException ex) {
            nextAllowedRefreshAt.set(null);
            markFailure(attemptAt, TiendaPorteDiagnostics.rootCauseLabel(ex));
            CatalogCacheSnapshot current = snapshot.get();
            if (current != null) {
                LOGGER.error(
                        "Catalog refresh failed. Keeping previous snapshot: version={} lastSuccessfulRefreshAt={} cause={}",
                        current.version(),
                        current.lastSuccessfulRefreshAt(),
                        TiendaPorteDiagnostics.rootCauseLabel(ex),
                        ex
                );
            }
            throw ex;
        }
    }

    private void markFailure(Instant attemptAt, String error) {
        lastAttemptAt.set(attemptAt);
        lastError.set(sanitizeError(error));
    }

    private String sanitizeError(String error) {
        if (error == null || error.isBlank()) {
            return "No se pudo actualizar el catalogo";
        }
        String sanitized = error.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private CatalogCacheSnapshot awaitInitialSnapshot(CompletableFuture<CatalogCacheSnapshot> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, cause);
        }
    }

    private void clearRefresh(CompletableFuture<CatalogCacheSnapshot> future) {
        synchronized (refreshLock) {
            if (refreshInFlight == future) {
                refreshInFlight = null;
            }
        }
    }

    private CompletableFuture<CatalogCacheSnapshot> activeRefresh() {
        return refreshInFlight == null || refreshInFlight.isDone() ? null : refreshInFlight;
    }

    private boolean isRefreshing() {
        synchronized (refreshLock) {
            return activeRefresh() != null;
        }
    }

    private boolean isCooldownActive(Instant now) {
        Instant nextAllowed = nextAllowedRefreshAt.get();
        return nextAllowed != null && now.isBefore(nextAllowed);
    }

    private Instant now() {
        return clock.instant();
    }

    private Duration ttl() {
        return Duration.ofMillis(properties.getCatalogCache().getTtlMs());
    }

    private List<TiendaPorteExternalProduct> copyProducts(List<TiendaPorteExternalProduct> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        List<TiendaPorteExternalProduct> copies = new ArrayList<>();
        for (TiendaPorteExternalProduct product : products) {
            if (product != null) {
                copies.add(copyProduct(product));
            }
        }
        return List.copyOf(copies);
    }

    private TiendaPorteExternalProduct copyProduct(TiendaPorteExternalProduct product) {
        TiendaPorteExternalProduct copy = new TiendaPorteExternalProduct();
        copy.setName(product.getName());
        copy.setPricePesos(product.getPricePesos());
        copy.setColorStock(copyMap(product.getColorStock()));
        copy.setCategory(copyCategory(product.getCategory()));
        copy.setSourceCategory(product.getSourceCategory());
        copy.setOriginalCategory(product.getOriginalCategory());
        copy.setProductReference(copyProductReference(product.getProductReference()));
        return copy;
    }

    private TiendaPorteProductReference copyProductReference(TiendaPorteProductReference productReference) {
        if (productReference == null) {
            return null;
        }
        TiendaPorteProductReference copy = new TiendaPorteProductReference();
        copy.setId(productReference.getId());
        copy.setName(productReference.getName());
        copy.setPriceUsd(productReference.getPriceUsd());
        copy.setPricePesos(productReference.getPricePesos());
        copy.setColorPrices(copyMap(productReference.getColorPrices()));
        copy.setCategory(copyCategory(productReference.getCategory()));
        return copy;
    }

    private TiendaPorteCategory copyCategory(TiendaPorteCategory category) {
        if (category == null) {
            return null;
        }
        TiendaPorteCategory copy = new TiendaPorteCategory();
        copy.setName(category.getName());
        return copy;
    }

    private <K, V> Map<K, V> copyMap(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private record CatalogCacheSnapshot(
            List<TiendaPorteExternalProduct> products,
            Instant lastSuccessfulRefreshAt,
            Instant lastAttemptAt,
            long version
    ) {

        private CatalogCacheSnapshot {
            Objects.requireNonNull(products, "products");
            Objects.requireNonNull(lastSuccessfulRefreshAt, "lastSuccessfulRefreshAt");
            Objects.requireNonNull(lastAttemptAt, "lastAttemptAt");
        }

        private boolean isFresh(Instant now, Duration ttl) {
            return now.isBefore(expiresAt(ttl));
        }

        private Instant expiresAt(Duration ttl) {
            return lastSuccessfulRefreshAt.plus(ttl);
        }

        private int productCount() {
            return products.size();
        }

        private List<TiendaPorteExternalProduct> productsCopy() {
            return products.stream()
                    .map(TiendaPorteCatalogCacheService::staticCopyProduct)
                    .toList();
        }
    }

    private static TiendaPorteExternalProduct staticCopyProduct(TiendaPorteExternalProduct product) {
        TiendaPorteExternalProduct copy = new TiendaPorteExternalProduct();
        copy.setName(product.getName());
        copy.setPricePesos(product.getPricePesos());
        copy.setColorStock(staticCopyMap(product.getColorStock()));
        copy.setCategory(staticCopyCategory(product.getCategory()));
        copy.setSourceCategory(product.getSourceCategory());
        copy.setOriginalCategory(product.getOriginalCategory());
        copy.setProductReference(staticCopyProductReference(product.getProductReference()));
        return copy;
    }

    private static TiendaPorteProductReference staticCopyProductReference(TiendaPorteProductReference productReference) {
        if (productReference == null) {
            return null;
        }
        TiendaPorteProductReference copy = new TiendaPorteProductReference();
        copy.setId(productReference.getId());
        copy.setName(productReference.getName());
        copy.setPriceUsd(productReference.getPriceUsd());
        copy.setPricePesos(productReference.getPricePesos());
        copy.setColorPrices(staticCopyMap(productReference.getColorPrices()));
        copy.setCategory(staticCopyCategory(productReference.getCategory()));
        return copy;
    }

    private static TiendaPorteCategory staticCopyCategory(TiendaPorteCategory category) {
        if (category == null) {
            return null;
        }
        TiendaPorteCategory copy = new TiendaPorteCategory();
        copy.setName(category.getName());
        return copy;
    }

    private static <K, V> Map<K, V> staticCopyMap(Map<K, V> map) {
        if (map == null || map.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private record RefreshStart(
            boolean started,
            boolean alreadyRunning,
            boolean blockedByCooldown,
            CompletableFuture<CatalogCacheSnapshot> future
    ) {

        private static RefreshStart started(CompletableFuture<CatalogCacheSnapshot> future) {
            return new RefreshStart(true, false, false, future);
        }

        private static RefreshStart alreadyRunning(CompletableFuture<CatalogCacheSnapshot> future) {
            return new RefreshStart(false, true, false, future);
        }

        private static RefreshStart blocked() {
            return new RefreshStart(false, false, true, null);
        }

        private static RefreshStart skipped(CompletableFuture<CatalogCacheSnapshot> future) {
            return new RefreshStart(false, false, false, future);
        }
    }
}
