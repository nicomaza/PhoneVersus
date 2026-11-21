package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.services.SeoProductContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/product-content")
public class PublicProductContentController {

    private final SeoProductContentService service;

    public PublicProductContentController(SeoProductContentService service) {
        this.service = service;
    }

    @GetMapping("/published-slugs")
    public PublishedSlugsResponse publishedSlugs() {
        return new PublishedSlugsResponse(service.publishedSlugs());
    }

    public record PublishedSlugsResponse(List<String> slugs) {}
}
