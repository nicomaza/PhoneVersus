package celulares.cordobacelulares.dtos.tiendaporte.comparativa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BombaiComparativaCompetitorPricesResponse {

    private Boolean matched;
    private String name;
    private BigDecimal cashArs;
    private BigDecimal currentArs;
    private BigDecimal listArs;
    private BigDecimal transferArs;
    private BigDecimal cardArs;
    private BigDecimal cashUsd;
    private BigDecimal currentUsd;
    private BigDecimal listUsd;
    private BigDecimal mainUsd;
}
