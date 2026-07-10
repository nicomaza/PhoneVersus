package celulares.cordobacelulares.dtos.tiendaporte.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TiendaPorteCalculatedPrices {

    private BigDecimal precioUsd;
    private BigDecimal precioPesos;
    private BigDecimal precioUsdt;
    private BigDecimal precioTransferenciaBancaria;
    private BigDecimal precioTarjeta3Pagos;
    private BigDecimal precioTarjeta6Pagos;
    private BigDecimal precioTarjeta12Pagos;
}
