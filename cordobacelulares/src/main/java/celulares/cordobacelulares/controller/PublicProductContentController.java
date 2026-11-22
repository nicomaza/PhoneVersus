package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.services.SeoProductContentService;
import celulares.cordobacelulares.dtos.seo.PublishedProductContent;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/products/{slug}/features")
    public PublishedFeaturesResponse features(@PathVariable String slug) {
        PublishedProductContent published = service.published(slug)
                .orElseThrow(() -> new ApiNotFoundException("La ficha publicada no existe"));
        return new PublishedFeaturesResponse(
                published.sourceName(), published.sourceUrl(),
                published.highlights(), published.specSections(), published.images()
        );
    }

    public record PublishedFeaturesResponse(
            String sourceName,
            String sourceUrl,
            List<PublishedProductContent.Highlight> highlights,
            List<PublishedProductContent.SpecSection> specSections,
            List<PublishedProductContent.Image> images
    ) {}
}
