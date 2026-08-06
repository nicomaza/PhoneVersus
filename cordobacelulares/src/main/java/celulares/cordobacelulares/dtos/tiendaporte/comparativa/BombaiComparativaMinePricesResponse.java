package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaMinePricesResponse {

    private BigDecimal costUsd;
    private BigDecimal priceUsd;
    private BigDecimal cashArs;
    private BigDecimal transferArs;
    private BigDecimal cardArs;
}
