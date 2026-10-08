package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.services.SeoCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoDiscoveryControllerTest {

    private final SeoCatalogService service = mock(SeoCatalogService.class);
    private final SeoDiscoveryController controller = new SeoDiscoveryController(service);

    @Test
    void sitemapContainsBasePagesAndOnlySlugsProvidedAsActive() {
        when(service.activeSlugs()).thenReturn(List.of("poco-c81-pro-4gb-128gb"));

        ResponseEntity<String> response = controller.sitemap();

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_XML);
        assertThat(response.getBody())
                .contains("<loc>https://cordobacelulares.com/</loc>")
                .contains("<loc>https://cordobacelulares.com/listapreciosactual</loc>")
                .contains("<loc>https://cordobacelulares.com/celulares/poco-c81-pro-4gb-128gb</loc>")
                .doesNotContain("www.");
    }

    @Test
    void robotsAllowsPublicDiscoveryAndDeclaresCanonicalSitemap() {
        ResponseEntity<String> response = controller.robots();

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("""
                User-agent: *
                Allow: /

                User-agent: OAI-SearchBot
                Allow: /

                Sitemap: https://cordobacelulares.com/sitemap.xml
                """);
    }
}
