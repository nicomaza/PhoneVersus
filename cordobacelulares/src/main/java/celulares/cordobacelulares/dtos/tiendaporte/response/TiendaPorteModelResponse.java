package celulares.cordobacelulares.dtos.tiendaporte.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TiendaPorteModelResponse {

    private String modeloNombre;
    private CatalogProductOrigin origen;
    private List<TiendaPorteColorStockResponse> colores;
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

    public TiendaPorteModelResponse(
            String modeloNombre,
            CatalogProductOrigin origen,
            List<TiendaPorteColorStockResponse> colores,
            BigDecimal precioUsd,
            BigDecimal precioPesos,
            BigDecimal precioUsdt,
            BigDecimal precioTransferenciaBancaria,
            BigDecimal precioTarjeta3Pagos,
            BigDecimal precioTarjeta6Pagos,
            BigDecimal precioTarjeta12Pagos
    ) {
        this(
                modeloNombre,
                origen,
                colores,
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
