package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheRefreshResponse;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheStatusResponse;
import celulares.cordobacelulares.services.implement.TiendaPorteCatalogCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalogo/cache")
public class CatalogCacheAdminController {

    private final TiendaPorteCatalogCacheService cacheService;

    public CatalogCacheAdminController(TiendaPorteCatalogCacheService cacheService) {
        this.cacheService = cacheService;
    }

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    @GetMapping("/status")
    public ResponseEntity<CatalogCacheStatusResponse> status() {
        return ResponseEntity.ok(cacheService.status());
    }

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    @PostMapping("/refresh")
    public ResponseEntity<CatalogCacheRefreshResponse> refresh() {
        return ResponseEntity.ok(cacheService.refreshFromAdmin());
    }
}
