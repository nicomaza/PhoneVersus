package celulares.cordobacelulares.dtos.priceconfiguration;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceConfigurationCreateRequest {

    @Schema(description = "Cotizacion dolar billete usada para convertir precios en dolares a pesos.", example = "1200.00")
    private BigDecimal dolarBillete;

    @Schema(description = "Cotizacion USDT usada para calcular precioUsdt desde el precio en dolares.", example = "1210.00")
    private BigDecimal usdt;

    @Schema(description = "Porcentaje de recargo sobre precioPesos para transferencia bancaria. Ejemplo: 3 representa 3 por ciento.", example = "3")
    private BigDecimal transferenciaBancaria;

    @Schema(description = "Porcentaje de recargo sobre precioPesos para tarjeta en 3 pagos. Ejemplo: 10 representa 10 por ciento.", example = "10")
    private BigDecimal tarjeta3Pagos;

    @Schema(description = "Porcentaje de recargo sobre precioPesos para tarjeta en 6 pagos. Ejemplo: 20 representa 20 por ciento.", example = "20")
    private BigDecimal tarjeta6Pagos;

    @Schema(description = "Porcentaje de recargo sobre precioPesos para tarjeta en 12 pagos. Ejemplo: 35 representa 35 por ciento.", example = "35")
    private BigDecimal tarjeta12Pagos;
}
