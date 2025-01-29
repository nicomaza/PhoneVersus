package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.box.BoxContentDto;
import celulares.cordobacelulares.repository.BoxContentJPA;
import celulares.cordobacelulares.services.BoxContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BoxContentServiceImpl implements BoxContentService {

    @Autowired
    BoxContentJPA boxContentJPA;


    @Override
    public List<BoxContentDto> getAllBoxContent() {
        return boxContentJPA.findAll()
                .stream()
                .map(content -> new BoxContentDto(content.getIdContent(), content.getNameContent()))
                .collect(Collectors.toList());
    }
}
