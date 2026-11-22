package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.dtos.seo.ProductContentAdminDtos;
import celulares.cordobacelulares.dtos.seo.PorteProductSyncDtos;
import celulares.cordobacelulares.services.SeoProductContentService;
import celulares.cordobacelulares.services.SeoPorteSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/product-content")
public class AdminProductContentController {

    private final SeoProductContentService service;
    private final SeoPorteSyncService porteSyncService;

    public AdminProductContentController(
            SeoProductContentService service,
            SeoPorteSyncService porteSyncService
    ) {
        this.service = service;
        this.porteSyncService = porteSyncService;
    }

    @PostMapping("/porte/sync")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public PorteProductSyncDtos.StartResponse synchronizeFromPorte() {
        return porteSyncService.startAll("MANUAL");
    }

    @GetMapping("/porte/sync/status")
    public PorteProductSyncDtos.StatusResponse porteSyncStatus() {
        return porteSyncService.status();
    }

    @PostMapping("/products/{slug}/porte/sync")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public PorteProductSyncDtos.StartResponse synchronizeProductFromPorte(@PathVariable String slug) {
        return porteSyncService.startProduct(slug);
    }

    @GetMapping("/products")
    public ProductContentAdminDtos.PageResponse list(
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String brand,
            @RequestParam(defaultValue = "ALL") String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return service.list(text, brand, state, page, limit);
    }

    @GetMapping("/products/{slug}")
    public ProductContentAdminDtos.DetailResponse detail(@PathVariable String slug) {
        return service.get(slug);
    }

    @PutMapping("/products/{slug}/draft")
    public ProductContentAdminDtos.DetailResponse saveDraft(
            @PathVariable String slug,
            @RequestBody ProductContentAdminDtos.SaveRequest request
    ) {
        return service.saveDraft(slug, request);
    }

    @PostMapping("/products/{slug}/publish")
    public ProductContentAdminDtos.DetailResponse publish(@PathVariable String slug) {
        return service.publish(slug);
    }

    @PostMapping("/products/{slug}/unpublish")
    public ProductContentAdminDtos.DetailResponse unpublish(@PathVariable String slug) {
        return service.unpublish(slug);
    }

    @PostMapping(value = "/products/{slug}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductContentAdminDtos.DetailResponse uploadImage(
            @PathVariable String slug,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String altText
    ) {
        return service.uploadImage(slug, file, altText);
    }

    @PutMapping("/products/{slug}/images/{imageId}")
    public ProductContentAdminDtos.DetailResponse updateImageAlt(
            @PathVariable String slug,
            @PathVariable long imageId,
            @RequestBody ProductContentAdminDtos.ImageAltRequest request
    ) {
        return service.updateImageAlt(slug, imageId, request);
    }

    @PutMapping("/products/{slug}/images/order")
    public ProductContentAdminDtos.DetailResponse reorderImages(
            @PathVariable String slug,
            @RequestBody ProductContentAdminDtos.ImageOrderRequest request
    ) {
        return service.reorderImages(slug, request);
    }

    @PutMapping("/products/{slug}/images/{imageId}/primary")
    public ProductContentAdminDtos.DetailResponse setPrimaryImage(
            @PathVariable String slug,
            @PathVariable long imageId
    ) {
        return service.setPrimaryImage(slug, imageId);
    }

    @DeleteMapping("/products/{slug}/images/{imageId}")
    public ProductContentAdminDtos.DetailResponse deleteImage(
            @PathVariable String slug,
            @PathVariable long imageId
    ) {
        return service.deleteImage(slug, imageId);
    }
}
