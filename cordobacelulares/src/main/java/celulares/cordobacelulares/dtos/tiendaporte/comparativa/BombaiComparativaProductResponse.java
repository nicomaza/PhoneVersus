package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaProductResponse {

    private String brand;
    private String model;
    private String fullName;
    private String origin;
    private Integer colorCount;
    private String matchStatus;
    private String matchMessage;
    private BombaiComparativaMinePricesResponse mine;
    private BombaiComparativaCompetitorPricesResponse competitor;
    private BombaiComparativaDifferencesResponse differences;
    private BombaiComparativaComparisonResponse comparison;
}
