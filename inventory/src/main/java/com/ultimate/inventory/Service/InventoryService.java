package com.ultimate.inventory.Service;


import com.ultimate.inventory.Constants.Capability;import com.ultimate.inventory.Constants.StockMovementType;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.StockAdjustmentDTO;
import com.ultimate.inventory.DTO.StockInDTO;
import com.ultimate.inventory.DTO.StockOutDTO;
import com.ultimate.inventory.DTO.StockTransferDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import com.ultimate.inventory.Service.BatchService.BatchInfo;
import com.ultimate.inventory.Service.BatchService.BatchView;
import com.ultimate.inventory.Service.BatchService.Slice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Every change to a stock level goes through {@link #receive}, {@link #deduct}, {@link #count} or
 * {@link #restore}. Each locks the stock line, applies the change in the product's base unit and
 * writes {@link StockMovement} records. When the business tracks batches, stock is also held in
 * {@link StockBatch} lots: receipts create a batch, and deductions take from batches
 * earliest-expiry-first (or oldest-first), skipping expired stock unless a batch is chosen.
 * <p>
 * When the business requires approval, stock-outs and adjustments by non-managers are saved as
 * {@link StockRequest}s instead and applied by {@link StockRequestService} once approved.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String OPENING = "Opening stock";

    /** How a quantity was entered by the user, kept on the movement for traceability. */
    public record Entered(Double quantity, String unit) {
        static final Entered NONE = new Entered(null, null);
        static Entered of(double qty, UnitService.Conversion c) {
            return c.isBase() ? NONE : new Entered(qty, c.unitLabel());
        }
    }

    /** Who/why of a stock change, copied onto every movement it writes. */
    public record Ctx(String notes, String performedBy, String approvedBy, String reference, Entered entered) {
        public static Ctx of(String notes, String performedBy) {
            return new Ctx(notes, performedBy, null, null, Entered.NONE);
        }
        public Ctx withEntered(Entered e) { return new Ctx(notes, performedBy, approvedBy, reference, e); }
        public Ctx withReference(String ref) { return new Ctx(notes, performedBy, approvedBy, ref, entered); }
        public Ctx withNotes(String n) { return new Ctx(n, performedBy, approvedBy, reference, entered); }
        Ctx first(boolean isFirst) { return isFirst ? this : withEntered(Entered.NONE); }
    }

    /** Result of a deduction: the stock line after it, and which batches it came from. */
    public record Deduction(Inventory inventory, List<Slice> slices) {
        /** Actual cost of what was taken, using batch cost where known and {@code fallbackUnitCost} otherwise. */
        public double cost(double fallbackUnitCost) {
            return slices.stream().mapToDouble(s -> s.quantity() *
                    (s.batch() != null && s.batch().getUnitCost() != null ? s.batch().getUnitCost() : fallbackUnitCost))
                    .sum();
        }
        /** e.g. "LOT-A12 x3, LOT-B07 x2"; null when no batches were involved. */
        public String batchSummary() {
            return summarize(slices);
        }
    }

    /** Outcome of a stock-out or adjustment: applied ({@code inventory}) or waiting for approval ({@code pending}). */
    public record ActionResult(Inventory inventory, StockRequest pending) {
        static ActionResult done(Inventory i) { return new ActionResult(i, null); }
        static ActionResult waiting(StockRequest r) { return new ActionResult(null, r); }
    }

    private final InventoryRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final StockMovementRepository movementRepo;
    private final StockBatchRepository batchRepo;
    private final StockRequestRepository requestRepo;
    private final BusinessAccessHelper accessHelper;
    private final UnitService unitService;
    private final SettingsService settingsService;
    private final BatchService batchService;

    public InventoryService(InventoryRepository inventoryRepo, ProductRepository productRepo,
                            StockMovementRepository movementRepo, StockBatchRepository batchRepo,
                            StockRequestRepository requestRepo, BusinessAccessHelper accessHelper,
                            UnitService unitService, SettingsService settingsService, BatchService batchService) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.movementRepo = movementRepo;
        this.batchRepo = batchRepo;
        this.requestRepo = requestRepo;
        this.accessHelper = accessHelper;
        this.unitService = unitService;
        this.settingsService = settingsService;
        this.batchService = batchService;
    }

    // ── API operations ───────────────────────────────────────────────────────

    @Transactional
    public Inventory stockIn(Long businessId, StockInDTO dto, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.STOCK_IN);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopAccess(dto.getShopId(), businessId, member);
        Product product = requireProductInBusiness(dto.getProductId(), business);
        UnitService.Conversion c = unitService.resolve(product, dto.getUnitId());
        requireNotExpired(dto.getExpiryDate());
        Double baseCost = dto.getCostPrice() != null ? dto.getCostPrice() / c.factor() : null;
        BatchInfo info = BatchInfo.of(dto.getBatchNumber(), dto.getExpiryDate(), baseCost,
                dto.getSupplierId(), "Stock in");
        return receive(product, shop, c.toBase(dto.getQuantity()), StockMovementType.STOCK_IN, dto.getSupplierId(),
                Ctx.of(dto.getNotes(), userEmail).withEntered(Entered.of(dto.getQuantity(), c)), info);
    }

    /** Stock leaving without a sale (write-off, damage…). Needs approval for non-managers when switched on. */
    @Transactional
    public ActionResult stockOut(Long businessId, StockOutDTO dto, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.STOCK_OUT);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopAccess(dto.getShopId(), businessId, member);
        Product product = requireProductInBusiness(dto.getProductId(), business);
        UnitService.Conversion c = unitService.resolve(product, dto.getUnitId());
        if (dto.getBatchId() != null) batchService.requireBatch(dto.getBatchId(), product, shop);

        if (needsApproval(business, member)) {
            StockRequest r = newRequest(business, shop, product, StockMovementType.STOCK_OUT, dto.getReason(),
                    dto.getNotes(), dto.getBatchId(), userEmail);
            r.setQuantity(dto.getQuantity());
            r.setUnitId(dto.getUnitId());
            r.setSummary(fmt(dto.getQuantity()) + " × " + (c.unitLabel() != null ? c.unitLabel() : "units"));
            return ActionResult.waiting(requestRepo.save(r));
        }
        return ActionResult.done(executeStockOut(product, shop, dto.getQuantity(), dto.getUnitId(), dto.getBatchId(),
                Ctx.of(withReason(dto.getReason(), dto.getNotes()), userEmail)));
    }

    /** Applies a stock-out now (directly, or when its request is approved). */
    @Transactional
    public Inventory executeStockOut(Product product, Shop shop, double enteredQty, Long unitId, Long batchId, Ctx ctx) {
        UnitService.Conversion c = unitService.resolve(product, unitId);
        return deduct(product, shop, c.toBase(enteredQty), StockMovementType.STOCK_OUT,
                ctx.withEntered(Entered.of(enteredQty, c)), batchId).inventory();
    }

    @Transactional
    public Map<String, Inventory> transfer(Long businessId, StockTransferDTO dto, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.TRANSFER);
        Business business = accessHelper.requireBusiness(businessId);
        if (dto.getFromShopId().equals(dto.getToShopId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Source and destination locations must be different");
        }
        Shop fromShop = accessHelper.requireShopAccess(dto.getFromShopId(), businessId, member);
        Shop toShop = accessHelper.requireShopInBusiness(dto.getToShopId(), businessId);
        Product product = requireProductInBusiness(dto.getProductId(), business);
        UnitService.Conversion c = unitService.resolve(product, dto.getUnitId());
        double baseQty = c.toBase(dto.getQuantity());
        Entered entered = Entered.of(dto.getQuantity(), c);

        Deduction out = deduct(product, fromShop, baseQty, StockMovementType.TRANSFER_OUT,
                Ctx.of(withReason("To " + toShop.getShopName(), dto.getNotes()), userEmail).withEntered(entered),
                dto.getBatchId());

        // The same lots arrive at the destination, keeping their number, expiry, cost and age.
        Ctx inCtx = Ctx.of(withReason("From " + fromShop.getShopName(), dto.getNotes()), userEmail);
        Inventory savedTo = null;
        if (out.slices().isEmpty()) {
            savedTo = receive(product, toShop, baseQty, StockMovementType.TRANSFER_IN, null,
                    inCtx.withEntered(entered), null);
        } else {
            for (Slice s : out.slices()) {
                BatchInfo info = s.batch() != null
                        ? BatchInfo.copyOf(s.batch(), "Transfer from " + fromShop.getShopName())
                        : null;
                savedTo = receive(product, toShop, s.quantity(), StockMovementType.TRANSFER_IN, null,
                        savedTo == null ? inCtx.withEntered(entered) : inCtx, info);
            }
        }

        log.info("Transfer: product={} from={} to={} qty={} by {}",
                product.getId(), fromShop.getId(), toShop.getId(), baseQty, userEmail);
        Map<String, Inventory> result = new HashMap<>();
        result.put("from", out.inventory());
        result.put("to", savedTo);
        return result;
    }

    /** Sets stock to a counted quantity. Needs approval for non-managers when switched on. */
    @Transactional
    public ActionResult adjust(Long businessId, StockAdjustmentDTO dto, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.ADJUST);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopAccess(dto.getShopId(), businessId, member);
        Product product = requireProductInBusiness(dto.getProductId(), business);
        if (dto.getBatchId() != null && !tracksBatches(business)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Batch tracking is not switched on");
        }
        StockBatch batch = dto.getBatchId() != null ? batchService.requireBatch(dto.getBatchId(), product, shop) : null;
        requireNotExpired(dto.getExpiryDate());

        if (needsApproval(business, member)) {
            // Remember what the system showed: approval applies the difference to the stock at that time.
            double expected = batch != null ? batch.getQuantityRemaining()
                    : inventoryRepo.findByProductAndShop(product, shop).map(Inventory::getQuantity).orElse(0.0);
            StockRequest r = newRequest(business, shop, product, StockMovementType.ADJUSTMENT, dto.getReason(),
                    dto.getNotes(), dto.getBatchId(), userEmail);
            r.setCountedQuantity(dto.getNewQuantity());
            r.setExpectedQuantity(expected);
            r.setBatchNumber(dto.getBatchNumber());
            r.setExpiryDate(dto.getExpiryDate());
            String unit = product.getUnit() != null ? " " + product.getUnit().getName() : "";
            r.setSummary("Counted " + fmt(dto.getNewQuantity()) + unit + " (system " + fmt(expected) + unit + ")"
                    + (batch != null ? " in batch " + batch.getBatchNumber() : ""));
            return ActionResult.waiting(requestRepo.save(r));
        }
        return ActionResult.done(count(product, shop, dto.getBatchId(), dto.getNewQuantity(), null,
                dto.getBatchNumber(), dto.getExpiryDate(),
                Ctx.of(withReason(dto.getReason(), dto.getNotes()), userEmail)));
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public Page<Inventory> listShopInventory(Long businessId, Long shopId, String userEmail, Pageable pageable) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Shop shop = accessHelper.requireShopAccess(shopId, businessId, member);
        return inventoryRepo.findByShop(shop, pageable);
    }

    public List<Inventory> getLowStock(Long businessId, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Set<Long> allowed = accessHelper.allowedShopIds(member);
        return inventoryRepo.findLowStockByBusiness(business).stream()
                .filter(i -> allowed == null || allowed.contains(i.getShop().getId())).toList();
    }

    /** Stock movement history, newest first, optionally for one location and/or product. */
    public Page<StockMovement> listMovements(Long businessId, Long shopId, Long productId,
                                             String userEmail, Pageable pageable) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = shopId != null ? accessHelper.requireShopAccess(shopId, businessId, member) : null;
        Product product = productId != null ? requireProductInBusiness(productId, business) : null;
        Set<Long> allowed = accessHelper.allowedShopIds(member);
        if (shop == null && allowed != null) {
            // Staff only see the history of the locations they work at.
            if (allowed.isEmpty()) return Page.empty(pageable);
            return movementRepo.searchInShops(business, allowed, product, pageable);
        }
        return movementRepo.search(business, shop, product, pageable);
    }

    /** Batches holding stock, optionally for one location and/or product. */
    @Transactional
    public List<BatchView> listBatches(Long businessId, Long shopId, Long productId,
                                       boolean includeEmpty, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = shopId != null ? accessHelper.requireShopAccess(shopId, businessId, member) : null;
        Set<Long> allowed = accessHelper.allowedShopIds(member);
        Product product = productId != null ? requireProductInBusiness(productId, business) : null;
        // Bring stock that predates batch tracking into "Opening stock" batches so the list adds up.
        if (shop != null && tracksBatches(business)) {
            List<Inventory> lines = product != null
                    ? inventoryRepo.findByProductAndShop(product, shop).map(List::of).orElse(List.of())
                    : inventoryRepo.findAllByShop(shop);
            for (Inventory line : lines) {
                if (Boolean.TRUE.equals(line.getProduct().getDeleted())) continue;
                inventoryRepo.findForUpdate(line.getProduct(), shop)
                        .ifPresent(inv -> batchService.reconcile(inv, OPENING, userEmail));
            }
        }
        int alertDays = alertDays(business);
        return batchRepo.search(business, shop, product, includeEmpty).stream()
                .filter(b -> allowed == null || allowed.contains(b.getShop().getId()))
                .map(b -> batchService.view(b, alertDays)).toList();
    }

    /** Batches already expired or expiring within {@code days} (default: the business alert window). */
    public List<BatchView> listExpiring(Long businessId, Long shopId, Integer days, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = shopId != null ? accessHelper.requireShopAccess(shopId, businessId, member) : null;
        Set<Long> allowed = accessHelper.allowedShopIds(member);
        int alertDays = alertDays(business);
        int window = days != null ? Math.max(0, days) : alertDays;
        return batchRepo.findExpiring(business, shop, LocalDate.now().plusDays(window)).stream()
                .filter(b -> allowed == null || allowed.contains(b.getShop().getId()))
                .map(b -> batchService.view(b, alertDays)).toList();
    }

    // ── Core stock operations (also used by sales, purchases, approvals and counts) ──

    /**
     * Removes {@code quantity} base units from a location. With batches tracked, takes from
     * {@code batchId} if given, otherwise earliest-expiry-first skipping expired stock.
     */
    @Transactional
    public Deduction deduct(Product product, Shop shop, double quantity, StockMovementType movementType,
                            Ctx ctx, Long batchId) {
        BusinessSettings settings = settingsService.forBusiness(shop.getBusiness());
        boolean allowNegative = Boolean.TRUE.equals(settings.getAllowNegativeStock());
        boolean batches = Boolean.TRUE.equals(settings.getTrackBatches());
        boolean fefo = Boolean.TRUE.equals(settings.getTrackExpiry());

        Inventory inventory = allowNegative
                ? lockOrCreate(product, shop, ctx.performedBy())
                : inventoryRepo.findForUpdate(product, shop).orElseThrow(() -> new AppException(HttpStatus.CONFLICT,
                        "'" + product.getProductName() + "' has no stock in " + shop.getShopName()));
        double qBefore = inventory.getQuantity();

        List<Slice> slices = new ArrayList<>();
        if (batches) {
            batchService.reconcile(inventory, OPENING, ctx.performedBy());
            StockBatch chosen = batchId != null ? batchService.requireBatch(batchId, product, shop) : null;
            double available = chosen != null ? chosen.getQuantityRemaining()
                    : batchService.available(product, shop, fefo);
            if (!allowNegative && available < quantity - 1e-9) {
                throw insufficient(product, shop, quantity, available, chosen,
                        chosen == null && fefo ? Math.max(0, qBefore - available) : 0);
            }
            slices.addAll(batchService.pick(product, shop, quantity, chosen, fefo, fefo));
            double taken = slices.stream().mapToDouble(Slice::quantity).sum();
            if (quantity - taken > 1e-9) slices.add(new Slice(null, quantity - taken)); // beyond batches (negative stock)
        } else if (!allowNegative && qBefore < quantity - 1e-9) {
            throw insufficient(product, shop, quantity, qBefore, null, 0);
        }

        Inventory saved = setQuantity(inventory, qBefore - quantity, ctx.performedBy());
        if (slices.isEmpty()) {
            recordMovement(product, shop, movementType, qBefore, -quantity, saved.getQuantity(), null, ctx, null);
        } else {
            // One movement per batch so each lot's history is traceable.
            double running = qBefore;
            for (int i = 0; i < slices.size(); i++) {
                Slice s = slices.get(i);
                recordMovement(product, shop, movementType, running, -s.quantity(), running - s.quantity(),
                        null, ctx.first(i == 0), s.batch());
                running -= s.quantity();
            }
            batchService.reconcile(saved, OPENING, ctx.performedBy());
        }
        syncProductTotal(product);
        return new Deduction(saved, slices);
    }

    /** Adds {@code quantity} base units to a location; with batches tracked, as a new batch. */
    @Transactional
    public Inventory receive(Product product, Shop shop, double quantity, StockMovementType movementType,
                             Long supplierId, Ctx ctx, BatchInfo info) {
        boolean batches = tracksBatches(shop.getBusiness());
        Inventory inventory = lockOrCreate(product, shop, ctx.performedBy());
        if (batches) batchService.reconcile(inventory, OPENING, ctx.performedBy());

        double qBefore = inventory.getQuantity();
        Inventory saved = setQuantity(inventory, qBefore + quantity, ctx.performedBy());
        StockBatch batch = null;
        if (batches) {
            batch = batchService.receive(product, shop, quantity, info, ctx.performedBy());
            // If the location was negative, part of this delivery covers stock already sold.
            batchService.reconcile(saved, OPENING, ctx.performedBy());
        }
        recordMovement(product, shop, movementType, qBefore, quantity, saved.getQuantity(), supplierId, ctx, batch);
        syncProductTotal(product);
        return saved;
    }

    /**
     * Puts stock back (e.g. a voided sale), into the original batch when it is still at this
     * location, otherwise into a new batch.
     */
    @Transactional
    public Inventory restore(Product product, Shop shop, double quantity, StockMovementType movementType,
                             Ctx ctx, Long batchId) {
        boolean batches = tracksBatches(shop.getBusiness());
        Inventory inventory = lockOrCreate(product, shop, ctx.performedBy());
        if (batches) batchService.reconcile(inventory, OPENING, ctx.performedBy());

        double qBefore = inventory.getQuantity();
        Inventory saved = setQuantity(inventory, qBefore + quantity, ctx.performedBy());
        StockBatch batch = null;
        if (batches) {
            batch = batchId != null ? batchRepo.findById(batchId)
                    .filter(b -> b.getProduct().getId().equals(product.getId()) && b.getShop().getId().equals(shop.getId()))
                    .orElse(null) : null;
            if (batch != null) {
                batch.setQuantityRemaining(batch.getQuantityRemaining() + quantity);
                batchService.save(batch);
            } else {
                batch = batchService.receive(product, shop, quantity,
                        BatchInfo.of(null, null, null, null, "Returned"), ctx.performedBy());
            }
            batchService.reconcile(saved, OPENING, ctx.performedBy());
        }
        recordMovement(product, shop, movementType, qBefore, quantity, saved.getQuantity(), null, ctx, batch);
        syncProductTotal(product);
        return saved;
    }

    /**
     * Applies a physical count. Without {@code expected}, stock is set to {@code counted}.
     * With {@code expected} (what the system showed when counting), the difference
     * {@code counted − expected} is applied to the current stock, so sales made since then are kept.
     * With {@code batchId}, the count is for that batch only.
     */
    @Transactional
    public Inventory count(Product product, Shop shop, Long batchId, double counted, Double expected,
                           String batchNumber, LocalDate expiryDate, Ctx ctx) {
        boolean batches = tracksBatches(shop.getBusiness());
        Inventory inventory = lockOrCreate(product, shop, ctx.performedBy());
        if (batches) batchService.reconcile(inventory, OPENING, ctx.performedBy());
        double qBefore = inventory.getQuantity();
        double qAfter;
        StockBatch touched = null;

        if (batchId != null && batches) {
            touched = batchService.requireBatch(batchId, product, shop);
            double current = touched.getQuantityRemaining();
            double target = expected == null ? counted : Math.max(0, current + counted - expected);
            touched.setQuantityRemaining(target);
            batchService.save(touched);
            qAfter = qBefore + (target - current);
        } else {
            qAfter = expected == null ? counted : Math.max(0, qBefore + counted - expected);
        }

        Inventory saved = setQuantity(inventory, qAfter, ctx.performedBy());
        if (batches && touched == null && qAfter > qBefore) {
            // Found extra stock: it goes into a batch (with the counted lot details if given).
            touched = batchService.receive(product, shop, qAfter - Math.max(qBefore, 0),
                    BatchInfo.of(batchNumber, expiryDate, null, null, "Stock count"), ctx.performedBy());
        }
        if (Math.abs(qAfter - qBefore) > 1e-9 || touched != null) {
            recordMovement(product, shop, StockMovementType.ADJUSTMENT, qBefore, qAfter - qBefore, qAfter,
                    null, ctx, touched);
        }
        // Missing stock is removed from the oldest batches.
        if (batches) batchService.reconcile(saved, OPENING, ctx.performedBy());
        syncProductTotal(product);
        return saved;
    }

    /** Movement references used when stock comes back from (or goes back to) an invoice. */
    public static String returnRef(Invoice invoice) { return "RET-" + invoice.getInvoiceNumber(); }
    public static String voidRef(Invoice invoice)   { return "VOID-" + invoice.getInvoiceNumber(); }

    /**
     * A customer brings back {@code quantity} base units of what a sale took. The stock goes back
     * into the batches the sale took it from, minus whatever was already returned to each.
     * Falls back to the sale's location for sales recorded before movements carried a reference.
     */
    @Transactional
    public void returnFromSale(Invoice invoice, Product product, double quantity, Ctx ctx) {
        if (quantity <= 1e-9) return;
        List<StockMovement> taken = movementRepo.findByReferenceCodeAndProductAndMovementTypeOrderByIdAsc(
                invoice.getInvoiceNumber(), product, StockMovementType.SALE);
        Map<Long, Double> already = byBatch(movementRepo.findByReferenceCodeInAndProductAndMovementType(
                List.of(returnRef(invoice), voidRef(invoice)), product, StockMovementType.SALE_RETURN), 1);
        double left = quantity;
        for (StockMovement m : taken) {
            if (left <= 1e-9) break;
            double room = -m.getQuantityChange() - consume(already, m.getBatchId(), -m.getQuantityChange());
            if (room <= 1e-9) continue;
            double put = Math.min(room, left);
            restore(product, m.getShop(), put, StockMovementType.SALE_RETURN, ctx, m.getBatchId());
            left -= put;
        }
        if (left > 1e-9) {
            // Legacy sale (no movement references) or rounding: put the rest back at the sale's location.
            restore(product, invoice.getShop(), left, StockMovementType.SALE_RETURN, ctx, null);
        }
    }

    /**
     * {@code quantity} base units of what a purchase brought in go back to the supplier, taken
     * from the batches that purchase created. Fails (and changes nothing) if that stock has
     * already been sold or moved.
     */
    @Transactional
    public void returnToSupplier(Invoice invoice, Product product, double quantity, Ctx ctx) {
        if (quantity <= 1e-9) return;
        List<StockMovement> added = movementRepo.findByReferenceCodeAndProductAndMovementTypeOrderByIdAsc(
                invoice.getInvoiceNumber(), product, StockMovementType.STOCK_IN);
        Map<Long, Double> already = byBatch(movementRepo.findByReferenceCodeInAndProductAndMovementType(
                List.of(returnRef(invoice), voidRef(invoice)), product, StockMovementType.PURCHASE_RETURN), -1);
        double left = quantity;
        try {
            for (StockMovement m : added) {
                if (left <= 1e-9) break;
                double room = m.getQuantityChange() - consume(already, m.getBatchId(), m.getQuantityChange());
                if (room <= 1e-9) continue;
                double take = Math.min(room, left);
                deduct(product, m.getShop(), take, StockMovementType.PURCHASE_RETURN, ctx, m.getBatchId());
                left -= take;
            }
            if (left > 1e-9) {
                deduct(product, invoice.getShop(), left, StockMovementType.PURCHASE_RETURN, ctx, null);
            }
        } catch (AppException ex) {
            throw new AppException(HttpStatus.CONFLICT, "Cannot send back '" + product.getProductName() +
                    "': some of the stock this purchase added has already been used. " + ex.getMessage());
        }
    }

    /** Sum of movement quantities per batch (null batch included), signed so returns count positive. */
    private static Map<Long, Double> byBatch(List<StockMovement> moves, int sign) {
        Map<Long, Double> out = new HashMap<>();
        for (StockMovement m : moves) out.merge(m.getBatchId(), sign * m.getQuantityChange(), Double::sum);
        return out;
    }

    /** Takes up to {@code max} from the batch's already-returned tally and says how much it took. */
    private static double consume(Map<Long, Double> tally, Long batchId, double max) {
        double have = tally.getOrDefault(batchId, 0.0);
        double used = Math.min(have, max);
        if (used > 0) tally.put(batchId, have - used);
        return Math.max(used, 0);
    }

    public boolean tracksBatches(Business business) {
        return Boolean.TRUE.equals(settingsService.forBusiness(business).getTrackBatches());
    }

    /** True when this member's stock-outs and adjustments must wait for a manager. */
    public boolean needsApproval(Business business, BusinessUser member) {
        return Boolean.TRUE.equals(settingsService.forBusiness(business).getRequireAdjustmentApproval())
                && !isManager(member);
    }

    public static boolean isManager(BusinessUser member) {
        return BusinessAccessHelper.isManager(member);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private StockRequest newRequest(Business business, Shop shop, Product product, StockMovementType type,
                                    String reason, String notes, Long batchId, String by) {
        StockRequest r = new StockRequest();
        r.setBusiness(business);
        r.setShop(shop);
        r.setProduct(product);
        r.setRequestType(type);
        r.setReason(reason);
        r.setNotes(notes);
        r.setBatchId(batchId);
        r.setRequestedBy(by);
        r.setCreatedBy(by);
        r.setCreatedAt(new Date());
        log.info("Stock {} for product={} at shop={} sent for approval by {}", type, product.getId(), shop.getId(), by);
        return r;
    }

    private Inventory lockOrCreate(Product product, Shop shop, String userEmail) {
        return inventoryRepo.findForUpdate(product, shop).orElseGet(() -> {
            Inventory inv = new Inventory();
            inv.setProduct(product);
            inv.setShop(shop);
            inv.setQuantity(0.0);
            inv.setCreatedBy(userEmail);
            inv.setCreatedAt(new Date());
            return inventoryRepo.save(inv);
        });
    }

    private Inventory setQuantity(Inventory inventory, double quantity, String by) {
        inventory.setQuantity(quantity);
        inventory.setUpdatedBy(by);
        inventory.setUpdatedAt(new Date());
        return inventoryRepo.save(inventory);
    }

    private void recordMovement(Product product, Shop shop, StockMovementType type,
                                double qBefore, double qChange, double qAfter,
                                Long supplierId, Ctx ctx, StockBatch batch) {
        StockMovement mv = new StockMovement();
        mv.setProduct(product);
        mv.setShop(shop);
        mv.setMovementType(type);
        mv.setQuantityBefore(qBefore);
        mv.setQuantityChange(qChange);
        mv.setQuantityAfter(qAfter);
        mv.setEnteredQuantity(ctx.entered().quantity());
        mv.setEnteredUnit(ctx.entered().unit());
        if (batch != null) {
            mv.setBatchId(batch.getId());
            mv.setBatchNumber(batch.getBatchNumber());
        }
        mv.setReferenceCode(ctx.reference());
        mv.setSupplierId(supplierId);
        mv.setNotes(ctx.notes());
        mv.setPerformedBy(ctx.performedBy());
        mv.setApprovedBy(ctx.approvedBy());
        mv.setCreatedAt(new Date());
        mv.setCreatedBy(ctx.performedBy());
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

    private int alertDays(Business business) {
        Integer d = settingsService.forBusiness(business).getExpiryAlertDays();
        return d != null ? d : 30;
    }

    private static void requireNotExpired(LocalDate expiry) {
        if (expiry != null && expiry.isBefore(LocalDate.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Expiry date " + expiry + " is in the past");
        }
    }

    private static AppException insufficient(Product p, Shop shop, double requested, double available,
                                             StockBatch batch, double expiredQty) {
        String unit = p.getUnit() != null ? " " + p.getUnit().getName() : "";
        String where = batch != null ? "batch " + batch.getBatchNumber() : shop.getShopName();
        String msg = "Insufficient stock for '" + p.getProductName() + "' in " + where +
                ". Available: " + fmt(available) + unit + ", requested: " + fmt(requested) + unit;
        if (expiredQty > 1e-9) {
            msg += ". Another " + fmt(expiredQty) + unit + " is expired and cannot be used";
        }
        return new AppException(HttpStatus.CONFLICT, msg);
    }

    static String summarize(List<Slice> slices) {
        String s = slices.stream().filter(x -> x.batch() != null)
                .map(x -> x.batch().getBatchNumber() + " x" + fmt(x.quantity()))
                .collect(Collectors.joining(", "));
        return s.isEmpty() ? null : (s.length() > 500 ? s.substring(0, 497) + "..." : s);
    }

    static String withReason(String reason, String notes) {
        boolean hasReason = reason != null && !reason.isBlank();
        boolean hasNotes = notes != null && !notes.isBlank();
        if (hasReason && hasNotes) return reason.trim() + " | " + notes.trim();
        return hasReason ? reason.trim() : (hasNotes ? notes.trim() : null);
    }

    static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 1000) / 1000.0);
    }
}
