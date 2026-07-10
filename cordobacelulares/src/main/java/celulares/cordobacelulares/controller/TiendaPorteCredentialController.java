package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialCreateRequest;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialPatchRequest;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialResponse;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialUpdateRequest;
import celulares.cordobacelulares.services.TiendaPorteCredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tiendaporte-credentials")
public class TiendaPorteCredentialController {

    private final TiendaPorteCredentialService credentialService;

    public TiendaPorteCredentialController(TiendaPorteCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    public ResponseEntity<TiendaPorteCredentialResponse> create(@RequestBody TiendaPorteCredentialCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(credentialService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TiendaPorteCredentialResponse>> getAll() {
        return ResponseEntity.ok(credentialService.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<TiendaPorteCredentialResponse> getActive() {
        return ResponseEntity.ok(credentialService.getActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TiendaPorteCredentialResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(credentialService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TiendaPorteCredentialResponse> update(
            @PathVariable Long id,
            @RequestBody TiendaPorteCredentialUpdateRequest request
    ) {
        return ResponseEntity.ok(credentialService.update(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TiendaPorteCredentialResponse> patch(
            @PathVariable Long id,
            @RequestBody TiendaPorteCredentialPatchRequest request
    ) {
        return ResponseEntity.ok(credentialService.patch(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        credentialService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
