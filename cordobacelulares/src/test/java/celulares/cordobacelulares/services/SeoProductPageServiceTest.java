package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.SeoCatalogProductResponse;
import celulares.cordobacelulares.dtos.seo.SeoProductPageView;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteColorStockResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoProductPageServiceTest {

    private final SeoCatalogService seoCatalogService = mock(SeoCatalogService.class);
    private final TiendaPorteService tiendaPorteService = mock(TiendaPorteService.class);
    private SeoProductPageService service;

    @BeforeEach
    void setUp() {
        service = new SeoProductPageService(
                seoCatalogService,
                tiendaPorteService,
                new ObjectMapper()
        );
    }

    @Test
    void activeHistoricalProductUsesOnlyFinalUnifiedCatalogValues() {
        String slug = "redmi-note-15-6gb-128gb";
        when(seoCatalogService.findBySlug(slug)).thenReturn(
                new SeoCatalogProductResponse(slug, "XIAOMI", "REDMI NOTE 15 6GB 128GB", true)
        );
        TiendaPorteModelResponse current = model(
                "REDMI NOTE 15 6GB 128GB",
                CatalogProductOrigin.GOOGLE_SHEET,
                new BigDecimal("500000"),
                new BigDecimal("525000"),
                new BigDecimal("630000"),
                new BigDecimal("720000"),
                new BigDecimal("999999")
        );
        current.setColores(List.of(
                new TiendaPorteColorStockResponse("Negro", 3),
                new TiendaPorteColorStockResponse("negro", 1),
                new TiendaPorteColorStockResponse("Azul", 2)
        ));
        when(tiendaPorteService.getAllowedCategories(null, null)).thenReturn(
                List.of(new TiendaPorteBrandResponse("XIAOMI", List.of(current)))
        );

        SeoProductPageView page = service.resolve(slug);

        assertThat(page.available()).isTrue();
        assertThat(page.precioEfectivo()).isEqualTo("$ 500.000");
        assertThat(page.precioTransferencia()).isEqualTo("$ 525.000");
        assertThat(page.precioTarjeta3Pagos()).isEqualTo("$ 630.000");
        assertThat(page.precioTarjeta6Pagos()).isEqualTo("$ 720.000");
        assertThat(page.cuotaTarjeta6()).isEqualTo("$ 120.000");
        assertThat(page.colores()).containsExactly("Negro", "Azul");
        assertThat(page.productJsonLd())
                .contains("\"@type\":\"Product\"")
                .contains("\"@type\":\"Offer\"")
                .contains("\"price\":\"500000\"")
                .doesNotContain("999999");
        assertThat(page.whatsappUrl()).doesNotContain("999999");
        assertThat(page.canonicalUrl())
                .isEqualTo("https://cordobacelulares.com/celulares/redmi-note-15-6gb-128gb");
    }

    @Test
    void inactiveHistoricalProductDoesNotReadCurrentCatalogOrInventOffer() {
        String slug = "modelo-historico";
        when(seoCatalogService.findBySlug(slug)).thenReturn(
                new SeoCatalogProductResponse(slug, "MOTOROLA", "MODELO HISTÓRICO", false)
        );

        SeoProductPageView page = service.resolve(slug);

        assertThat(page.available()).isFalse();
        assertThat(page.modelo()).isEqualTo("MODELO HISTÓRICO");
        assertThat(page.precioEfectivo()).isNull();
        assertThat(page.productJsonLd())
                .contains("\"@type\":\"Product\"")
                .doesNotContain("\"@type\":\"Offer\"");
        verify(tiendaPorteService, never()).getAllowedCategories(null, null);
    }

    @Test
    void serializesUntrustedCatalogTextSafelyForHtmlScript() {
        String modelName = "MODELO </script><script>alert(1)</script>";
        String brand = "MARCA <img src=x onerror=alert(2)>";
        String slug = "modelo-script-script-alert-1-script";
        when(seoCatalogService.findBySlug(slug)).thenReturn(
                new SeoCatalogProductResponse(slug, brand, modelName, true)
        );
        TiendaPorteModelResponse current = model(
                modelName,
                CatalogProductOrigin.TIENDA_PORTE,
                new BigDecimal("100000"),
                null,
                null,
                null,
                null
        );
        current.setColores(List.of(new TiendaPorteColorStockResponse("<b>Rojo</b>", 1)));
        when(tiendaPorteService.getAllowedCategories(null, null)).thenReturn(
                List.of(new TiendaPorteBrandResponse(brand, List.of(current)))
        );

        SeoProductPageView page = service.resolve(slug);

        assertThat(page.productJsonLd())
                .doesNotContain("</script>")
                .doesNotContain("<img")
                .contains("\\u003c/script\\u003e");
        assertThat(page.breadcrumbJsonLd()).doesNotContain("<img");
        assertThat(page.colores()).containsExactly("<b>Rojo</b>");
    }

    @Test
    void preservesPerfumeConditionAndVisibleCommercialWarningWithoutPhoneClaims() {
        String slug = "fragancia-floral";
        when(seoCatalogService.findBySlug(slug)).thenReturn(
                new SeoCatalogProductResponse(slug, "PERFUMES", "FRAGANCIA FLORAL", true)
        );
        TiendaPorteModelResponse current = model(
                "FRAGANCIA FLORAL",
                CatalogProductOrigin.TIENDA_PORTE,
                new BigDecimal("90000"),
                new BigDecimal("95000"),
                null,
                null,
                null
        );
        current.setColores(List.of(new TiendaPorteColorStockResponse("Caja rosa", 1)));
        when(tiendaPorteService.getAllowedCategories(null, null)).thenReturn(
                List.of(new TiendaPorteBrandResponse("PERFUMES", List.of(current)))
        );

        SeoProductPageView page = service.resolve(slug);

        assertThat(page.intro()).isEqualTo("Precio actualizado en Córdoba Celulares.");
        assertThat(page.conditionLabel()).isEqualTo("En caja sellada de fábrica, sin abrir.");
        assertThat(page.whatsappWarning())
                .isEqualTo("Los perfumes requieren pago completo anticipado. ¿Desea continuar?");
        assertThat(page.description()).doesNotContain("liberado", "original");
        assertThat(page.whatsappUrl()).doesNotContain("Caja%20rosa");
    }

    private TiendaPorteModelResponse model(
            String name,
            CatalogProductOrigin origin,
            BigDecimal cash,
            BigDecimal transfer,
            BigDecimal card3,
            BigDecimal card6,
            BigDecimal card12
    ) {
        return new TiendaPorteModelResponse(
                name,
                origin,
                List.of(),
                null,
                cash,
                null,
                transfer,
                card3,
                card6,
                card12
        );
    }
}
