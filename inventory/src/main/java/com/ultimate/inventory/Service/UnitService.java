package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.ProductUnitDTO;
import com.ultimate.inventory.DTO.UnitDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Product;
import com.ultimate.inventory.Model.ProductUnit;
import com.ultimate.inventory.Model.Unit;
import com.ultimate.inventory.Repository.ProductRepository;
import com.ultimate.inventory.Repository.ProductUnitRepository;
import com.ultimate.inventory.Repository.UnitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class UnitService {

    /** Units every business starts with: {name, symbol}. */
    private static final String[][] DEFAULT_UNITS = {
            {"Piece", "pc"}, {"Pack", "pk"}, {"Box", "box"}, {"Carton", "ctn"}, {"Dozen", "dz"},
            {"Bottle", "btl"}, {"Bag", "bag"}, {"Kilogram", "kg"}, {"Gram", "g"},
            {"Litre", "L"}, {"Millilitre", "ml"}, {"Metre", "m"},
            {"Tablet", "tab"}, {"Strip", "strip"},
    };

    /** Result of converting an entered quantity into the product's base unit. */
    public record Conversion(double factor, String unitLabel, ProductUnit productUnit) {
        public double toBase(double qty) { return qty * factor; }
        public boolean isBase() { return productUnit == null; }
    }

    private final UnitRepository unitRepo;
    private final ProductUnitRepository productUnitRepo;
    private final ProductRepository productRepo;
    private final BusinessAccessHelper accessHelper;

    public UnitService(UnitRepository unitRepo, ProductUnitRepository productUnitRepo,
                       ProductRepository productRepo, BusinessAccessHelper accessHelper) {
        this.unitRepo = unitRepo;
        this.productUnitRepo = productUnitRepo;
        this.productRepo = productRepo;
        this.accessHelper = accessHelper;
    }

    // ── Units of measure ─────────────────────────────────────────────────────

    @Transactional
    public List<Unit> listUnits(Long businessId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        List<Unit> units = new ArrayList<>(unitRepo.findByBusiness(business));
        if (units.isEmpty()) {
            for (String[] d : DEFAULT_UNITS) units.add(newUnit(business, d[0], d[1], "system"));
        }
        units.sort(Comparator.comparing(Unit::getName, String.CASE_INSENSITIVE_ORDER));
        return units;
    }

    @Transactional
    public Unit createUnit(Long businessId, UnitDTO dto, String userEmail) {
        requireManager(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        String name = dto.getName().trim();
        if (unitRepo.existsByBusinessAndNameIgnoreCase(business, name)) {
            throw new AppException(HttpStatus.CONFLICT, "A unit named '" + name + "' already exists");
        }
        return newUnit(business, name, blankToNull(dto.getSymbol()), userEmail);
    }

    @Transactional
    public Unit updateUnit(Long businessId, Long unitId, UnitDTO dto, String userEmail) {
        requireManager(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Unit unit = requireUnit(unitId, business);
        String name = dto.getName().trim();
        if (!name.equalsIgnoreCase(unit.getName()) && unitRepo.existsByBusinessAndNameIgnoreCase(business, name)) {
            throw new AppException(HttpStatus.CONFLICT, "A unit named '" + name + "' already exists");
        }
        unit.setName(name);
        unit.setSymbol(blankToNull(dto.getSymbol()));
        unit.setUpdatedBy(userEmail);
        unit.setUpdatedAt(new Date());
        return unitRepo.save(unit);
    }

    @Transactional
    public void deleteUnit(Long businessId, Long unitId, String userEmail) {
        requireManager(businessId, userEmail);
        Unit unit = requireUnit(unitId, accessHelper.requireBusiness(businessId));
        if (productRepo.existsByUnit(unit) || productUnitRepo.existsByUnit(unit)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Unit '" + unit.getName() + "' is used by products and cannot be deleted");
        }
        unitRepo.delete(unit);
    }

    // ── Product pack sizes ───────────────────────────────────────────────────

    public List<ProductUnit> listProductUnits(Long businessId, Long productId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Product product = requireProduct(productId, accessHelper.requireBusiness(businessId));
        return productUnitRepo.findByProduct(product);
    }

    /** Replaces the full list of pack sizes for a product. */
    @Transactional
    public List<ProductUnit> setProductUnits(Long businessId, Long productId,
                                             List<ProductUnitDTO> dtos, String userEmail) {
        requireManager(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Product product = requireProduct(productId, business);
        if (!dtos.isEmpty() && product.getUnit() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Set the product's base unit before adding pack sizes");
        }
        Set<Long> seen = new HashSet<>();
        for (ProductUnitDTO d : dtos) {
            if (d.getUnitId() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Each pack size needs a unit");
            }
            if (d.getConversionFactor() == null || d.getConversionFactor() <= 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Pack size quantity must be greater than zero");
            }
            if (d.getSellingPrice() != null && d.getSellingPrice() <= 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Pack selling price must be greater than zero");
            }
            if (!seen.add(d.getUnitId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Each unit can only be listed once");
            }
            if (product.getUnit() != null && product.getUnit().getId().equals(d.getUnitId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "The base unit cannot also be a pack size");
            }
        }
        productUnitRepo.deleteByProduct(product);
        productUnitRepo.flush();
        List<ProductUnit> saved = new ArrayList<>();
        for (ProductUnitDTO d : dtos) {
            ProductUnit pu = new ProductUnit();
            pu.setProduct(product);
            pu.setUnit(requireUnit(d.getUnitId(), business));
            pu.setConversionFactor(d.getConversionFactor());
            pu.setBarcode(blankToNull(d.getBarcode()));
            pu.setSellingPrice(d.getSellingPrice());
            pu.setCreatedBy(userEmail);
            pu.setCreatedAt(new Date());
            saved.add(productUnitRepo.save(pu));
        }
        return saved;
    }

    /**
     * Resolves the unit a quantity was entered in. Null or the product's own base
     * unit gives factor 1; any other unit must be one of the product's pack sizes.
     */
    public Conversion resolve(Product product, Long unitId) {
        if (unitId == null || (product.getUnit() != null && product.getUnit().getId().equals(unitId))) {
            return new Conversion(1.0, product.getUnit() != null ? product.getUnit().getName() : null, null);
        }
        Unit unit = requireUnit(unitId, product.getBusiness());
        ProductUnit pu = productUnitRepo.findByProductAndUnit(product, unit)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,
                        "'" + unit.getName() + "' is not a pack size of '" + product.getProductName() + "'"));
        String base = product.getUnit() != null ? product.getUnit().getName() : "base";
        return new Conversion(pu.getConversionFactor(),
                unit.getName() + " (" + fmt(pu.getConversionFactor()) + " " + base + ")", pu);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Unit newUnit(Business business, String name, String symbol, String by) {
        Unit u = new Unit();
        u.setBusiness(business);
        u.setName(name);
        u.setSymbol(symbol);
        u.setCreatedBy(by);
        u.setCreatedAt(new Date());
        return unitRepo.save(u);
    }

    private void requireManager(Long businessId, String email) {
        accessHelper.requireRole(businessId, email, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
    }

    private Unit requireUnit(Long unitId, Business business) {
        return unitRepo.findByIdAndBusiness(unitId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Unit not found in this business"));
    }

    private Product requireProduct(Long productId, Business business) {
        return productRepo.findByIdAndBusinessAndDeletedFalse(productId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
