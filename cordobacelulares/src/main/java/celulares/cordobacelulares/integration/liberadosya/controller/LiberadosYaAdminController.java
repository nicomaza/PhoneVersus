package celulares.cordobacelulares.integration.liberadosya.controller;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaRefreshResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaStatusResponse;
import celulares.cordobacelulares.integration.liberadosya.service.LiberadosYaCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integrations/liberadosya")
public class LiberadosYaAdminController {

    private final LiberadosYaCatalogService catalogService;

    public LiberadosYaAdminController(LiberadosYaCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/status")
    public ResponseEntity<LiberadosYaStatusResponse> status() {
        return ResponseEntity.ok(catalogService.status());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LiberadosYaRefreshResponse> refresh() {
        return ResponseEntity.ok(catalogService.refreshFromAdmin());
    }
}
