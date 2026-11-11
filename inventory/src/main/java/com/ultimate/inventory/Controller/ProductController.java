package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.ProductDTO;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/businesses/{businessId}/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid ProductDTO dto,
                                    @AuthenticationPrincipal String email) {
        Product product = productService.createProduct(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @RequestParam(required = false) String search,
                                  @PageableDefault(size = 20) Pageable pageable,
                                  @AuthenticationPrincipal String email) {
        Page<Product> page = productService.listProducts(businessId, search, email, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @PathVariable Long productId,
                                 @AuthenticationPrincipal String email) {
        Product product = productService.getProduct(businessId, productId, email);
        return ResponseEntity.ok(product);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<?> update(@PathVariable Long businessId,
                                    @PathVariable Long productId,
                                    @RequestBody @Valid ProductDTO dto,
                                    @AuthenticationPrincipal String email) {
        Product product = productService.updateProduct(businessId, productId, dto, email);
        return ResponseEntity.ok(product);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> delete(@PathVariable Long businessId,
                                    @PathVariable Long productId,
                                    @AuthenticationPrincipal String email) {
        productService.deleteProduct(businessId, productId, email);
        return ResponseEntity.ok(Map.of("success", true, "message", "Product deleted"));
    }
}
