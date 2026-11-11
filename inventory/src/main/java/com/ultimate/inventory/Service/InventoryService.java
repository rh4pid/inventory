package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.StockMovementType;
import com.ultimate.inventory.DTO.*;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final StockMovementRepository movementRepo;
    private final BusinessAccessHelper accessHelper;

    public InventoryService(InventoryRepository inventoryRepo, ProductRepository productRepo,
                            StockMovementRepository movementRepo, BusinessAccessHelper accessHelper) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.movementRepo = movementRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Inventory stockIn(Long businessId, StockInDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopInBusiness(dto.getShopId(), businessId);
        Product product = requireProductInBusiness(dto.getProductId(), business);
        return addInventory(product, shop, dto.getQuantity(), StockMovementType.STOCK_IN,
                dto.getSupplierId(), dto.getNotes(), userEmail);
    }

    @Transactional
    public Inventory stockOut(Long businessId, StockOutDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopInBusiness(dto.getShopId(), businessId);
        Product product = requireProductInBusiness(dto.getProductId(), business);
        return deductInventory(product, shop, dto.getQuantity(), StockMovementType.STOCK_OUT,
                dto.getNotes(), userEmail);
    }

    @Transactional
    public Map<String, Inventory> transfer(Long businessId, StockTransferDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);

        if (dto.getFromShopId().equals(dto.getToShopId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Source and destination shops must be different");
        }

        Shop fromShop = accessHelper.requireShopInBusiness(dto.getFromShopId(), businessId);
        Shop toShop = accessHelper.requireShopInBusiness(dto.getToShopId(), businessId);
        Product product = requireProductInBusiness(dto.getProductId(), business);

        Inventory savedFrom = deductInventory(product, fromShop, dto.getQuantity(),
                StockMovementType.TRANSFER_OUT, dto.getNotes(), userEmail);
        Inventory savedTo = addInventory(product, toShop, dto.getQuantity(),
                StockMovementType.TRANSFER_IN, null, dto.getNotes(), userEmail);

        log.info("Transfer: product={} from={} to={} qty={} by {}",
                product.getId(), fromShop.getId(), toShop.getId(), dto.getQuantity(), userEmail);

        Map<String, Inventory> result = new HashMap<>();
        result.put("from", savedFrom);
        result.put("to", savedTo);
        return result;
    }

    @Transactional
    public Inventory adjust(Long businessId, StockAdjustmentDTO dto, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopInBusiness(dto.getShopId(), businessId);
        Product product = requireProductInBusiness(dto.getProductId(), business);

        Inventory inventory = inventoryRepo.findByProductAndShop(product, shop)
                .orElseGet(() -> createInventory(product, shop, userEmail));

        double qBefore = inventory.getQuantity();
        double qChange = dto.getNewQuantity() - qBefore;
        inventory.setQuantity(dto.getNewQuantity());
        inventory.setUpdatedBy(userEmail);
        inventory.setUpdatedAt(new Date());
        Inventory saved = inventoryRepo.save(inventory);

        recordMovement(product, shop, StockMovementType.ADJUSTMENT, qBefore, qChange,
                dto.getNewQuantity(), null, dto.getNotes(), userEmail);

        syncProductTotal(product);
        return saved;
    }

    public Page<Inventory> listShopInventory(Long businessId, Long shopId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Shop shop = accessHelper.requireShopInBusiness(shopId, businessId);
        return inventoryRepo.findByShop(shop, pageable);
    }

    public List<Inventory> getLowStock(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return inventoryRepo.findLowStockByBusiness(business);
    }

    // ── Internal helpers called by SaleService / PurchaseService ─────────────

    @Transactional
    public Inventory deductInventory(Product product, Shop shop, Double quantity,
                                     StockMovementType movementType, String notes, String performedBy) {
        Inventory inventory = inventoryRepo.findByProductAndShop(product, shop)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "No inventory for product '" + product.getProductName() + "' in shop '" + shop.getShopName() + "'"));

        if (inventory.getQuantity() < quantity) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Insufficient stock for '" + product.getProductName() +
                    "'. Available: " + inventory.getQuantity() + ", Requested: " + quantity);
        }

        double qBefore = inventory.getQuantity();
        inventory.setQuantity(qBefore - quantity);
        inventory.setUpdatedBy(performedBy);
        inventory.setUpdatedAt(new Date());
        Inventory saved = inventoryRepo.save(inventory);

        recordMovement(product, shop, movementType, qBefore, -quantity, saved.getQuantity(),
                null, notes, performedBy);
        syncProductTotal(product);
        return saved;
    }

    @Transactional
    public Inventory addInventory(Product product, Shop shop, Double quantity,
                                  StockMovementType movementType, Long supplierId,
                                  String notes, String performedBy) {
        Inventory inventory = inventoryRepo.findByProductAndShop(product, shop)
                .orElseGet(() -> createInventory(product, shop, performedBy));

        double qBefore = inventory.getQuantity();
        inventory.setQuantity(qBefore + quantity);
        inventory.setUpdatedBy(performedBy);
        inventory.setUpdatedAt(new Date());
        Inventory saved = inventoryRepo.save(inventory);

        recordMovement(product, shop, movementType, qBefore, quantity, saved.getQuantity(),
                supplierId, notes, performedBy);
        syncProductTotal(product);
        return saved;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Inventory createInventory(Product product, Shop shop, String userEmail) {
        Inventory inv = new Inventory();
        inv.setProduct(product);
        inv.setShop(shop);
        inv.setQuantity(0.0);
        inv.setCreatedBy(userEmail);
        inv.setCreatedAt(new Date());
        return inventoryRepo.save(inv);
    }

    private void recordMovement(Product product, Shop shop, StockMovementType type,
                                double qBefore, double qChange, double qAfter,
                                Long supplierId, String notes, String performedBy) {
        StockMovement mv = new StockMovement();
        mv.setProduct(product);
        mv.setShop(shop);
        mv.setMovementType(type);
        mv.setQuantityBefore(qBefore);
        mv.setQuantityChange(qChange);
        mv.setQuantityAfter(qAfter);
        mv.setSupplierId(supplierId);
        mv.setNotes(notes);
        mv.setPerformedBy(performedBy);
        mv.setCreatedAt(new Date());
        mv.setCreatedBy(performedBy);
        movementRepo.save(mv);
    }

    private void syncProductTotal(Product product) {
        Double total = inventoryRepo.sumQuantityByProduct(product);
        product.setTotalCount(total != null ? total : 0.0);
        product.setUpdatedAt(new Date());
        productRepo.save(product);
    }

    private Product requireProductInBusiness(Long productId, Business business) {
        return productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found in this business"));
    }
}
