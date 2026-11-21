package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.HomeBannerDtos;
import celulares.cordobacelulares.services.HomeBannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/home-banners")
public class PublicHomeBannerController {

    private final HomeBannerService service;

    public PublicHomeBannerController(HomeBannerService service) {
        this.service = service;
    }

    @GetMapping
    public List<HomeBannerDtos.PublicResponse> list() {
        return service.listActive();
    }
}
