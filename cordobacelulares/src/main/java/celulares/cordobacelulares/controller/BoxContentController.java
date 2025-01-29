package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.box.BoxContentDto;
import celulares.cordobacelulares.services.BoxContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/boxcontent")
public class BoxContentController {

    @Autowired
    BoxContentService boxContentService;


    @GetMapping
    public List<BoxContentDto> getAllBoxContent() {
        return boxContentService.getAllBoxContent();
    }
}
