package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.SeoCatalogSyncResult;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheStatusResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.services.implement.TiendaPorteCatalogCacheService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoCatalogSyncSchedulerTest {

    private final TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
    private final TiendaPorteService tiendaPorteService = mock(TiendaPorteService.class);
    private final SeoCatalogService seoCatalogService = mock(SeoCatalogService.class);

    @Test
    void doesNotRequestUnifiedCatalogWithoutSafeCachedSnapshot() {
        when(cacheService.status()).thenReturn(status(false, false, 0, 0, "provider error"));
        SeoCatalogSyncScheduler scheduler = new SeoCatalogSyncScheduler(
                cacheService,
                tiendaPorteService,
                seoCatalogService,
                true
        );

        scheduler.synchronizeFreshCatalog();

        verify(tiendaPorteService, never()).getAllowedCategories(null, null);
    }

    @Test
    void synchronizesEachFreshCacheVersionOnlyOnce() {
        when(cacheService.status()).thenReturn(status(true, true, 7, 20, null));
        when(tiendaPorteService.getAllowedCategories(null, null)).thenReturn(catalog(CatalogProductOrigin.GOOGLE_SHEET));
        when(seoCatalogService.synchronize(anyList(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(new SeoCatalogSyncResult(1, 1, 0, 0, 0, 1, 1, true, false));
        SeoCatalogSyncScheduler scheduler = new SeoCatalogSyncScheduler(
                cacheService,
                tiendaPorteService,
                seoCatalogService,
                true
        );

        scheduler.synchronizeFreshCatalog();
        scheduler.synchronizeFreshCatalog();

        verify(tiendaPorteService, times(1)).getAllowedCategories(null, null);
        verify(seoCatalogService, times(1)).synchronize(anyList(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void missingSupplierSnapshotPreservesHistoricalActivationState() {
        when(cacheService.status()).thenReturn(status(true, true, 8, 20, null));
        when(tiendaPorteService.getAllowedCategories(null, null)).thenReturn(catalog(CatalogProductOrigin.TIENDA_PORTE));
        when(seoCatalogService.synchronize(anyList(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(new SeoCatalogSyncResult(1, 1, 0, 0, 0, 1, 1, false, false));
        SeoCatalogSyncScheduler scheduler = new SeoCatalogSyncScheduler(
                cacheService,
                tiendaPorteService,
                seoCatalogService,
                true
        );

        scheduler.synchronizeFreshCatalog();

        verify(seoCatalogService).synchronize(anyList(), org.mockito.ArgumentMatchers.eq(false));
    }

    private CatalogCacheStatusResponse status(
            boolean initialized,
            boolean fresh,
            long version,
            int productCount,
            String lastError
    ) {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        return new CatalogCacheStatusResponse(
                initialized,
                fresh,
                initialized && !fresh,
                false,
                version,
                productCount,
                initialized ? now : null,
                now,
                initialized ? now.plusSeconds(180) : null,
                null,
                lastError
        );
    }

    private List<TiendaPorteBrandResponse> catalog(CatalogProductOrigin origin) {
        TiendaPorteModelResponse model = new TiendaPorteModelResponse();
        model.setModeloNombre("POCO C81 PRO 4GB 128GB");
        model.setOrigen(origin);
        return List.of(new TiendaPorteBrandResponse("XIAOMI", List.of(model)));
    }
}
