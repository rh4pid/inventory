package com.ultimate.inventory.Service;


import com.ultimate.inventory.Constants.Capability;import com.ultimate.inventory.DTO.ProductDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final UnitRepository unitRepo;
    private final TaxRateRepository taxRateRepo;
    private final BusinessAccessHelper accessHelper;
    private final InventoryRepository inventoryRepo;

    public ProductService(ProductRepository productRepo, CategoryRepository categoryRepo,
                          UnitRepository unitRepo, TaxRateRepository taxRateRepo,
                          BusinessAccessHelper accessHelper, InventoryRepository inventoryRepo) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
        this.unitRepo = unitRepo;
        this.taxRateRepo = taxRateRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Product createProduct(Long businessId, ProductDTO dto, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CATALOG);
        Business business = accessHelper.requireBusiness(businessId);

        if (dto.getSku() != null && !dto.getSku().isBlank()) {
            productRepo.findByBusinessAndSku(business, dto.getSku()).ifPresent(p -> {
                throw new AppException(HttpStatus.CONFLICT, "SKU already exists in this business");
            });
        }

        Product product = new Product();
        product.setBusiness(business);
        product.setCreatedBy(userEmail);
        product.setCreatedAt(new Date());
        applyDto(product, dto, business, userEmail);
        Product saved = productRepo.save(product);
        log.info("Product created: {} by {}", saved.getId(), userEmail);
        return saved;
    }

    @Transactional
    public Product updateProduct(Long businessId, Long productId, ProductDTO dto, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CATALOG);
        Business business = accessHelper.requireBusiness(businessId);

        Product product = productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));

        if (dto.getSku() != null && !dto.getSku().isBlank() &&
                !dto.getSku().equals(product.getSku())) {
            productRepo.findByBusinessAndSku(business, dto.getSku()).ifPresent(p -> {
                throw new AppException(HttpStatus.CONFLICT, "SKU already exists in this business");
            });
        }

        product.setUpdatedBy(userEmail);
        product.setUpdatedAt(new Date());
        applyDto(product, dto, business, userEmail);
        return productRepo.save(product);
    }

    public Product getProduct(Long businessId, Long productId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public Page<Product> listProducts(Long businessId, String search, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        if (search != null && !search.isBlank()) {
            return productRepo.findByBusinessAndProductNameContainingIgnoreCaseAndDeletedFalse(
                    business, search, pageable);
        }
        return productRepo.findByBusinessAndDeletedFalse(business, pageable);
    }

    @Transactional
    public void deleteProduct(Long businessId, Long productId, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CATALOG);
        Business business = accessHelper.requireBusiness(businessId);
        Product product = productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));
        // Deleting hides the product everywhere, so its stock would silently vanish from the books.
        String holding = inventoryRepo.findByProduct(product).stream()
                .filter(i -> Math.abs(i.getQuantity()) > 1e-9)
                .map(i -> InventoryService.fmt(i.getQuantity()) + " at " + i.getShop().getShopName())
                .collect(java.util.stream.Collectors.joining(", "));
        if (!holding.isEmpty()) {
            throw new AppException(HttpStatus.CONFLICT, "Cannot delete '" + product.getProductName() +
                    "' while it still has stock (" + holding + "). Write the stock off, transfer it, " +
                    "or count it to zero first.");
        }
        product.setDeleted(true);
        product.setUpdatedBy(userEmail);
        product.setUpdatedAt(new Date());
        productRepo.save(product);
        log.info("Product soft-deleted: {} by {}", productId, userEmail);
    }

    private void applyDto(Product product, ProductDTO dto, Business business, String userEmail) {
        product.setProductName(dto.getProductName());
        product.setDescription(dto.getDescription());
        product.setSku(dto.getSku());
        product.setBarcode(dto.getBarcode());
        product.setCostPrice(dto.getCostPrice());
        product.setSellingPrice(dto.getSellingPrice());
        product.setDiscountType(dto.getDiscountType());
        product.setDiscountValue(dto.getDiscountValue());
        product.setReorderLevel(dto.getReorderLevel());

        if (dto.getCategoryId() != null) {
            Category cat = categoryRepo.findByIdAndBusinessAndDeletedFalse(dto.getCategoryId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Category not found in this business"));
            product.setCategory(cat);
        } else {
            product.setCategory(null);
        }

        if (dto.getUnitId() != null) {
            Unit unit = unitRepo.findByIdAndBusiness(dto.getUnitId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Unit not found in this business"));
            product.setUnit(unit);
        } else {
            product.setUnit(null);
        }

        if (dto.getTaxRateId() != null) {
            TaxRate taxRate = taxRateRepo.findByIdAndBusiness(dto.getTaxRateId(), business)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Tax rate not found in this business"));
            product.setTaxRate(taxRate);
        } else {
            product.setTaxRate(null);
        }

        if (dto.getDiscountValue() != null && dto.getSellingPrice() != null) {
            double discount = "PERCENT".equalsIgnoreCase(dto.getDiscountType())
                    ? dto.getSellingPrice() * (dto.getDiscountValue() / 100)
                    : (dto.getDiscountValue() != null ? dto.getDiscountValue() : 0);
            product.setActualPrice(dto.getSellingPrice() - discount);
        } else {
            product.setActualPrice(dto.getSellingPrice());
        }
    }
}
