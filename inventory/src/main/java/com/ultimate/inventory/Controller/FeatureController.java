package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.FeatureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Features (Color, Size, …) and products' values for them. */
@RestController
@RequestMapping("/businesses/{businessId}")
public class FeatureController {

    private final FeatureService features;

    public FeatureController(FeatureService features) {
        this.features = features;
    }

    public record FeatureName(String name) {}

    @GetMapping("/features")
    public ResponseEntity<?> list(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(features.list(businessId, email));
    }

    @PostMapping("/features")
    public ResponseEntity<?> create(@PathVariable Long businessId, @RequestBody FeatureName body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(features.create(businessId, body.name(), email));
    }

    @PutMapping("/features/{featureId}")
    public ResponseEntity<?> rename(@PathVariable Long businessId, @PathVariable Long featureId,
                                    @RequestBody FeatureName body, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(features.rename(businessId, featureId, body.name(), email));
    }

    @DeleteMapping("/features/{featureId}")
    public ResponseEntity<?> delete(@PathVariable Long businessId, @PathVariable Long featureId,
                                    @AuthenticationPrincipal String email) {
        features.delete(businessId, featureId, email);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/products/{productId}/features")
    public ResponseEntity<?> forProduct(@PathVariable Long businessId, @PathVariable Long productId,
                                        @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(features.forProduct(businessId, productId, email));
    }

    /** Replaces the product's features (leave one out, or send a blank value, to remove it). */
    @PutMapping("/products/{productId}/features")
    public ResponseEntity<?> setForProduct(@PathVariable Long businessId, @PathVariable Long productId,
                                           @RequestBody List<FeatureService.SetValue> values,
                                           @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(features.setForProduct(businessId, productId, values, email));
    }

    /** Features of several products: ?productIds=1,2,3 -> { "1": [...], "2": [...] }. */
    @GetMapping("/product-features")
    public ResponseEntity<?> forProducts(@PathVariable Long businessId, @RequestParam String productIds,
                                         @AuthenticationPrincipal String email) {
        List<Long> ids = Arrays.stream(productIds.split(",")).map(String::trim).filter(s -> s.matches("\\d+"))
                .limit(200).map(Long::valueOf).toList();
        return ResponseEntity.ok(features.forProducts(businessId, ids, email));
    }
}
