package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationCreateRequest;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationPatchRequest;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationResponse;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationUpdateRequest;
import celulares.cordobacelulares.services.PriceConfigurationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price-configuration")
public class PriceConfigurationController {

    private final PriceConfigurationService priceConfigurationService;

    public PriceConfigurationController(PriceConfigurationService priceConfigurationService) {
        this.priceConfigurationService = priceConfigurationService;
    }

    @PostMapping
    public ResponseEntity<PriceConfigurationResponse> create(@RequestBody PriceConfigurationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(priceConfigurationService.create(request));
    }

    @GetMapping
    public ResponseEntity<PriceConfigurationResponse> get() {
        return ResponseEntity.ok(priceConfigurationService.get());
    }

    @PutMapping
    public ResponseEntity<PriceConfigurationResponse> update(@RequestBody PriceConfigurationUpdateRequest request) {
        return ResponseEntity.ok(priceConfigurationService.update(request));
    }

    @PatchMapping
    public ResponseEntity<PriceConfigurationResponse> patch(@RequestBody PriceConfigurationPatchRequest request) {
        return ResponseEntity.ok(priceConfigurationService.patch(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete() {
        priceConfigurationService.delete();
        return ResponseEntity.noContent().build();
    }
}
