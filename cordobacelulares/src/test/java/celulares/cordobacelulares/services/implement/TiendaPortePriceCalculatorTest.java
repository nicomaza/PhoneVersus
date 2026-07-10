package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.internal.TiendaPorteCalculatedPrices;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TiendaPortePriceCalculatorTest {

    private final TiendaPortePriceCalculator calculator = new TiendaPortePriceCalculator();

    @Test
    void calculatesThreePercentSurcharge() {
        BigDecimal result = calculator.calcularPrecioConRecargo(money("100"), money("3"));

        assertThat(result).isEqualByComparingTo("103.00");
    }

    @Test
    void calculatesTenPercentSurcharge() {
        BigDecimal result = calculator.calcularPrecioConRecargo(money("1000"), money("10"));

        assertThat(result).isEqualByComparingTo("1100.00");
    }

    @Test
    void roundsSurchargeToTwoDecimalsWithHalfUp() {
        BigDecimal result = calculator.calcularPrecioConRecargo(money("999.99"), money("3"));

        assertThat(result).isEqualByComparingTo("1029.99");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void zeroPercentageReturnsBasePrice() {
        BigDecimal result = calculator.calcularPrecioConRecargo(money("100"), BigDecimal.ZERO);

        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void nullPercentageReturnsBasePrice() {
        BigDecimal result = calculator.calcularPrecioConRecargo(money("100"), null);

        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void usdPriceWithoutDollarQuotationDoesNotCalculateInvalidPesoPrices() {
        TiendaPorteCalculatedPrices prices = calculator.calculate(money("100"), priceConfiguration(), null);

        assertThat(prices.getPrecioUsd()).isEqualByComparingTo("100.00");
        assertThat(prices.getPrecioPesos()).isNull();
        assertThat(prices.getPrecioTransferenciaBancaria()).isNull();
        assertThat(prices.getPrecioTarjeta3Pagos()).isNull();
        assertThat(prices.getPrecioTarjeta6Pagos()).isNull();
        assertThat(prices.getPrecioTarjeta12Pagos()).isNull();
    }

    @Test
    void zeroDollarQuotationDoesNotCalculateInvalidPesoPrices() {
        TiendaPorteCalculatedPrices prices = calculator.calculate(money("100"), priceConfiguration(), BigDecimal.ZERO);

        assertThat(prices.getPrecioUsd()).isEqualByComparingTo("100.00");
        assertThat(prices.getPrecioPesos()).isNull();
        assertThat(prices.getPrecioTransferenciaBancaria()).isNull();
        assertThat(prices.getPrecioTarjeta3Pagos()).isNull();
        assertThat(prices.getPrecioTarjeta6Pagos()).isNull();
        assertThat(prices.getPrecioTarjeta12Pagos()).isNull();
    }

    @Test
    void pesoPriceAvailableCalculatesSurchargesWithoutDollarQuotation() {
        TiendaPorteCalculatedPrices prices = calculator.calculate(null, money("100"), priceConfiguration(), null);

        assertThat(prices.getPrecioPesos()).isEqualByComparingTo("100.00");
        assertThat(prices.getPrecioTransferenciaBancaria()).isEqualByComparingTo("103.00");
        assertThat(prices.getPrecioTarjeta3Pagos()).isEqualByComparingTo("110.00");
        assertThat(prices.getPrecioTarjeta6Pagos()).isEqualByComparingTo("120.00");
        assertThat(prices.getPrecioTarjeta12Pagos()).isEqualByComparingTo("135.00");
    }

    @Test
    void rejectsNegativePercentageAsInvalidConfiguration() {
        PriceConfiguration configuration = priceConfiguration();
        configuration.setTransferenciaBancaria(money("-1"));

        assertThatThrownBy(() -> calculator.calculate(null, money("100"), configuration, null))
                .isInstanceOf(TiendaPorteBadRequestException.class)
                .hasMessageContaining("Configuracion invalida")
                .hasMessageContaining("transferenciaBancaria");
    }

    @Test
    void convertsUsdToPesosOnceBeforeApplyingSurcharges() {
        TiendaPorteCalculatedPrices prices = calculator.calculate(money("10"), priceConfiguration(), money("1000"));

        assertThat(prices.getPrecioPesos()).isEqualByComparingTo("10000.00");
        assertThat(prices.getPrecioTarjeta3Pagos()).isEqualByComparingTo("11000.00");
    }

    private PriceConfiguration priceConfiguration() {
        PriceConfiguration configuration = new PriceConfiguration();
        configuration.setTransferenciaBancaria(money("3"));
        configuration.setTarjeta3Pagos(money("10"));
        configuration.setTarjeta6Pagos(money("20"));
        configuration.setTarjeta12Pagos(money("35"));
        return configuration;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
