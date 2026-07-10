package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetPreviewResponse;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetReloadResponse;
import celulares.cordobacelulares.services.SupplierSheetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supplier-sheet")
public class SupplierSheetController {

    private final SupplierSheetService supplierSheetService;

    public SupplierSheetController(SupplierSheetService supplierSheetService) {
        this.supplierSheetService = supplierSheetService;
    }

    @GetMapping("/preview")
    public ResponseEntity<SupplierSheetPreviewResponse> preview() {
        return ResponseEntity.ok(supplierSheetService.preview());
    }

    @PostMapping("/reload")
    public ResponseEntity<SupplierSheetReloadResponse> reload() {
        return ResponseEntity.ok(new SupplierSheetReloadResponse(supplierSheetService.reload()));
    }
}
