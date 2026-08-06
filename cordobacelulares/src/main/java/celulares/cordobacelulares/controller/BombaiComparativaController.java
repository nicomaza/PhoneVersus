package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaResponse;
import celulares.cordobacelulares.services.implement.BombaiComparativaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/bombai")
public class BombaiComparativaController {

    private final BombaiComparativaService comparativaService;

    public BombaiComparativaController(BombaiComparativaService comparativaService) {
        this.comparativaService = comparativaService;
    }

    @GetMapping("/comparativa")
    public ResponseEntity<BombaiComparativaResponse> getComparativa() {
        return ResponseEntity.ok(comparativaService.getComparativa());
    }
}
