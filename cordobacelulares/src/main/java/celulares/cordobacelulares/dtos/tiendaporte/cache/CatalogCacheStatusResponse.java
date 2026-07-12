package celulares.cordobacelulares.dtos.tiendaporte.cache;

import java.time.Instant;

public record CatalogCacheStatusResponse(
        boolean initialized,
        boolean fresh,
        boolean stale,
        boolean refreshing,
        long version,
        int productCount,
        Instant lastSuccessfulRefreshAt,
        Instant lastAttemptAt,
        Instant expiresAt,
        Instant nextAllowedRefreshAt,
        String lastError
) {
}
