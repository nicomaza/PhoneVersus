package celulares.cordobacelulares.integration.liberadosya.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LiberadosYaQuotationResponse(
        Instant obtainedAt,
        Instant lastAttemptAt,
        UpdateStatus updateStatus,
        boolean stale,
        String sourceAppJsUrl,
        BigDecimal dolarBillete,
        BigDecimal usdt,
        MarkupByTransferScheme transferencia,
        MarkupByTransferScheme tarjeta1Pago,
        MarkupByTransferScheme qr1Pago,
        MarkupByTransferScheme planZ3Pagos,
        MarkupByTransferScheme tarjeta3Pagos,
        MarkupByTransferScheme tarjeta6Pagos,
        String errorMessage
) {

    public enum UpdateStatus {
        CURRENT,
        STALE,
        UNAVAILABLE
    }

    public record MarkupByTransferScheme(
            BigDecimal general,
            BigDecimal net7Net10
    ) {
    }
}
