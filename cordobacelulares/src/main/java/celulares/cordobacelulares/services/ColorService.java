package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.color.ColorDto;
import celulares.cordobacelulares.entities.BrandEntity;
import celulares.cordobacelulares.entities.ColorEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ColorService {
    ColorEntity getColorById(Long id) ;

    List<ColorDto> getAllColors();
}
