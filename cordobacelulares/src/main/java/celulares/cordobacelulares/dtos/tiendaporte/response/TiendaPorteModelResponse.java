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
    private List<TiendaPorteColorStockResponse> colores;
    private BigDecimal precioUsd;
    private BigDecimal precioPesos;
    private BigDecimal precioUsdt;
    private BigDecimal precioTransferenciaBancaria;
    private BigDecimal precioTarjeta3Pagos;
    private BigDecimal precioTarjeta6Pagos;
    private BigDecimal precioTarjeta12Pagos;
}
