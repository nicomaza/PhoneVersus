package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.color.ColorDto;
import celulares.cordobacelulares.entities.ColorEntity;
import celulares.cordobacelulares.repository.ColorJPA;
import celulares.cordobacelulares.services.ColorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ColorServiceImpl implements ColorService {
    @Autowired
    ColorJPA colorJPA;
    @Override
    public ColorEntity getColorById(Long id) {
        return colorJPA.getReferenceById(id);
    }

    @Override
    public List<ColorDto> getAllColors() {
        // Transforma las entidades en DTOs
        return colorJPA.findAll().stream()
                .map(color -> new ColorDto(color.getIdColor(), color.getName()))
                .collect(Collectors.toList());
    }
}
