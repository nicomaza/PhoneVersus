package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaProductResponse;
import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaMoney;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaPrices;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaProductNotFoundException;
import celulares.cordobacelulares.integration.liberadosya.service.LiberadosYaCatalogService;
import celulares.cordobacelulares.services.TiendaPorteService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BombaiComparativaServiceTest {

    @Test
    void calculatesCashAndUsdDifferencesAndCashOnlyGlobalStatistics() {
        TiendaPorteService tiendaPorte = mock(TiendaPorteService.class);
        LiberadosYaCatalogService liberadosYa = mock(LiberadosYaCatalogService.class);
        when(tiendaPorte.getAll(null, null)).thenReturn(List.of(new TiendaPorteBrandResponse(
                "Xiaomi",
                List.of(
                        model("Modelo A 8GB 256GB", "1", "1000"),
                        model("Modelo B 8GB 256GB", "2", "2000"),
                        model("Modelo C 8GB 256GB", "3", "3000"),
                        model("Modelo sin match 8GB 256GB", "4", "4000")
                )
        )));
        when(liberadosYa.findMatch(anyString(), eq("Modelo A 8GB 256GB")))
                .thenReturn(competitor("Modelo A 8GB 256GB", "1200", "0.71"));
        when(liberadosYa.findMatch(anyString(), eq("Modelo B 8GB 256GB")))
                .thenReturn(competitor("Modelo B 8GB 256GB", "1800", "1.06"));
        when(liberadosYa.findMatch(anyString(), eq("Modelo C 8GB 256GB")))
                .thenReturn(competitor("Modelo C 8GB 256GB", "3000", null));
        when(liberadosYa.findMatch(anyString(), eq("Modelo sin match 8GB 256GB")))
                .thenThrow(new LiberadosYaProductNotFoundException());

        BombaiComparativaResponse response = new BombaiComparativaService(tiendaPorte, liberadosYa).getComparativa();

        BombaiComparativaProductResponse cheaper = row(response, "Modelo A 8GB 256GB");
        assertThat(cheaper.getMine().getCostUsd()).isEqualByComparingTo("1.00");
        assertThat(cheaper.getMine().getPriceUsd()).isEqualByComparingTo("1.00");
        assertThat(cheaper.getDifferences().getCashArs()).isEqualByComparingTo("200.00");
        assertThat(cheaper.getDifferences().getUsd()).isEqualByComparingTo("-0.29");
        assertThat(cheaper.getComparison().getCash()).isEqualTo("CHEAPER");

        BombaiComparativaProductResponse expensive = row(response, "Modelo B 8GB 256GB");
        assertThat(expensive.getDifferences().getCashArs()).isEqualByComparingTo("-200.00");
        assertThat(expensive.getComparison().getCash()).isEqualTo("MORE_EXPENSIVE");

        BombaiComparativaProductResponse missingUsd = row(response, "Modelo C 8GB 256GB");
        assertThat(missingUsd.getDifferences().getUsd()).isNull();
        assertThat(missingUsd.getComparison().getUsd()).isEqualTo("NOT_AVAILABLE");

        assertThat(response.getSummary().getCompetitorImplicitRateOnMyCost().getMedian())
                .isEqualByComparingTo("1000.00");
        assertThat(response.getSummary().getMineCheaperCount()).isEqualTo(1);
        assertThat(response.getSummary().getMineMoreExpensiveCount()).isEqualTo(1);
        assertThat(response.getSummary().getMixedCount()).isEqualTo(1);
        assertThat(response.getSummary().getResultStatus()).isEqualTo("MIXED");
        assertThat(response.getSummary().getComparableProducts()).isEqualTo(3);
        assertThat(response.getSummary().getWithoutMatchCount()).isEqualTo(1);
    }

    private TiendaPorteModelResponse model(String name, String usd, String cashArs) {
        return new TiendaPorteModelResponse(
                name,
                CatalogProductOrigin.TIENDA_PORTE,
                List.of(),
                new BigDecimal(usd),
                new BigDecimal(cashArs),
                null,
                null,
                null,
                null,
                null
        );
    }

    private LiberadosYaProduct competitor(String name, String cashArs, String cashUsd) {
        LiberadosYaProduct product = new LiberadosYaProduct();
        product.setNombre(name);
        product.setVersionComercial(name);
        product.setPrecios(new LiberadosYaPrices(
                new LiberadosYaMoney(null, null, new BigDecimal(cashArs), null, null, null),
                new LiberadosYaMoney(null, null, cashUsd == null ? null : new BigDecimal(cashUsd), null, null, null)
        ));
        return product;
    }

    private BombaiComparativaProductResponse row(BombaiComparativaResponse response, String model) {
        return response.getProducts().stream()
                .filter(product -> model.equals(product.getModel()))
                .findFirst()
                .orElseThrow();
    }
}
