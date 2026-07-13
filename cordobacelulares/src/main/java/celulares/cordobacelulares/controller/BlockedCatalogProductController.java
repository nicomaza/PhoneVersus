package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.admin.BlockedCatalogProductResponse;
import celulares.cordobacelulares.services.implement.BlockedCatalogProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/catalogo/bloqueados")
public class BlockedCatalogProductController {

    private final BlockedCatalogProductService blockedCatalogProductService;

    public BlockedCatalogProductController(BlockedCatalogProductService blockedCatalogProductService) {
        this.blockedCatalogProductService = blockedCatalogProductService;
    }

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    @GetMapping
    public ResponseEntity<List<BlockedCatalogProductResponse>> getBlockedProducts() {
        return ResponseEntity.ok(blockedCatalogProductService.getAll());
    }

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    @PostMapping("/{externalProductId}")
    public ResponseEntity<BlockedCatalogProductResponse> block(@PathVariable Long externalProductId) {
        return ResponseEntity.ok(blockedCatalogProductService.block(externalProductId));
    }

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    @DeleteMapping("/{externalProductId}")
    public ResponseEntity<Void> unblock(@PathVariable Long externalProductId) {
        blockedCatalogProductService.unblock(externalProductId);
        return ResponseEntity.noContent().build();
    }
}
