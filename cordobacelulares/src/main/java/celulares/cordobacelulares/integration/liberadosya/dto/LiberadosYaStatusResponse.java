package celulares.cordobacelulares.integration.liberadosya.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LiberadosYaStatusResponse(
        boolean available,
        boolean refreshing,
        String phase,
        int processedProducts,
        int totalProducts,
        int percent,
        String currentProduct,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant generatedAt,
        int totalUrlsFound,
        int errorsCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant lastSuccessfulRefreshAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant lastAttemptAt,
        String lastError
) {
}
