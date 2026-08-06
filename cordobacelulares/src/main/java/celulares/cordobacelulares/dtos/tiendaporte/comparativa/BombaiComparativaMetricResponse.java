package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaMetricResponse {

    private BigDecimal average;
    private BigDecimal median;
    private BigDecimal min;
    private BigDecimal max;
    private Integer count;
}
