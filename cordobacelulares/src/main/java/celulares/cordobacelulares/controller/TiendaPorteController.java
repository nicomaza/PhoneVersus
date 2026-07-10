package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteCategoryPageResponse;
import celulares.cordobacelulares.services.TiendaPorteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/tiendaporte/equipos")
public class TiendaPorteController {

    private final TiendaPorteService tiendaPorteService;

    public TiendaPorteController(TiendaPorteService tiendaPorteService) {
        this.tiendaPorteService = tiendaPorteService;
    }

    @GetMapping
    public ResponseEntity<List<TiendaPorteBrandResponse>> getAll(
            @RequestParam(required = false) BigDecimal cotizacionDolar,
            @RequestHeader(value = "X-Cotizacion-Dolar", required = false) String cotizacionDolarHeader
    ) {
        return ResponseEntity.ok(tiendaPorteService.getAll(cotizacionDolar, cotizacionDolarHeader));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<TiendaPorteBrandResponse>> search(
            @RequestParam String texto,
            @RequestParam(required = false) BigDecimal cotizacionDolar,
            @RequestHeader(value = "X-Cotizacion-Dolar", required = false) String cotizacionDolarHeader
    ) {
        return ResponseEntity.ok(tiendaPorteService.search(texto, cotizacionDolar, cotizacionDolarHeader));
    }

    @GetMapping("/categorias-principales")
    public ResponseEntity<List<TiendaPorteBrandResponse>> getAllowedCategories(
            @RequestParam(required = false) BigDecimal cotizacionDolar,
            @RequestHeader(value = "X-Cotizacion-Dolar", required = false) String cotizacionDolarHeader
    ) {
        return ResponseEntity.ok(tiendaPorteService.getAllowedCategories(cotizacionDolar, cotizacionDolarHeader));
    }

    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<TiendaPorteCategoryPageResponse> getByCategory(
            @PathVariable String categoria,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) BigDecimal cotizacionDolar,
            @RequestHeader(value = "X-Cotizacion-Dolar", required = false) String cotizacionDolarHeader
    ) {
        return ResponseEntity.ok(tiendaPorteService.getByCategory(categoria, page, limit, cotizacionDolar, cotizacionDolarHeader));
    }
}
