package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.color.ColorDto;
import celulares.cordobacelulares.services.BrandService;
import celulares.cordobacelulares.services.ColorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/colors")
public class ColorController {

    @Autowired
    ColorService colorService;

    @GetMapping
    public List<ColorDto> getAllColors() {
        return colorService.getAllColors();
    }
}
