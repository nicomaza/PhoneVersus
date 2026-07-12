package celulares.cordobacelulares.dtos.tiendaporte.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCatalogProductRowResponse {

    private Long id;
    private String marca;
    private String modelo;
    private String color;
    private Integer cantidad;
    private String origen;
    private BigDecimal precioUsd;
    private BigDecimal precioPesos;
    private BigDecimal precioTransferenciaBancaria;
    private BigDecimal precioTarjeta3Pagos;
    private BigDecimal precioTarjeta6Pagos;
    private BigDecimal precioTarjeta12Pagos;
}
