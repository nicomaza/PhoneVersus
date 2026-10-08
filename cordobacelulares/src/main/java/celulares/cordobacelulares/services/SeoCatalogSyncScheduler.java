package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.SeoCatalogSyncResult;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheStatusResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.services.implement.TiendaPorteCatalogCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SeoCatalogSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeoCatalogSyncScheduler.class);

    private final TiendaPorteCatalogCacheService cacheService;
    private final TiendaPorteService tiendaPorteService;
    private final SeoCatalogService seoCatalogService;
    private final boolean supplierSheetEnabled;
    private final AtomicLong lastSynchronizedVersion = new AtomicLong();

    public SeoCatalogSyncScheduler(
            TiendaPorteCatalogCacheService cacheService,
            TiendaPorteService tiendaPorteService,
            SeoCatalogService seoCatalogService,
            @Value("${supplier-sheet.enabled:true}") boolean supplierSheetEnabled
    ) {
        this.cacheService = cacheService;
        this.tiendaPorteService = tiendaPorteService;
        this.seoCatalogService = seoCatalogService;
        this.supplierSheetEnabled = supplierSheetEnabled;
    }

    @Scheduled(
            fixedDelayString = "${seo.catalog-sync.poll-ms:15000}",
            initialDelayString = "${seo.catalog-sync.initial-delay-ms:10000}"
    )
    public synchronized void synchronizeFreshCatalog() {
        CatalogCacheStatusResponse status = cacheService.status();
        if (!isSafeSnapshot(status) || status.version() <= lastSynchronizedVersion.get()) {
            return;
        }

        try {
            List<TiendaPorteBrandResponse> catalog = tiendaPorteService.getAllowedCategories(null, null);
            if (catalog == null || catalog.isEmpty()) {
                LOGGER.warn(
                        "SEO catalog sync not applied: unified catalog is empty for cache version={}",
                        status.version()
                );
                return;
            }

            boolean supplierSnapshotPresent = containsSupplierSheetProducts(catalog);
            boolean allowDeactivation = !supplierSheetEnabled || supplierSnapshotPresent;
            if (!allowDeactivation) {
                LOGGER.warn(
                        "SEO catalog sync will preserve missing historical products: Supplier Sheet is enabled but no GOOGLE_SHEET product was present"
                );
            }

            SeoCatalogSyncResult result = seoCatalogService.synchronize(catalog, allowDeactivation);
            if (!result.skipped()) {
                lastSynchronizedVersion.set(status.version());
            }
        } catch (RuntimeException ex) {
            LOGGER.error(
                    "SEO catalog sync failed for cache version={}; historical activation state was preserved",
                    status.version(),
                    ex
            );
        }
    }

    private boolean isSafeSnapshot(CatalogCacheStatusResponse status) {
        return status != null
                && status.initialized()
                && status.fresh()
                && !status.refreshing()
                && status.productCount() > 0
                && status.lastError() == null;
    }

    private boolean containsSupplierSheetProducts(List<TiendaPorteBrandResponse> catalog) {
        return catalog.stream()
                .filter(brand -> brand != null && brand.getModelos() != null)
                .flatMap(brand -> brand.getModelos().stream())
                .anyMatch(model -> model != null && model.getOrigen() == CatalogProductOrigin.GOOGLE_SHEET);
    }
}
