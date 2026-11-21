package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.HomeBannerDtos;
import celulares.cordobacelulares.services.HomeBannerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/home-banners")
public class AdminHomeBannerController {

    private final HomeBannerService service;

    public AdminHomeBannerController(HomeBannerService service) {
        this.service = service;
    }

    @GetMapping
    public List<HomeBannerDtos.AdminResponse> list() {
        return service.listAll();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HomeBannerDtos.AdminResponse create(
            @RequestParam String title,
            @RequestParam String altText,
            @RequestParam(required = false) String linkUrl,
            @RequestPart("desktopImage") MultipartFile desktopImage,
            @RequestPart(value = "mobileImage", required = false) MultipartFile mobileImage
    ) {
        return service.create(title, altText, linkUrl, desktopImage, mobileImage);
    }

    @PutMapping("/{id}")
    public HomeBannerDtos.AdminResponse updateMetadata(
            @PathVariable Long id,
            @Valid @RequestBody HomeBannerDtos.MetadataRequest request
    ) {
        return service.updateMetadata(id, request);
    }

    @PatchMapping("/{id}/active")
    public HomeBannerDtos.AdminResponse setActive(
            @PathVariable Long id,
            @RequestBody HomeBannerDtos.ActivationRequest request
    ) {
        return service.setActive(id, request.active());
    }

    @PutMapping("/order")
    public List<HomeBannerDtos.AdminResponse> reorder(
            @Valid @RequestBody HomeBannerDtos.ReorderRequest request
    ) {
        return service.reorder(request.bannerIds());
    }

    @PutMapping(path = "/{id}/images/desktop", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HomeBannerDtos.AdminResponse replaceDesktop(
            @PathVariable Long id,
            @RequestPart("image") MultipartFile image
    ) {
        return service.replaceDesktop(id, image);
    }

    @PutMapping(path = "/{id}/images/mobile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HomeBannerDtos.AdminResponse replaceMobile(
            @PathVariable Long id,
            @RequestPart("image") MultipartFile image
    ) {
        return service.replaceMobile(id, image);
    }

    @DeleteMapping("/{id}/images/mobile")
    public HomeBannerDtos.AdminResponse removeMobile(@PathVariable Long id) {
        return service.removeMobile(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
