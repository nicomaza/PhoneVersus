package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaSummaryResponse {

    private BombaiComparativaMetricResponse competitorTransferMarkup;
    private BombaiComparativaMetricResponse competitorCardMarkup;
    private BombaiComparativaMetricResponse competitorImplicitRateOnMyCost;
    private BombaiComparativaMetricResponse competitorCustomerExchangeRate;
    private Integer totalProducts;
    private Integer matchedProducts;
    private Integer comparableProducts;
    private Integer mineCheaperCount;
    private BigDecimal mineCheaperPercent;
    private Integer mineMoreExpensiveCount;
    private BigDecimal mineMoreExpensivePercent;
    private Integer mixedCount;
    private Integer withoutMatchCount;
}
