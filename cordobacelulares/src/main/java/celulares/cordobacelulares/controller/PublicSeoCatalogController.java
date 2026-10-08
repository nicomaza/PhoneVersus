package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.seo.SeoCatalogProductResponse;
import celulares.cordobacelulares.services.SeoCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/catalog/products")
public class PublicSeoCatalogController {

    private final SeoCatalogService seoCatalogService;

    public PublicSeoCatalogController(SeoCatalogService seoCatalogService) {
        this.seoCatalogService = seoCatalogService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<SeoCatalogProductResponse> findBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(seoCatalogService.findBySlug(slug));
    }
}
