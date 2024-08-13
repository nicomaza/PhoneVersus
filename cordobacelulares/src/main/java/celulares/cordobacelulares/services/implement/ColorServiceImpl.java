package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.entities.ColorEntity;
import celulares.cordobacelulares.repository.ColorJPA;
import celulares.cordobacelulares.services.ColorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ColorServiceImpl implements ColorService {
    @Autowired
    ColorJPA colorJPA;
    @Override
    public ColorEntity getColorById(Long id) {
        return colorJPA.getReferenceById(id);
    }
}
