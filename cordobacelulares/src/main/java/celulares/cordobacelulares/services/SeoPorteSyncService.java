package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.seo.PorteProductSyncDtos;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductSpecsResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.exceptions.TiendaPorteRateLimitException;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.services.implement.CatalogProductPolicy;
import celulares.cordobacelulares.services.implement.TiendaPorteCatalogCacheService;
import celulares.cordobacelulares.services.implement.TiendaPorteClient;
import celulares.cordobacelulares.services.implement.ProductType;
import celulares.cordobacelulares.utils.SeoProductSlug;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SeoPorteSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeoPorteSyncService.class);
    private final TiendaPorteProperties properties;
    private final TiendaPorteCatalogCacheService catalogCacheService;
    private final TiendaPorteClient client;
    private final CatalogProductPolicy productPolicy;
    private final SeoCatalogProductRepository productRepository;
    private final SeoPorteContentImporter importer;
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seo-porte-sync-coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile RunProgress progress;

    public SeoPorteSyncService(
            TiendaPorteProperties properties,
            TiendaPorteCatalogCacheService catalogCacheService,
            TiendaPorteClient client,
            CatalogProductPolicy productPolicy,
            SeoCatalogProductRepository productRepository,
            SeoPorteContentImporter importer
    ) {
        this.properties = properties;
        this.catalogCacheService = catalogCacheService;
        this.client = client;
        this.productPolicy = productPolicy;
        this.productRepository = productRepository;
        this.importer = importer;
        this.workers = Executors.newFixedThreadPool(
                Math.max(1, properties.getProductSync().getMaxConcurrency()),
                runnable -> {
                    Thread thread = new Thread(runnable, "seo-porte-sync-worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );
        this.progress = RunProgress.idle();
    }

    public PorteProductSyncDtos.StartResponse startAll(String trigger) {
        return start(trigger, null);
    }

    public PorteProductSyncDtos.StartResponse startProduct(String slug) {
        String cleanSlug = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        SeoCatalogProduct product = productRepository.findBySlug(cleanSlug)
                .orElseThrow(() -> new ApiNotFoundException("Producto SEO no encontrado"));
        if (product.getOrigen() != CatalogProductOrigin.TIENDA_PORTE) {
            throw new ApiNotFoundException("El producto no proviene de Tienda Porte");
        }
        return start("MANUAL_PRODUCT", cleanSlug);
    }

    public PorteProductSyncDtos.StatusResponse status() {
        return progress.snapshot(properties.getProductSync());
    }

    @Scheduled(
            fixedDelayString = "${tiendaporte.product-sync.interval-ms:86400000}",
            initialDelayString = "${tiendaporte.product-sync.initial-delay-ms:300000}"
    )
    public void scheduledSync() {
        if (properties.getProductSync().isEnabled()) startAll("SCHEDULED");
    }

    private PorteProductSyncDtos.StartResponse start(String trigger, String onlySlug) {
        if (!running.compareAndSet(false, true)) {
            return new PorteProductSyncDtos.StartResponse(false, "Ya existe una sincronizacion en curso", status());
        }
        progress = RunProgress.started(trigger);
        coordinator.submit(() -> run(trigger, onlySlug));
        return new PorteProductSyncDtos.StartResponse(true, "Sincronizacion iniciada", status());
    }

    private void run(String trigger, String onlySlug) {
        try {
            List<ReferenceCandidate> candidates = candidates(onlySlug);
            int totalProducts = candidates.stream().mapToInt(candidate -> candidate.slugs().size()).sum();
            progress.initializeTotals(candidates.size(), totalProducts);
            int batchSize = Math.max(1, properties.getProductSync().getBatchSize());
            for (int from = 0; from < candidates.size(); from += batchSize) {
                int to = Math.min(from + batchSize, candidates.size());
                List<Callable<Void>> tasks = candidates.subList(from, to).stream()
                        .<Callable<Void>>map(candidate -> () -> {
                            synchronizeReference(candidate);
                            return null;
                        }).toList();
                List<Future<Void>> futures = workers.invokeAll(tasks);
                for (Future<Void> future : futures) future.get();
            }
            progress.finish(progress.errors() == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS", null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            progress.finish("FAILED", "Sincronizacion interrumpida");
        } catch (Exception ex) {
            LOGGER.error("Fallo la sincronizacion de fichas de Tienda Porte", ex);
            progress.finish("FAILED", safeMessage(ex));
        } finally {
            running.set(false);
        }
    }

    private void synchronizeReference(ReferenceCandidate candidate) {
        progress.current(candidate.referenceId(), candidate.slugs().isEmpty() ? null : candidate.slugs().get(0));
        Optional<TiendaPorteProductSpecsResponse> response;
        try {
            response = fetchWithRetries(candidate.referenceId());
        } catch (RuntimeException ex) {
            for (String slug : candidate.slugs()) {
                recordError(slug, candidate.referenceId(), ex);
            }
            return;
        }

        if (response.isEmpty() || incomplete(response.get())) {
            for (String slug : candidate.slugs()) {
                importer.recordMissingSheet(slug, candidate.referenceId(), "SIN_FICHA_EN_PORTE", null);
                progress.withoutSheet();
            }
            return;
        }

        for (String slug : candidate.slugs()) {
            progress.current(candidate.referenceId(), slug);
            try {
                SeoPorteContentImporter.ImportOutcome outcome = importer.importProduct(
                        slug, candidate.referenceId(), response.get(), candidate.imageUrls()
                );
                progress.success(outcome);
            } catch (RuntimeException ex) {
                recordError(slug, candidate.referenceId(), ex);
            }
        }
    }

    private void recordError(String slug, long referenceId, RuntimeException ex) {
        String message = safeMessage(ex);
        try {
            importer.recordMissingSheet(slug, referenceId, "ERROR", message);
        } catch (RuntimeException persistenceError) {
            LOGGER.error("No se pudo registrar el error de sincronizacion para slug={}", slug, persistenceError);
        }
        LOGGER.warn("No se pudo sincronizar ficha de Porte. referenceId={} slug={} error={}", referenceId, slug, message);
        progress.error(message);
    }

    private Optional<TiendaPorteProductSpecsResponse> fetchWithRetries(long referenceId) {
        int attempts = Math.max(1, properties.getProductSync().getMaxRetries() + 1);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return client.getProductSpecs(referenceId);
            } catch (TiendaPorteRateLimitException ex) {
                last = ex;
                if (attempt < attempts) sleepUntil(ex.getNextAllowedRefreshAt());
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt < attempts) sleep(Duration.ofSeconds(attempt));
            }
        }
        throw last;
    }

    private List<ReferenceCandidate> candidates(String onlySlug) {
        List<TiendaPorteExternalProduct> external = catalogCacheService.getProducts();
        Map<String, SeoCatalogProduct> seoBySlug = new HashMap<>();
        for (SeoCatalogProduct product : productRepository.findAll()) {
            seoBySlug.put(product.getSlug(), product);
        }

        Map<String, Set<Long>> referenceIdsBySlug = new HashMap<>();
        Map<Long, CandidateBuilder> byReference = new LinkedHashMap<>();
        for (TiendaPorteExternalProduct item : external) {
            TiendaPorteProductReference reference = item == null ? null : item.getProductReference();
            if (reference == null || reference.getId() == null || reference.getId() <= 0) continue;
            if (productPolicy.resolveProductType(item, null) != ProductType.PHONE) continue;
            String model = firstNonBlank(reference.getName(), item.getName());
            String slug = SeoProductSlug.fromModel(model);
            SeoCatalogProduct seo = seoBySlug.get(slug);
            if (seo == null || seo.getOrigen() != CatalogProductOrigin.TIENDA_PORTE) continue;
            if (onlySlug != null && !onlySlug.equals(slug)) continue;

            referenceIdsBySlug.computeIfAbsent(slug, ignored -> new LinkedHashSet<>()).add(reference.getId());
            CandidateBuilder builder = byReference.computeIfAbsent(reference.getId(), CandidateBuilder::new);
            builder.slugs.add(slug);
            if (reference.getImages() != null) builder.imageUrls.addAll(reference.getImages());
        }

        Set<String> ambiguous = new LinkedHashSet<>();
        referenceIdsBySlug.forEach((slug, ids) -> {
            if (ids.size() > 1) ambiguous.add(slug);
        });
        if (onlySlug != null && ambiguous.contains(onlySlug)) {
            throw new IllegalStateException("La asociacion con Tienda Porte es ambigua para " + onlySlug);
        }

        List<ReferenceCandidate> result = new ArrayList<>();
        for (CandidateBuilder builder : byReference.values()) {
            List<String> slugs = builder.slugs.stream().filter(slug -> !ambiguous.contains(slug)).toList();
            if (!slugs.isEmpty()) {
                result.add(new ReferenceCandidate(builder.referenceId, slugs, builder.imageUrls.stream().toList()));
            }
        }
        if (onlySlug != null && result.isEmpty()) {
            throw new ApiNotFoundException("No se encontro una referencia unica de Tienda Porte para el producto");
        }
        return result;
    }

    private boolean incomplete(TiendaPorteProductSpecsResponse response) {
        if (response == null || response.getSheet() == null) return true;
        boolean noHighlights = response.getSheet().getHighlights() == null || response.getSheet().getHighlights().isEmpty();
        boolean noSections = response.getSheet().getSections() == null || response.getSheet().getSections().isEmpty();
        return noHighlights && noSections;
    }

    private void sleepUntil(Instant target) {
        Duration delay = target == null ? Duration.ofSeconds(1) : Duration.between(Instant.now(), target);
        sleep(delay.isNegative() || delay.isZero() ? Duration.ofMillis(1) : delay);
    }

    private void sleep(Duration delay) {
        try {
            Thread.sleep(Math.max(1, delay.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sincronizacion interrumpida", ex);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) return "Error no especificado";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    @PreDestroy
    public void shutdown() {
        coordinator.shutdownNow();
        workers.shutdownNow();
    }

    private record ReferenceCandidate(long referenceId, List<String> slugs, List<String> imageUrls) {}

    private static class CandidateBuilder {
        private final long referenceId;
        private final Set<String> slugs = new LinkedHashSet<>();
        private final Set<String> imageUrls = new LinkedHashSet<>();
        private CandidateBuilder(long referenceId) { this.referenceId = referenceId; }
    }

    private static class RunProgress {
        private boolean running;
        private String state;
        private String trigger;
        private Instant startedAt;
        private Instant finishedAt;
        private int totalReferences;
        private int totalProducts;
        private int processedProducts;
        private int imported;
        private int updated;
        private int unchanged;
        private int withoutSheet;
        private int errors;
        private Long currentReferenceId;
        private String currentSlug;
        private String lastError;

        static RunProgress idle() {
            RunProgress value = new RunProgress();
            value.state = "IDLE";
            return value;
        }

        static RunProgress started(String trigger) {
            RunProgress value = new RunProgress();
            value.running = true;
            value.state = "RUNNING";
            value.trigger = trigger;
            value.startedAt = Instant.now();
            return value;
        }

        synchronized void initializeTotals(int references, int products) {
            totalReferences = references;
            totalProducts = products;
        }

        synchronized void current(long referenceId, String slug) {
            currentReferenceId = referenceId;
            currentSlug = slug;
        }

        synchronized void success(SeoPorteContentImporter.ImportOutcome outcome) {
            processedProducts++;
            switch (outcome) {
                case IMPORTED -> imported++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }

        synchronized void withoutSheet() { processedProducts++; withoutSheet++; }
        synchronized void error(String message) { processedProducts++; errors++; lastError = message; }
        synchronized int errors() { return errors; }

        synchronized void finish(String finalState, String error) {
            running = false;
            state = finalState;
            finishedAt = Instant.now();
            currentReferenceId = null;
            currentSlug = null;
            if (error != null) lastError = error;
        }

        synchronized PorteProductSyncDtos.StatusResponse snapshot(TiendaPorteProperties.ProductSync config) {
            return new PorteProductSyncDtos.StatusResponse(
                    running, state, trigger, startedAt, finishedAt, totalReferences, totalProducts,
                    processedProducts, imported, updated, unchanged, withoutSheet, errors,
                    currentReferenceId, currentSlug, lastError, config.isEnabled(), config.getIntervalMs(),
                    config.getInitialDelayMs(), config.getBatchSize(), config.getMaxConcurrency(), config.getMaxRetries()
            );
        }
    }
}
