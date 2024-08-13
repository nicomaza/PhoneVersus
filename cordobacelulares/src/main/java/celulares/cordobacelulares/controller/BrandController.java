package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.BrandDto;
import celulares.cordobacelulares.dtos.common.ErrorApi;
import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.services.BrandService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
@RestController
@RequestMapping("/api/brand")
public class BrandController {

    @Autowired
    BrandService brandService;

    @PostMapping("createBrand")
    public ResponseEntity<Object> createBrand(@RequestBody BrandDto brandDTO) {
        try {
            String brandName = brandService.createBrand(brandDTO);
            return ResponseEntity.ok(brandName);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error al crear la marca.", ex.getMessage()));
        }
    }
    @GetMapping
    public ResponseEntity<Object> getAllBrands() {
        try {
            List<BrandDto> brands = brandService.getAllBrands();
            return ResponseEntity.ok(brands);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining brands", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteBrand(@PathVariable Long id) {
        try {
            brandService.deleteBrand(id);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error deleting brand", ex.getMessage()));
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity<Object> getBrandById(@PathVariable Long id) {
        try {
            BrandEntity brand = brandService.getBrandById(id);
            return brand != null ? ResponseEntity.ok(brand) : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.NOT_FOUND.value(), "Brand not found", "No brand found with id " + id));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining brand by ID", ex.getMessage()));
        }
    }
}
