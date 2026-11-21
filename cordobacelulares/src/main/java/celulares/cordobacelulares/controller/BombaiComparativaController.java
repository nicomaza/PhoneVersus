package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.comparativa.BombaiComparativaResponse;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaQuotationResponse;
import celulares.cordobacelulares.integration.liberadosya.service.LiberadosYaQuotationService;
import celulares.cordobacelulares.services.implement.BombaiComparativaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/bombai")
public class BombaiComparativaController {

    private final BombaiComparativaService comparativaService;
    private final LiberadosYaQuotationService quotationService;

    public BombaiComparativaController(
            BombaiComparativaService comparativaService,
            LiberadosYaQuotationService quotationService
    ) {
        this.comparativaService = comparativaService;
        this.quotationService = quotationService;
    }

    @GetMapping("/comparativa")
    public ResponseEntity<BombaiComparativaResponse> getComparativa() {
        return ResponseEntity.ok(comparativaService.getComparativa());
    }

    @GetMapping("/liberadosya/cotizaciones")
    public ResponseEntity<LiberadosYaQuotationResponse> getLiberadosYaQuotations() {
        return ResponseEntity.ok(quotationService.getQuotations());
    }
}
