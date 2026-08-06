package celulares.cordobacelulares.integration.liberadosya.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LiberadosYaRefreshResponse(
        boolean refreshStarted,
        boolean alreadyRunning,
        LiberadosYaStatusResponse status
) {
}
