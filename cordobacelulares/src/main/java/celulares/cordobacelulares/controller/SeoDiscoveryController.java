package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.services.SeoCatalogService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SeoDiscoveryController {

    private static final String CANONICAL_DOMAIN = "https://cordobacelulares.com";
    private static final String ROBOTS = """
            User-agent: *
            Allow: /

            User-agent: OAI-SearchBot
            Allow: /

            Sitemap: https://cordobacelulares.com/sitemap.xml
            """;

    private final SeoCatalogService seoCatalogService;

    public SeoDiscoveryController(SeoCatalogService seoCatalogService) {
        this.seoCatalogService = seoCatalogService;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(buildSitemap(seoCatalogService.activeSlugs()));
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(ROBOTS);
    }

    String buildSitemap(List<String> activeSlugs) {
        StringBuilder xml = new StringBuilder(512);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, CANONICAL_DOMAIN + "/");
        appendUrl(xml, CANONICAL_DOMAIN + "/listapreciosactual");
        if (activeSlugs != null) {
            for (String slug : activeSlugs) {
                if (slug != null && !slug.isBlank()) {
                    appendUrl(xml, CANONICAL_DOMAIN + "/celulares/" + slug);
                }
            }
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    private void appendUrl(StringBuilder xml, String location) {
        xml.append("  <url><loc>")
                .append(location)
                .append("</loc></url>\n");
    }
}
