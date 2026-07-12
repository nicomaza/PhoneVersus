package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.admin.AdminCatalogPageResponse;
import celulares.cordobacelulares.services.implement.AdminCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalogo")
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    public AdminCatalogController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    @GetMapping("/equipos")
    public ResponseEntity<AdminCatalogPageResponse> getProducts(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String stock
    ) {
        return ResponseEntity.ok(adminCatalogService.getProducts(texto, categoria, page, limit, sort, direction, origen, stock));
    }
}
