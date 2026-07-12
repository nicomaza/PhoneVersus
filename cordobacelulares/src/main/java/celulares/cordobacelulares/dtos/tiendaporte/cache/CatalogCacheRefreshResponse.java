package celulares.cordobacelulares.dtos.tiendaporte.cache;

public record CatalogCacheRefreshResponse(
        boolean refreshStarted,
        boolean alreadyRunning,
        boolean blockedByCooldown,
        CatalogCacheStatusResponse status
) {
}
