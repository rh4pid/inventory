package com.ultimate.inventory.Service;

import com.ultimate.inventory.DTO.CategoryDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Category;
import com.ultimate.inventory.Repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepo;
    private final BusinessAccessHelper accessHelper;

    public CategoryService(CategoryRepository categoryRepo, BusinessAccessHelper accessHelper) {
        this.categoryRepo = categoryRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Category createCategory(Long businessId, CategoryDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Category category = new Category();
        category.setBusiness(business);
        category.setCategoryName(dto.getCategoryName());
        category.setCreatedBy(userEmail);
        category.setCreatedAt(new Date());

        if (dto.getParentId() != null) {
            Category parent = categoryRepo.findByIdAndBusinessAndDeletedFalse(dto.getParentId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Parent category not found"));
            category.setParent(parent);
        }

        Category saved = categoryRepo.save(category);
        log.info("Category created: {} by {}", saved.getId(), userEmail);
        return saved;
    }

    public List<Category> listCategories(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return categoryRepo.findByBusinessAndDeletedFalse(business);
    }

    public List<Category> listRootCategories(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return categoryRepo.findByBusinessAndParentIsNullAndDeletedFalse(business);
    }

    @Transactional
    public Category updateCategory(Long businessId, Long categoryId, CategoryDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Category category = categoryRepo.findByIdAndBusinessAndDeletedFalse(categoryId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Category not found"));

        category.setCategoryName(dto.getCategoryName());
        category.setUpdatedBy(userEmail);
        category.setUpdatedAt(new Date());

        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(categoryId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Category cannot be its own parent");
            }
            Category parent = categoryRepo.findByIdAndBusinessAndDeletedFalse(dto.getParentId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Parent category not found"));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return categoryRepo.save(category);
    }

    @Transactional
    public void deleteCategory(Long businessId, Long categoryId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        Category category = categoryRepo.findByIdAndBusinessAndDeletedFalse(categoryId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Category not found"));

        category.setDeleted(true);
        category.setUpdatedBy(userEmail);
        category.setUpdatedAt(new Date());
        categoryRepo.save(category);
        log.info("Category soft-deleted: {} by {}", categoryId, userEmail);
    }
}
