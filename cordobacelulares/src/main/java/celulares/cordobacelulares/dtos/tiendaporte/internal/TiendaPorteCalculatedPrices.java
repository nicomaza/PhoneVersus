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
    private BigDecimal precioTarjeta1Pago;
    private BigDecimal precioQr1Pago;
    private BigDecimal precioPlanZ3Pagos;
    private BigDecimal precioTarjeta3Pagos;
    private BigDecimal precioTarjeta6Pagos;
    private BigDecimal precioTarjeta12Pagos;

    public TiendaPorteCalculatedPrices(
            BigDecimal precioUsd,
            BigDecimal precioPesos,
            BigDecimal precioUsdt,
            BigDecimal precioTransferenciaBancaria,
            BigDecimal precioTarjeta3Pagos,
            BigDecimal precioTarjeta6Pagos,
            BigDecimal precioTarjeta12Pagos
    ) {
        this(
                precioUsd,
                precioPesos,
                precioUsdt,
                precioTransferenciaBancaria,
                null,
                null,
                null,
                precioTarjeta3Pagos,
                precioTarjeta6Pagos,
                precioTarjeta12Pagos
        );
    }
}
