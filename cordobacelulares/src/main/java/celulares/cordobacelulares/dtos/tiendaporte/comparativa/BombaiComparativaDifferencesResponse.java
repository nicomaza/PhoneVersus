package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaDifferencesResponse {

    private BigDecimal cashArs;
    private BigDecimal transferArs;
    private BigDecimal cardArs;
    private BigDecimal usd;
}
