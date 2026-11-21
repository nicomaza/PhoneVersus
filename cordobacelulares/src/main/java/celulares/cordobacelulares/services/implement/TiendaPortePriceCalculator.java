package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.internal.TiendaPorteCalculatedPrices;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class TiendaPortePriceCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public TiendaPorteCalculatedPrices calculate(
            BigDecimal precioUsd,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado
    ) {
        return calculate(precioUsd, null, configuration, dolarBilleteAplicado);
    }

    public TiendaPorteCalculatedPrices calculate(
            BigDecimal precioUsd,
            BigDecimal precioPesos,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado
    ) {
        validateConfiguration(configuration);
        BigDecimal normalizedUsd = normalizeUsd(precioUsd);
        BigDecimal precioBasePesos = resolvePrecioBasePesos(precioPesos, normalizedUsd, dolarBilleteAplicado);

        return new TiendaPorteCalculatedPrices(
                normalizedUsd,
                precioBasePesos,
                calculatePriceFromUsd(normalizedUsd, configuration == null ? null : configuration.getUsdt()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getTransferenciaBancaria()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getTarjeta1Pago()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getQr1Pago()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getPlanZ3Pagos()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getTarjeta3Pagos()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getTarjeta6Pagos()),
                calcularPrecioConRecargo(precioBasePesos, configuration == null ? null : configuration.getTarjeta12Pagos())
        );
    }

    public BigDecimal calcularPrecioConRecargo(BigDecimal precioBase, BigDecimal porcentaje) {
        if (precioBase == null) {
            return null;
        }
        if (precioBase.signum() < 0) {
            return ZERO;
        }
        BigDecimal normalizedBase = precioBase.setScale(2, RoundingMode.HALF_UP);
        if (porcentaje == null || porcentaje.signum() <= 0) {
            return normalizedBase;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(porcentaje.divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
        return normalizedBase.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeUsd(BigDecimal precioUsd) {
        if (precioUsd == null || precioUsd.signum() <= 0) {
            return ZERO;
        }
        return precioUsd.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePrecioBasePesos(BigDecimal precioPesos, BigDecimal precioUsd, BigDecimal dolarBilleteAplicado) {
        BigDecimal normalizedPesos = normalizePrecioPesos(precioPesos);
        if (normalizedPesos != null) {
            return normalizedPesos;
        }
        if (precioUsd.signum() == 0) {
            return null;
        }
        BigDecimal quotation = normalizeQuotation(dolarBilleteAplicado);
        if (quotation == null) {
            return null;
        }
        return precioUsd.multiply(quotation).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePrecioPesos(BigDecimal precioPesos) {
        if (precioPesos == null || precioPesos.signum() < 0) {
            return null;
        }
        return precioPesos.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePriceFromUsd(BigDecimal precioUsd, BigDecimal quotation) {
        BigDecimal safeQuotation = normalizeQuotation(quotation);
        if (precioUsd.signum() == 0 || safeQuotation == null) {
            return ZERO;
        }
        return precioUsd.multiply(safeQuotation).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeQuotation(BigDecimal quotation) {
        if (quotation == null || quotation.signum() <= 0) {
            return null;
        }
        return quotation;
    }

    private void validateConfiguration(PriceConfiguration configuration) {
        if (configuration == null) {
            return;
        }
        validatePercentage("transferenciaBancaria", configuration.getTransferenciaBancaria());
        validatePercentage("tarjeta1Pago", configuration.getTarjeta1Pago());
        validatePercentage("qr1Pago", configuration.getQr1Pago());
        validatePercentage("planZ3Pagos", configuration.getPlanZ3Pagos());
        validatePercentage("tarjeta3Pagos", configuration.getTarjeta3Pagos());
        validatePercentage("tarjeta6Pagos", configuration.getTarjeta6Pagos());
        validatePercentage("tarjeta12Pagos", configuration.getTarjeta12Pagos());
    }

    private void validatePercentage(String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new TiendaPorteBadRequestException("Configuracion invalida: " + field + " no puede ser negativo");
        }
    }

}
