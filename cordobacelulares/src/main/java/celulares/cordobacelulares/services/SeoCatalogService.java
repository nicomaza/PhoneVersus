package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.SeoCatalogProductResponse;
import celulares.cordobacelulares.dtos.seo.SeoCatalogSyncResult;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import celulares.cordobacelulares.utils.SeoProductSlug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SeoCatalogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeoCatalogService.class);

    private final SeoCatalogProductRepository repository;
    private final Clock clock;

    @Autowired
    public SeoCatalogService(SeoCatalogProductRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SeoCatalogService(SeoCatalogProductRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SeoCatalogSyncResult synchronize(
            List<TiendaPorteBrandResponse> catalog,
            boolean allowDeactivation
    ) {
        List<CatalogCandidate> candidates = flatten(catalog);
        if (candidates.isEmpty()) {
            LOGGER.warn("SEO catalog sync skipped: unified catalog is empty");
            return SeoCatalogSyncResult.skippedResult();
        }

        Map<String, List<CatalogCandidate>> candidatesBySlug = groupBySlug(candidates);
        List<SeoCatalogProduct> historical = repository.findAll();
        Map<String, SeoCatalogProduct> historicalBySlug = new LinkedHashMap<>();
        for (SeoCatalogProduct product : historical) {
            historicalBySlug.putIfAbsent(product.getSlug(), product);
        }

        int collisions = logAndCountCollisions(candidatesBySlug);
        int created = 0;
        int reactivated = 0;
        int deactivated = 0;
        Instant syncAt = clock.instant();
        List<SeoCatalogProduct> newProducts = new ArrayList<>();
        Set<String> currentSlugs = new LinkedHashSet<>(candidatesBySlug.keySet());

        for (Map.Entry<String, List<CatalogCandidate>> entry : candidatesBySlug.entrySet()) {
            String slug = entry.getKey();
            SeoCatalogProduct existing = historicalBySlug.get(slug);
            CatalogCandidate candidate = chooseCandidate(entry.getValue(), existing);

            if (existing == null) {
                SeoCatalogProduct createdProduct = new SeoCatalogProduct(
                        null,
                        slug,
                        candidate.brand(),
                        candidate.model(),
                        candidate.origin(),
                        syncAt,
                        syncAt,
                        true
                );
                newProducts.add(createdProduct);
                historicalBySlug.put(slug, createdProduct);
                created++;
                continue;
            }

            if (!existing.isActive()) {
                reactivated++;
            }
            existing.setMarca(candidate.brand());
            existing.setModelo(candidate.model());
            existing.setOrigen(candidate.origin());
            existing.setLastSeenAt(syncAt);
            existing.setActive(true);
        }

        if (allowDeactivation) {
            for (SeoCatalogProduct product : historical) {
                if (product.isActive() && !currentSlugs.contains(product.getSlug())) {
                    product.setActive(false);
                    deactivated++;
                }
            }
        } else {
            LOGGER.warn("SEO catalog sync completed without deactivation because the unified snapshot was not fully verifiable");
        }

        if (!newProducts.isEmpty()) {
            repository.saveAll(newProducts);
        }

        long historicalCount = historical.size() + newProducts.size();
        long activeCount = Stream.concat(historical.stream(), newProducts.stream())
                .filter(SeoCatalogProduct::isActive)
                .count();

        SeoCatalogSyncResult result = new SeoCatalogSyncResult(
                candidates.size(),
                created,
                reactivated,
                deactivated,
                collisions,
                historicalCount,
                activeCount,
                allowDeactivation,
                false
        );
        LOGGER.info(
                "SEO catalog synchronized: found={} created={} reactivated={} deactivated={} collisions={} historical={} active={} deactivationApplied={}",
                result.found(),
                result.created(),
                result.reactivated(),
                result.deactivated(),
                result.slugCollisions(),
                result.historicalCount(),
                result.activeCount(),
                result.deactivationApplied()
        );
        return result;
    }

    @Transactional(readOnly = true)
    public SeoCatalogProductResponse findBySlug(String slug) {
        String cleanSlug = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        SeoCatalogProduct product = repository.findBySlug(cleanSlug)
                .orElseThrow(() -> new ApiNotFoundException("El producto solicitado nunca existio en el catalogo"));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<String> activeSlugs() {
        return repository.findAllByActiveTrueOrderBySlugAsc().stream()
                .map(SeoCatalogProduct::getSlug)
                .toList();
    }

    @Transactional(readOnly = true)
    public long historicalCount() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return repository.countByActiveTrue();
    }

    private List<CatalogCandidate> flatten(List<TiendaPorteBrandResponse> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }

        List<CatalogCandidate> candidates = new ArrayList<>();
        for (TiendaPorteBrandResponse brandResponse : catalog) {
            if (brandResponse == null) {
                continue;
            }
            String brand = clean(brandResponse.getMarca());
            if (brand.isBlank()) {
                brand = "Sin marca";
            }
            List<TiendaPorteModelResponse> models = brandResponse.getModelos();
            if (models == null) {
                continue;
            }
            for (TiendaPorteModelResponse modelResponse : models) {
                if (modelResponse == null) {
                    continue;
                }
                String model = clean(modelResponse.getModeloNombre());
                String slug = SeoProductSlug.fromModel(model);
                if (!model.isBlank() && !slug.isBlank()) {
                    candidates.add(new CatalogCandidate(slug, brand, model, modelResponse.getOrigen()));
                }
            }
        }
        return candidates;
    }

    private Map<String, List<CatalogCandidate>> groupBySlug(List<CatalogCandidate> candidates) {
        Map<String, List<CatalogCandidate>> grouped = new LinkedHashMap<>();
        for (CatalogCandidate candidate : candidates) {
            grouped.computeIfAbsent(candidate.slug(), ignored -> new ArrayList<>()).add(candidate);
        }
        return grouped;
    }

    private int logAndCountCollisions(Map<String, List<CatalogCandidate>> candidatesBySlug) {
        int collisions = 0;
        for (Map.Entry<String, List<CatalogCandidate>> entry : candidatesBySlug.entrySet()) {
            Map<String, CatalogCandidate> distinctProducts = new LinkedHashMap<>();
            for (CatalogCandidate candidate : entry.getValue()) {
                distinctProducts.putIfAbsent(candidate.identity(), candidate);
            }
            if (distinctProducts.size() <= 1) {
                continue;
            }
            collisions++;
            LOGGER.warn(
                    "SEO slug collision detected: slug={} products={}",
                    entry.getKey(),
                    distinctProducts.values().stream()
                            .map(candidate -> candidate.brand() + " | " + candidate.model())
                            .toList()
            );
        }
        return collisions;
    }

    private CatalogCandidate chooseCandidate(
            List<CatalogCandidate> candidates,
            SeoCatalogProduct existing
    ) {
        if (existing != null) {
            String historicalIdentity = identity(existing.getMarca(), existing.getModelo());
            for (CatalogCandidate candidate : candidates) {
                if (candidate.identity().equals(historicalIdentity)) {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    private SeoCatalogProductResponse toResponse(SeoCatalogProduct product) {
        return new SeoCatalogProductResponse(
                product.getSlug(),
                product.getMarca(),
                product.getModelo(),
                product.isActive()
        );
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String identity(String brand, String model) {
        return normalizeIdentity(brand) + "|" + normalizeIdentity(model);
    }

    private static String normalizeIdentity(String value) {
        return (value == null ? "" : value)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private record CatalogCandidate(
            String slug,
            String brand,
            String model,
            CatalogProductOrigin origin
    ) {
        private String identity() {
            return SeoCatalogService.identity(brand, model);
        }
    }
}
