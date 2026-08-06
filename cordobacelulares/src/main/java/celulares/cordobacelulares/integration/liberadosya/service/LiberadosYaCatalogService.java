package celulares.cordobacelulares.integration.liberadosya.service;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaCatalogSnapshot;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaRefreshResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaStatusResponse;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaBadRequestException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaCatalogNotAvailableException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaMatchAmbiguousException;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaProductNotFoundException;
import celulares.cordobacelulares.integration.liberadosya.matching.LiberadosYaMatchResult;
import celulares.cordobacelulares.integration.liberadosya.matching.LiberadosYaProductMatcher;
import celulares.cordobacelulares.integration.liberadosya.scraping.LiberadosYaScrapingClient;
import celulares.cordobacelulares.integration.liberadosya.store.LiberadosYaCatalogState;
import celulares.cordobacelulares.integration.liberadosya.store.LiberadosYaCatalogStore;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LiberadosYaCatalogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaCatalogService.class);
    private static final int MAX_PARAM_LENGTH = 140;
    private static final String PHASE_IDLE = "IDLE";
    private static final String PHASE_STARTING = "STARTING";
    private static final String PHASE_DISCOVERING = "DISCOVERING";
    private static final String PHASE_SCRAPING = "SCRAPING";
    private static final String PHASE_PERSISTING = "PERSISTING";
    private static final String PHASE_COMPLETED = "COMPLETED";
    private static final String PHASE_FAILED = "FAILED";

    private final LiberadosYaProperties properties;
    private final LiberadosYaScrapingClient scrapingClient;
    private final LiberadosYaCatalogStore store;
    private final LiberadosYaProductMatcher matcher;
    private final Clock clock;
    private final ExecutorService refreshExecutor;
    private final boolean ownsExecutor;
    private final Object refreshLock = new Object();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastAttemptAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulRefreshAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<RefreshProgress> refreshProgress = new AtomicReference<>(RefreshProgress.idle());

    private CompletableFuture<LiberadosYaCatalogState> refreshInFlight;

    @Autowired
    public LiberadosYaCatalogService(
            LiberadosYaProperties properties,
            LiberadosYaScrapingClient scrapingClient,
            LiberadosYaCatalogStore store,
            LiberadosYaProductMatcher matcher
    ) {
        this(properties, scrapingClient, store, matcher, Clock.systemUTC(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "liberadosya-catalog-refresh");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    LiberadosYaCatalogService(
            LiberadosYaProperties properties,
            LiberadosYaScrapingClient scrapingClient,
            LiberadosYaCatalogStore store,
            LiberadosYaProductMatcher matcher,
            Clock clock,
            ExecutorService refreshExecutor,
            boolean ownsExecutor
    ) {
        this.properties = properties;
        this.scrapingClient = scrapingClient;
        this.store = store;
        this.matcher = matcher;
        this.clock = clock;
        this.refreshExecutor = refreshExecutor;
        this.ownsExecutor = ownsExecutor;
    }

    @PostConstruct
    public void initialize() {
        boolean loaded = store.loadFromDiskIfPresent();
        LiberadosYaCatalogState current = store.current();
        if (loaded && current != null && current.snapshot() != null) {
            lastSuccessfulRefreshAt.set(current.snapshot().getGeneratedAt());
        }
        if (properties.isEnabled() && properties.isInitialRefresh()) {
            LOGGER.info("LiberadosYa initial refresh scheduled. cid={} loadedSnapshot={}", TiendaPorteDiagnostics.currentCorrelationId(), loaded);
            startRefresh();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (ownsExecutor) {
            refreshExecutor.shutdownNow();
        }
    }

    @Scheduled(cron = "${liberadosya.refresh-cron:0 0 */6 * * *}")
    public void scheduledRefresh() {
        if (properties.isEnabled()) {
            startRefresh();
        }
    }

    public LiberadosYaRefreshResponse refreshFromAdmin() {
        RefreshStart refreshStart = startRefresh();
        return new LiberadosYaRefreshResponse(refreshStart.refreshStarted(), refreshStart.refreshAlreadyRunning(), status());
    }

    public LiberadosYaStatusResponse status() {
        LiberadosYaCatalogState state = store.current();
        LiberadosYaCatalogSnapshot snapshot = state == null ? null : state.snapshot();
        boolean available = state != null && state.available();
        RefreshProgress progress = refreshProgress.get();
        boolean currentlyRefreshing = isRefreshing();
        String phase = phase(progress, currentlyRefreshing);
        int progressTotal = progress.totalProducts();
        int totalProducts = progressTotal > 0 && !PHASE_IDLE.equals(phase)
                ? progressTotal
                : snapshot == null ? 0 : snapshot.getTotalProducts();
        int processedProducts = progressTotal > 0 && !PHASE_IDLE.equals(phase)
                ? progress.processedProducts()
                : snapshot == null ? 0 : snapshot.getTotalProducts();
        return new LiberadosYaStatusResponse(
                available,
                currentlyRefreshing,
                phase,
                processedProducts,
                totalProducts,
                percent(phase, processedProducts, totalProducts),
                progress.currentProduct(),
                snapshot == null ? null : snapshot.getGeneratedAt(),
                snapshot == null ? 0 : snapshot.getTotalUrlsFound(),
                snapshot == null ? 0 : snapshot.getErrorsCount(),
                lastSuccessfulRefreshAt.get(),
                lastAttemptAt.get(),
                lastError.get()
        );
    }

    public LiberadosYaProduct findMatch(String brand, String model) {
        String safeBrand = validateParam("brand", brand);
        String safeModel = validateParam("model", model);
        LiberadosYaCatalogState state = store.current();
        if (state == null || !state.available()) {
            throw new LiberadosYaCatalogNotAvailableException();
        }
        LiberadosYaMatchResult result = matcher.match(safeBrand, safeModel, state.index());
        return switch (result.status()) {
            case MATCHED -> result.product();
            case AMBIGUOUS -> throw new LiberadosYaMatchAmbiguousException();
            case NOT_FOUND -> throw new LiberadosYaProductNotFoundException();
        };
    }

    private RefreshStart startRefresh() {
        synchronized (refreshLock) {
            if (!properties.isEnabled()) {
                lastError.set("Integracion LiberadosYa deshabilitada");
                refreshProgress.set(RefreshProgress.idle());
                return RefreshStart.skipped();
            }
            if (!refreshing.compareAndSet(false, true)) {
                LOGGER.info("LiberadosYa refresh rejected by lock. cid={}", TiendaPorteDiagnostics.currentCorrelationId());
                return RefreshStart.alreadyRunning();
            }
            try {
                refreshProgress.set(RefreshProgress.starting());
                CompletableFuture<LiberadosYaCatalogState> future = CompletableFuture.supplyAsync(this::runRefreshWithCleanup, refreshExecutor);
                refreshInFlight = future;
                future.whenComplete((unused, throwable) -> clearRefresh(future));
                return RefreshStart.started();
            } catch (RuntimeException ex) {
                refreshing.set(false);
                lastError.set(sanitizeError(ex));
                refreshProgress.set(refreshProgress.get().failed());
                throw ex;
            }
        }
    }

    private LiberadosYaCatalogState runRefreshWithCleanup() {
        try {
            return refreshSnapshot();
        } finally {
            refreshing.set(false);
        }
    }

    private LiberadosYaCatalogState refreshSnapshot() {
        Instant attemptAt = clock.instant();
        long startNanos = System.nanoTime();
        lastAttemptAt.set(attemptAt);
        try {
            LOGGER.info("LiberadosYa refresh started. cid={}", TiendaPorteDiagnostics.currentCorrelationId());
            refreshProgress.set(RefreshProgress.starting());
            LiberadosYaCatalogSnapshot snapshot = scrapingClient.fetchCatalog(progressListener());
            if (snapshot.getProducts() == null || snapshot.getProducts().isEmpty()) {
                throw new LiberadosYaIntegrationException("El refresh de LiberadosYa no produjo productos validos");
            }
            updatePhase(PHASE_PERSISTING);
            LiberadosYaCatalogState state = store.replaceSnapshot(snapshot);
            lastSuccessfulRefreshAt.set(snapshot.getGeneratedAt());
            lastError.set(null);
            refreshProgress.set(RefreshProgress.completed(snapshot));
            LOGGER.info(
                    "LiberadosYa refresh finished. cid={} totalProducts={} totalUrls={} errors={} durationMs={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    snapshot.getTotalProducts(),
                    snapshot.getTotalUrlsFound(),
                    snapshot.getErrorsCount(),
                    (System.nanoTime() - startNanos) / 1_000_000
            );
            return state;
        } catch (RuntimeException ex) {
            String error = sanitizeError(ex);
            lastError.set(error);
            refreshProgress.set(refreshProgress.get().failed());
            LiberadosYaCatalogState current = store.current();
            LOGGER.error(
                    "LiberadosYa refresh failed. cid={} keepingPreviousSnapshot={} previousGeneratedAt={} exception={} cause={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    current != null && current.available(),
                    current == null || current.snapshot() == null ? null : current.snapshot().getGeneratedAt(),
                    rootCauseName(ex),
                    error,
                    ex
            );
            throw ex;
        }
    }

    private String validateParam(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new LiberadosYaBadRequestException("El parametro '" + name + "' es obligatorio");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_PARAM_LENGTH) {
            throw new LiberadosYaBadRequestException("El parametro '" + name + "' excede la longitud permitida");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new LiberadosYaBadRequestException("El parametro '" + name + "' contiene caracteres invalidos");
        }
        return trimmed;
    }

    private void clearRefresh(CompletableFuture<LiberadosYaCatalogState> future) {
        synchronized (refreshLock) {
            if (refreshInFlight == future) {
                refreshInFlight = null;
            }
        }
    }

    private CompletableFuture<LiberadosYaCatalogState> activeRefresh() {
        return refreshInFlight == null || refreshInFlight.isDone() ? null : refreshInFlight;
    }

    private boolean isRefreshing() {
        return refreshing.get();
    }

    private LiberadosYaScrapingClient.ProgressListener progressListener() {
        AtomicInteger processed = new AtomicInteger();
        return new LiberadosYaScrapingClient.ProgressListener() {
            @Override
            public void phase(String phase) {
                updatePhase(phase);
            }

            @Override
            public void productsDiscovered(int totalProducts) {
                processed.set(0);
                refreshProgress.set(new RefreshProgress(PHASE_DISCOVERING, 0, Math.max(0, totalProducts), null));
            }

            @Override
            public void productStarted(String productUrl) {
                refreshProgress.updateAndGet(progress -> progress.withPhase(PHASE_SCRAPING).withCurrentProduct(productUrl));
            }

            @Override
            public void productFinished(String productUrl) {
                int processedProducts = processed.incrementAndGet();
                refreshProgress.updateAndGet(progress -> progress
                        .withPhase(PHASE_SCRAPING)
                        .withProcessedProducts(processedProducts)
                        .withCurrentProduct(productUrl));
            }
        };
    }

    private void updatePhase(String phase) {
        String safePhase = phase == null || phase.isBlank() ? PHASE_STARTING : phase;
        refreshProgress.updateAndGet(progress -> progress.withPhase(safePhase));
    }

    private String phase(RefreshProgress progress, boolean currentlyRefreshing) {
        if (progress == null) {
            return PHASE_IDLE;
        }
        String phase = progress.phase();
        if (currentlyRefreshing && (phase == null || PHASE_IDLE.equals(phase) || PHASE_COMPLETED.equals(phase) || PHASE_FAILED.equals(phase))) {
            return PHASE_STARTING;
        }
        if (phase == null || phase.isBlank()) {
            return PHASE_IDLE;
        }
        return phase;
    }

    private int percent(String phase, int processedProducts, int totalProducts) {
        if (PHASE_COMPLETED.equals(phase)) {
            return 100;
        }
        if (totalProducts <= 0) {
            return 0;
        }
        int percent = (int) Math.floor((processedProducts * 100.0d) / totalProducts);
        return Math.max(0, Math.min(100, percent));
    }

    private String sanitizeError(Throwable throwable) {
        Throwable rootCause = TiendaPorteDiagnostics.rootCause(throwable);
        if (rootCause == null) {
            return "No se pudo actualizar el catalogo de LiberadosYa";
        }
        String message = rootCause.getMessage();
        String sanitized = looksLikeHtml(message)
                ? rootCause.getClass().getSimpleName() + ": html-redacted"
                : TiendaPorteDiagnostics.rootCauseLabel(throwable);
        sanitized = sanitized.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private boolean looksLikeHtml(String value) {
        return value != null && value.matches("(?is).*<\\s*/?\\s*[a-z][a-z0-9:-]*(\\s|>|/).*");
    }

    private String rootCauseName(Throwable throwable) {
        Throwable rootCause = TiendaPorteDiagnostics.rootCause(throwable);
        return rootCause == null ? "-" : rootCause.getClass().getSimpleName();
    }

    private record RefreshStart(boolean refreshStarted, boolean refreshAlreadyRunning) {

        private static RefreshStart started() {
            return new RefreshStart(true, false);
        }

        private static RefreshStart alreadyRunning() {
            return new RefreshStart(false, true);
        }

        private static RefreshStart skipped() {
            return new RefreshStart(false, false);
        }
    }

    private record RefreshProgress(
            String phase,
            int processedProducts,
            int totalProducts,
            String currentProduct
    ) {

        private static RefreshProgress idle() {
            return new RefreshProgress(PHASE_IDLE, 0, 0, null);
        }

        private static RefreshProgress starting() {
            return new RefreshProgress(PHASE_STARTING, 0, 0, null);
        }

        private static RefreshProgress completed(LiberadosYaCatalogSnapshot snapshot) {
            int total = snapshot == null ? 0 : Math.max(0, snapshot.getTotalUrlsFound());
            int processed = total > 0 ? total : snapshot == null ? 0 : Math.max(0, snapshot.getTotalProducts());
            return new RefreshProgress(PHASE_COMPLETED, processed, total, null);
        }

        private RefreshProgress failed() {
            return new RefreshProgress(PHASE_FAILED, processedProducts, totalProducts, currentProduct);
        }

        private RefreshProgress withPhase(String nextPhase) {
            return new RefreshProgress(nextPhase, processedProducts, totalProducts, currentProduct);
        }

        private RefreshProgress withProcessedProducts(int nextProcessedProducts) {
            return new RefreshProgress(phase, Math.max(0, nextProcessedProducts), totalProducts, currentProduct);
        }

        private RefreshProgress withCurrentProduct(String nextCurrentProduct) {
            return new RefreshProgress(phase, processedProducts, totalProducts, nextCurrentProduct);
        }
    }
}
