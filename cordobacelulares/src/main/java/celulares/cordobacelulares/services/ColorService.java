package celulares.cordobacelulares.services;

import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.entities.ColorEntity;
import org.springframework.stereotype.Service;

@Service
public interface ColorService {
    ColorEntity getColorById(Long id) ;
}
