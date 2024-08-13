package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.ModelDto;
import celulares.cordobacelulares.entities.ModelEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ModelService {

    public List<ModelEntity> getAllModels();
    ModelEntity getModelById(Long id);

    ModelEntity saveModel(ModelDto modelDto);

     ModelEntity updateModel(Long id, ModelEntity model) ;

     void deleteModel(Long id) ;
}
