package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.GlobalExceptionHandler;
import celulares.cordobacelulares.dtos.ModelDto;
import celulares.cordobacelulares.entities.ModelEntity;
import celulares.cordobacelulares.repository.BrandJPA;
import celulares.cordobacelulares.repository.ModelJPA;
import celulares.cordobacelulares.services.BrandService;
import celulares.cordobacelulares.services.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;
import java.util.Optional;

@Service
public class ModelServiceImpl implements ModelService {
    @Autowired
    ModelJPA modelRepository;
    @Autowired
    BrandJPA brandJPA;

    @Override
    public ModelEntity saveModel(ModelDto modelDto) {

        Optional<ModelEntity> existingModel = modelRepository.findByModelNameIgnoreCase(modelDto.getModel());

        if (existingModel.isPresent()) {
            throw new GlobalExceptionHandler.DuplicateModelException("Model already exists");
        }

        ModelEntity modelnew = new ModelEntity();
        modelnew.setModelName(modelDto.getModel());
        modelnew.setBrand(brandJPA.getReferenceById(modelDto.getBrand()));

        return modelRepository.save(modelnew);
    }

    public List<ModelEntity> getAllModels() {
        return modelRepository.findAll();
    }

    public ModelEntity getModelById(Long id) {
        return modelRepository.findById(id).orElse(null);
    }


    public ModelEntity updateModel(Long id, ModelEntity model) {
        if (modelRepository.existsById(id)) {
            model.setIdModel(id);
            return modelRepository.save(model);
        }
        return null;
    }

    public void deleteModel(Long id) {
        modelRepository.deleteById(id);
    }
}
