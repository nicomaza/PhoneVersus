package celulares.cordobacelulares.integration.liberadosya.controller;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.service.LiberadosYaCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/products/liberadosya")
public class LiberadosYaPublicController {

    private final LiberadosYaCatalogService catalogService;

    public LiberadosYaPublicController(LiberadosYaCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/match")
    public ResponseEntity<LiberadosYaProduct> match(
            @RequestParam String brand,
            @RequestParam String model
    ) {
        return ResponseEntity.ok(catalogService.findMatch(brand, model));
    }
}
