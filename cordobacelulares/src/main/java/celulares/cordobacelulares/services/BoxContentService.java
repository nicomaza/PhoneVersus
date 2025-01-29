package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.box.BoxContentDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface BoxContentService {

    List<BoxContentDto> getAllBoxContent();
}
