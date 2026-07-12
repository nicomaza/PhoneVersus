package celulares.cordobacelulares.exceptions;

import java.time.Instant;

public class TiendaPorteRateLimitException extends TiendaPorteIntegrationException {

    private final int statusCode;
    private final Instant retryAfter;
    private final Instant nextAllowedRefreshAt;

    public TiendaPorteRateLimitException(String message, int statusCode, Instant retryAfter, Instant nextAllowedRefreshAt) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
        this.nextAllowedRefreshAt = nextAllowedRefreshAt;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Instant getRetryAfter() {
        return retryAfter;
    }

    public Instant getNextAllowedRefreshAt() {
        return nextAllowedRefreshAt;
    }
}
