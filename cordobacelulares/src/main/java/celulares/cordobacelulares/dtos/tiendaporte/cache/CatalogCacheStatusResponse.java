package celulares.cordobacelulares.dtos.tiendaporte.cache;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record CatalogCacheStatusResponse(
        boolean initialized,
        boolean fresh,
        boolean stale,
        boolean refreshing,
        long version,
        int productCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant lastSuccessfulRefreshAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant lastAttemptAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant expiresAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant nextAllowedRefreshAt,
        String lastError
) {
}
