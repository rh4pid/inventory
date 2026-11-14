package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.DTO.CategoryDTO;
import com.ultimate.inventory.DTO.ProductDTO;
import com.ultimate.inventory.DTO.StockInDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.Unit;
import com.ultimate.inventory.Repository.CategoryRepository;
import com.ultimate.inventory.Repository.ProductRepository;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Repository.UnitRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.CategoryService;
import com.ultimate.inventory.Service.InventoryService;
import com.ultimate.inventory.Service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Products, optionally with the stock you already have at one location. Opening stock is recorded
 * like any stock in without a purchase: its value enters the books as stock the owner brought in.
 */
@Component
public class ProductImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("name", "Product name", "Must be new.", "Coca-Cola 500ml"),
            ImportColumn.optional("sku", "SKU", "Your own product code. Must be new.", "CC-500"),
            ImportColumn.optional("barcode", "Barcode", "", "5449000000996"),
            ImportColumn.optional("category", "Category", "A category name. New ones are created for you.", "Drinks"),
            ImportColumn.optional("unit", "Unit", "The unit it's counted in, from Settings → Units (name or symbol).", "Bottle"),
            ImportColumn.required("cost", "Cost price", "What one unit costs you.", "6.50"),
            ImportColumn.required("price", "Selling price", "What you sell one unit for.", "8.00"),
            ImportColumn.optional("reorder", "Reorder level", "Warn when stock falls to this.", "24"),
            ImportColumn.optional("description", "Description", "", ""),
            ImportColumn.optional("location", "Location", "Where the opening stock is (location name or code). Needed with an opening quantity.", "Osu branch"),
            ImportColumn.optional("quantity", "Opening quantity", "Stock you have there now.", "120"),
            ImportColumn.optional("batch", "Batch number", "For the opening stock, if you track batches.", "B2026-01"),
            ImportColumn.optional("expiry", "Expiry date", "For the opening stock (yyyy-mm-dd).", "2027-06-30"));

    private final BusinessAccessHelper access;
    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final UnitRepository unitRepo;
    private final ShopRepository shopRepo;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final InventoryService inventoryService;

    public ProductImporter(BusinessAccessHelper access, ProductRepository productRepo, CategoryRepository categoryRepo,
                           UnitRepository unitRepo, ShopRepository shopRepo, ProductService productService,
                           CategoryService categoryService, InventoryService inventoryService) {
        this.access = access;
        this.productRepo = productRepo;
        this.categoryRepo = categoryRepo;
        this.unitRepo = unitRepo;
        this.shopRepo = shopRepo;
        this.productService = productService;
        this.categoryService = categoryService;
        this.inventoryService = inventoryService;
    }

    @Override public ImportKind kind() { return ImportKind.PRODUCTS; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of(
                "Opening stock goes into one location per row. For stock at other locations, use Stock in after the upload.",
                "The value of opening stock (quantity × cost price) is recorded in your books as stock the owner brought into the business.");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_CATALOG);
    }

    private static String key(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);

        Set<String> names = new HashSet<>();
        Set<String> skus = new HashSet<>();
        productRepo.findByBusinessAndDeletedFalse(business, Pageable.unpaged()).forEach(p -> {
            names.add(key(p.getProductName()));
            if (p.getSku() != null && !p.getSku().isBlank()) skus.add(key(p.getSku()));
        });
        // Category name -> id; filled in as new categories get created while saving.
        Map<String, Long> categoryIds = new HashMap<>();
        categoryRepo.findByBusinessAndDeletedFalse(business).forEach(cat -> categoryIds.putIfAbsent(key(cat.getCategoryName()), cat.getId()));
        Map<String, Unit> units = new HashMap<>();
        for (Unit u : unitRepo.findByBusiness(business)) {
            if (u.getName() != null) units.putIfAbsent(key(u.getName()), u);
            if (u.getSymbol() != null) units.putIfAbsent(key(u.getSymbol()), u);
        }
        Map<String, Shop> locations = LocationImporter.locationsByName(shopRepo, business);
        Boolean canStockIn = null;

        Set<String> fileNames = new HashSet<>();
        Set<String> fileSkus = new HashSet<>();
        Set<String> newCategories = new TreeSet<>();
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String name = c.text("name", 200);
            String sku = c.text("sku", 80);
            String barcode = c.text("barcode", 80);
            String category = c.text("category", 120);
            String unitName = c.text("unit");
            BigDecimal cost = c.positive("cost");
            BigDecimal price = c.positive("price");
            Integer reorder = c.wholeNumber("reorder");
            String description = c.text("description", 1000);
            String locationName = c.text("location");
            BigDecimal quantity = c.positive("quantity");
            String batch = c.text("batch", 80);
            LocalDate expiry = c.date("expiry");

            if (name != null) {
                if (names.contains(key(name))) c.error("name", "You already have a product called " + name);
                else if (!fileNames.add(key(name))) c.error("name", name + " appears more than once in this file");
            }
            if (sku != null) {
                if (skus.contains(key(sku))) c.error("sku", "SKU " + sku + " is already used");
                else if (!fileSkus.add(key(sku))) c.error("sku", "SKU " + sku + " appears more than once in this file");
            }
            Unit unit = null;
            if (unitName != null) {
                unit = units.get(key(unitName));
                if (unit == null) {
                    c.error("unit", "No unit called \"" + unitName + "\"" + (units.isEmpty() ? ". Add units under Settings → Units first."
                            : ". Units you have: " + String.join(", ", new TreeSet<>(units.keySet()))));
                }
            }
            if (category != null && !categoryIds.containsKey(key(category))) newCategories.add(category.trim());

            Shop shop = null;
            if (quantity != null && locationName == null) c.error("location", "Say where the opening quantity is");
            if (locationName != null && quantity == null) c.error("quantity", "Enter the opening quantity at " + locationName);
            if (locationName != null) {
                shop = locations.get(key(locationName));
                if (shop == null) c.error("location", "No location called \"" + locationName + "\"");
            }
            if (shop != null && quantity != null) {
                if (canStockIn == null) canStockIn = hasStockIn(businessId, email);
                if (!canStockIn) c.error("quantity", "Your role can't add stock. Leave opening quantities blank, or ask a manager.");
                else {
                    try {
                        access.requireShopAccess(shop.getId(), businessId, email);
                    } catch (AppException e) {
                        c.error("location", "You don't work at " + shop.getShopName());
                    }
                }
            }
            if (expiry != null && expiry.isBefore(LocalDate.now())) c.error("expiry", "This date has passed");
            if ((batch != null || expiry != null) && quantity == null) c.error("quantity", "Batch and expiry are for opening stock: enter a quantity too");
            if (!c.ok()) continue;

            ProductDTO dto = new ProductDTO();
            dto.setProductName(name);
            dto.setSku(sku);
            dto.setBarcode(barcode);
            dto.setDescription(description);
            dto.setCostPrice(cost.doubleValue());
            dto.setSellingPrice(price.doubleValue());
            dto.setReorderLevel(reorder);
            if (unit != null) dto.setUnitId(unit.getId());
            Long shopId = shop != null ? shop.getId() : null;
            plan.add(row.number(), r -> {
                if (category != null) {
                    dto.setCategoryId(categoryIds.computeIfAbsent(key(category), k -> {
                        CategoryDTO cd = new CategoryDTO();
                        cd.setCategoryName(category.trim());
                        return categoryService.createCategory(businessId, cd, email).getId();
                    }));
                }
                Product p = productService.createProduct(businessId, dto, email);
                if (quantity != null && shopId != null) {
                    StockInDTO in = new StockInDTO();
                    in.setProductId(p.getId());
                    in.setShopId(shopId);
                    in.setQuantity(quantity.doubleValue());
                    in.setCostPrice(cost.doubleValue());
                    in.setBatchNumber(batch);
                    in.setExpiryDate(expiry);
                    in.setNotes("Opening stock (uploaded)");
                    inventoryService.stockIn(businessId, in, email);
                }
                r.addCreated();
            });
        }
        if (!newCategories.isEmpty()) result.note("New categories will be created: " + String.join(", ", newCategories));
        return plan;
    }

    private boolean hasStockIn(Long businessId, String email) {
        try {
            access.requireCapability(businessId, email, Capability.STOCK_IN);
            return true;
        } catch (AppException e) {
            return false;
        }
    }
}
