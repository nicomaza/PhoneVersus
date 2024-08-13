package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.ModelDto;
import celulares.cordobacelulares.dtos.common.ErrorApi;
import celulares.cordobacelulares.entities.ModelEntity;
import celulares.cordobacelulares.repository.ModelJPA;
import celulares.cordobacelulares.services.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    @Autowired
    ModelService modelService;

    @GetMapping
    public ResponseEntity<Object> getAllModels() {
        try {
            List<ModelEntity> models = modelService.getAllModels();
            return ResponseEntity.ok(models);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining models", ex.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getModelById(@PathVariable Long id) {
        try {
            ModelEntity model = modelService.getModelById(id);
            return model != null ? ResponseEntity.ok(model) : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.NOT_FOUND.value(), "Model not found", "No model found with id " + id));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error obtaining model by ID", ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Object> createModel(@RequestBody ModelDto modelDto ) {
        try {
            ModelEntity savedModel = modelService.saveModel(modelDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedModel);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error creating model", ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> updateModel(@PathVariable Long id, @RequestBody ModelEntity model) {
        try {
            ModelEntity updatedModel = modelService.updateModel(id, model);
            return updatedModel != null ? ResponseEntity.ok(updatedModel) : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.NOT_FOUND.value(), "Model not found", "No model found with id " + id));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error updating model", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteModel(@PathVariable Long id) {
        try {
            modelService.deleteModel(id);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorApi(LocalDateTime.now().toString(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error deleting model", ex.getMessage()));
        }
    }
}
