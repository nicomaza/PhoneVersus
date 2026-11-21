package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.seo.SeoProductPageView;
import celulares.cordobacelulares.dtos.seo.PublishedProductContent;
import celulares.cordobacelulares.config.BombaiSecurityProperties;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.security.AdminSecurityConfig;
import celulares.cordobacelulares.services.SeoProductPageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeoProductPageController.class)
@Import({AdminSecurityConfig.class, BombaiSecurityProperties.class})
class SeoProductPageControllerTest {

    private static final String SLUG = "redmi-note-15-6gb-128gb";
    private static final String CANONICAL = "https://cordobacelulares.com/celulares/" + SLUG;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeoProductPageService productPageService;

    @Test
    void activeSlugReturnsCompleteServerRenderedProductHtml() throws Exception {
        when(productPageService.resolve(SLUG)).thenReturn(activePage());

        mockMvc.perform(get("/celulares/{slug}", SLUG))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<html lang=\"es-AR\"")))
                .andExpect(content().string(containsString("<h1>REDMI NOTE 15 6GB 128GB</h1>")))
                .andExpect(content().string(containsString("XIAOMI")))
                .andExpect(content().string(containsString("$ 500.000")))
                .andExpect(content().string(containsString("href=\"/seo-assets/seo-product.css\"")))
                .andExpect(content().string(containsString("rel=\"canonical\" href=\"" + CANONICAL + "\"")))
                .andExpect(content().string(containsString("\"@type\":\"Product\"")))
                .andExpect(content().string(containsString("\"@type\":\"Offer\"")))
                .andExpect(content().string(not(containsString("<h2 id=\"product-highlights-title\">"))))
                .andExpect(content().string(not(containsString("Tarjeta 12 pagos"))))
                .andExpect(content().string(not(containsString("999.999"))));
    }

    @Test
    void inactiveHistoricalSlugStaysHttp200WithoutOffer() throws Exception {
        when(productPageService.resolve(SLUG)).thenReturn(inactivePage());

        mockMvc.perform(get("/celulares/{slug}", SLUG))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>REDMI NOTE 15 6GB 128GB</h1>")))
                .andExpect(content().string(containsString("Producto no disponible actualmente")))
                .andExpect(content().string(containsString("\"@type\":\"Product\"")))
                .andExpect(content().string(not(containsString("\"@type\":\"Offer\""))))
                .andExpect(content().string(not(containsString("$ 500.000"))));
    }

    @Test
    void unknownSlugReturnsRealHttp404() throws Exception {
        when(productPageService.resolve("nunca-existio"))
                .thenThrow(new ApiNotFoundException("El producto solicitado nunca existio en el catalogo"));

        mockMvc.perform(get("/celulares/nunca-existio"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("href=\"/seo-assets/seo-product.css\"")))
                .andExpect(content().string(containsString("Producto no encontrado")));
    }

    @Test
    void dynamicVisibleTextIsHtmlEscaped() throws Exception {
        SeoProductPageView malicious = new SeoProductPageView(
                SLUG,
                "MARCA <img src=x onerror=alert(2)>",
                "MODELO <script>alert(1)</script>",
                true,
                "MODELO <script>alert(1)</script>: precio en Córdoba | Córdoba Celulares",
                "Descripción <b>no confiable</b>",
                CANONICAL,
                "Precio <em>actualizado</em>",
                "$ 500.000",
                null,
                null,
                null,
                null,
                List.of("<strong>Rojo</strong>"),
                "Condición <iframe>no confiable</iframe>",
                null,
                "https://wa.me/5493512129922",
                "{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"name\":\"MODELO \\u003cscript\\u003ealert(1)\\u003c/script\\u003e\"}",
                "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\"}"
        );
        when(productPageService.resolve(SLUG)).thenReturn(malicious);

        mockMvc.perform(get("/celulares/{slug}", SLUG))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("<img src=x"))))
                .andExpect(content().string(not(containsString("<strong>Rojo</strong>"))))
                .andExpect(content().string(containsString("MODELO &lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("&lt;strong&gt;Rojo&lt;/strong&gt;")));
    }

    @Test
    void seoStylesheetIsServedFromDedicatedSpringAssetPath() throws Exception {
        mockMvc.perform(get("/seo-assets/seo-product.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString(".product-page")));
    }

    @Test
    void publishedContentIsFullyPresentInServerRenderedHtml() throws Exception {
        SeoProductPageView base = activePage();
        SeoProductPageView enriched = new SeoProductPageView(
                base.slug(), base.marca(), base.modelo(), base.available(), base.title(),
                "Descripción editorial publicada", base.canonicalUrl(), base.intro(),
                base.precioEfectivo(), base.precioTransferencia(), base.precioTarjeta3Pagos(),
                base.precioTarjeta6Pagos(), base.cuotaTarjeta6(), base.colores(), base.conditionLabel(),
                base.whatsappWarning(), base.whatsappUrl(),
                "{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"description\":\"Descripción editorial publicada\",\"image\":[\"https://cordobacelulares.com/product-media/phone.webp\"]}",
                base.breadcrumbJsonLd(), true, "Descripción editorial publicada",
                List.of(new PublishedProductContent.Image("/product-media/phone.webp", "Frente del equipo", true)),
                List.of(new PublishedProductContent.Highlight("Pantalla de alta definición")),
                List.of(new PublishedProductContent.SpecSection(
                        "Pantalla", List.of(new PublishedProductContent.Spec("Tamaño", "6,7 pulgadas"))
                )),
                "https://www.mi.com/ar", "Xiaomi Argentina",
                "https://cordobacelulares.com/product-media/phone.webp"
        );
        when(productPageService.resolve(SLUG)).thenReturn(enriched);

        mockMvc.perform(get("/celulares/{slug}", SLUG))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Descripción editorial publicada")))
                .andExpect(content().string(containsString("src=\"/product-media/phone.webp\"")))
                .andExpect(content().string(containsString("Pantalla de alta definición")))
                .andExpect(content().string(containsString("6,7 pulgadas")))
                .andExpect(content().string(containsString("og:image")))
                .andExpect(content().string(containsString("$ 500.000")));
    }

    private SeoProductPageView activePage() {
        return new SeoProductPageView(
                SLUG,
                "XIAOMI",
                "REDMI NOTE 15 6GB 128GB",
                true,
                "REDMI NOTE 15 6GB 128GB: precio en Córdoba | Córdoba Celulares",
                "Consultá el precio actualizado del REDMI NOTE 15 6GB 128GB en Córdoba Celulares.",
                CANONICAL,
                "Celular nuevo, liberado y original. Precio actualizado en Córdoba Celulares.",
                "$ 500.000",
                "$ 525.000",
                "$ 630.000",
                "$ 720.000",
                "$ 120.000",
                List.of("Negro", "Azul"),
                "Equipo nuevo, liberado, original, sellado de fábrica y con garantía.",
                null,
                "https://wa.me/5493512129922?text=consulta",
                "{\"@context\":\"https://schema.org\",\"@type\":\"Product\",\"offers\":{\"@type\":\"Offer\",\"priceCurrency\":\"ARS\",\"price\":\"500000\"}}",
                "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\"}"
        );
    }

    private SeoProductPageView inactivePage() {
        return new SeoProductPageView(
                SLUG,
                "XIAOMI",
                "REDMI NOTE 15 6GB 128GB",
                false,
                "REDMI NOTE 15 6GB 128GB | Córdoba Celulares",
                "Producto no disponible actualmente.",
                CANONICAL,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "Equipo nuevo, liberado, original, sellado de fábrica y con garantía.",
                null,
                "https://wa.me/5493512129922?text=consulta",
                "{\"@context\":\"https://schema.org\",\"@type\":\"Product\"}",
                "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\"}"
        );
    }
}
