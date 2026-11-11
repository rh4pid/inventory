package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.CategoryDTO;
import com.ultimate.inventory.Model.Category;
import com.ultimate.inventory.Service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/businesses/{businessId}/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid CategoryDTO dto,
                                    @AuthenticationPrincipal String email) {
        Category category = categoryService.createCategory(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @RequestParam(defaultValue = "false") boolean rootOnly,
                                  @AuthenticationPrincipal String email) {
        List<Category> categories = rootOnly
                ? categoryService.listRootCategories(businessId, email)
                : categoryService.listCategories(businessId, email);
        return ResponseEntity.ok(categories);
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<?> update(@PathVariable Long businessId,
                                    @PathVariable Long categoryId,
                                    @RequestBody @Valid CategoryDTO dto,
                                    @AuthenticationPrincipal String email) {
        Category category = categoryService.updateCategory(businessId, categoryId, dto, email);
        return ResponseEntity.ok(category);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<?> delete(@PathVariable Long businessId,
                                    @PathVariable Long categoryId,
                                    @AuthenticationPrincipal String email) {
        categoryService.deleteCategory(businessId, categoryId, email);
        return ResponseEntity.ok(Map.of("success", true, "message", "Category deleted"));
    }
}
