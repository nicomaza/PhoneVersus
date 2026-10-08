package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.seo.SeoProductPageView;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.services.SeoProductPageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class SeoProductPageController {

    private final SeoProductPageService productPageService;

    public SeoProductPageController(SeoProductPageService productPageService) {
        this.productPageService = productPageService;
    }

    @GetMapping(value = "/celulares/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView product(@PathVariable String slug) {
        try {
            SeoProductPageView page = productPageService.resolve(slug);
            return new ModelAndView("seo/product", "page", page);
        } catch (ApiNotFoundException ex) {
            ModelAndView notFound = new ModelAndView("seo/product-not-found");
            notFound.setStatus(HttpStatus.NOT_FOUND);
            return notFound;
        }
    }
}
