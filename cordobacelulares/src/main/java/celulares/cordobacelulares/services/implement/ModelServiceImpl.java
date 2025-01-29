package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.GlobalExceptionHandler;
import celulares.cordobacelulares.dtos.model.ModelDto;
import celulares.cordobacelulares.dtos.model.ModelNewDto;
import celulares.cordobacelulares.entities.ModelEntity;
import celulares.cordobacelulares.repository.BrandJPA;
import celulares.cordobacelulares.repository.ModelJPA;
import celulares.cordobacelulares.services.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Override
    public List<ModelNewDto> getAllModelsDto() {
        return modelRepository.findAll().stream().map(model ->
                new ModelNewDto(
                        model.getModelName(), // model
                        model.getIdModel(),   // idModel
                        model.getBrand().getBrandName(), // brand
                        model.getBrand().getIdBrand() // idBrand
                )
        ).collect(Collectors.toList());
    }


}
