package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.services.HomeBannerMediaStorage;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.TimeUnit;

@Controller
public class HomeBannerMediaController {

    private final HomeBannerMediaStorage storage;

    public HomeBannerMediaController(HomeBannerMediaStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/home-banner-media/{filename:.+}")
    public ResponseEntity<Resource> image(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.contentType(filename)))
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                .body(storage.load(filename));
    }
}
