package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.ProductImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Product pictures for <img> tags (which can't send a sign-in token). The key is random and unguessable. */
@RestController
public class PublicImageController {

    private final ProductImageService images;
    private final com.ultimate.inventory.Service.BusinessLogoService logos;

    public PublicImageController(ProductImageService images, com.ultimate.inventory.Service.BusinessLogoService logos) {
        this.images = images;
        this.logos = logos;
    }

    /** Company logos, shown on invitations and in the app frame. The key changes on every upload. */
    @GetMapping("/public/business-logos/{key}")
    public ResponseEntity<byte[]> businessLogo(@PathVariable String key) {
        return logos.byKey(key)
                .map(l -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(l.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(l.getData()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/public/product-images/{key}")
    public ResponseEntity<byte[]> productImage(@PathVariable String key) {
        return images.byKey(key)
                .map(i -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(i.getContentType()))
                        // The key changes whenever the picture does, so it can be cached for a long time.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(i.getData()))
                .orElse(ResponseEntity.notFound().build());
    }
}
