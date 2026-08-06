package celulares.cordobacelulares.integration.liberadosya.exception;

import java.time.Instant;

public class LiberadosYaRateLimitException extends LiberadosYaIntegrationException {

    private final Instant retryAfter;

    public LiberadosYaRateLimitException(String message, Instant retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Instant getRetryAfter() {
        return retryAfter;
    }
}
