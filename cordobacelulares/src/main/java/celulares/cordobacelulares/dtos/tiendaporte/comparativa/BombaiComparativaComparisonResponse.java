package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaComparisonResponse {

    private String cash;
    private String transfer;
    private String card;
    private String usd;
    private String overall;
    private Integer comparableCount;
}
