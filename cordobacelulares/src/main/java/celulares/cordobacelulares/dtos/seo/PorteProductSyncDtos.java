package celulares.cordobacelulares.dtos.seo;

import java.time.Instant;

public final class PorteProductSyncDtos {

    private PorteProductSyncDtos() {}

    public record StartResponse(boolean started, String message, StatusResponse status) {}

    public record StatusResponse(
            boolean running,
            String state,
            String trigger,
            Instant startedAt,
            Instant finishedAt,
            int totalReferences,
            int totalProducts,
            int processedProducts,
            int imported,
            int updated,
            int unchanged,
            int withoutSheet,
            int errors,
            Long currentReferenceId,
            String currentSlug,
            String lastError,
            boolean automaticEnabled,
            long automaticIntervalMs,
            long automaticInitialDelayMs,
            int batchSize,
            int maxConcurrency,
            int maxRetries
    ) {}
}
