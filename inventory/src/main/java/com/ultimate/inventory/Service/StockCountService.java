package com.ultimate.inventory.Service;


import com.ultimate.inventory.Constants.Capability;import com.ultimate.inventory.Constants.StockCountStatus;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Stocktakes. Starting a count snapshots the system quantity of every product at the location.
 * People enter what they physically count; posting applies counted − snapshot to the current
 * stock, so sales made while counting are not lost. Uncounted lines are left unchanged.
 * Non-managers see a blind count (no system quantities) and, when approvals are required,
 * submit the count for a manager to post.
 */
@Service
public class StockCountService {

    private static final Logger log = LoggerFactory.getLogger(StockCountService.class);
    private static final List<StockCountStatus> ACTIVE = List.of(StockCountStatus.OPEN, StockCountStatus.SUBMITTED);

    /** A line as shown to the current user; systemQuantity/difference are hidden in blind counts. */
    public record LineView(Long id, Long productId, String productName, String sku, String baseUnit,
                           Double systemQuantity, Double countedQuantity, Double difference,
                           Double postedDifference) {}

    public record CountView(StockCount count, boolean blind, boolean canPost, List<LineView> lines,
                            int countedLines, int totalLines) {}

    public record LineEntry(Long lineId, Double countedQuantity) {}

    private final StockCountRepository countRepo;
    private final StockCountLineRepository lineRepo;
    private final InventoryRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final InventoryService inventoryService;
    private final SettingsService settingsService;
    private final BusinessAccessHelper accessHelper;

    public StockCountService(StockCountRepository countRepo, StockCountLineRepository lineRepo,
                             InventoryRepository inventoryRepo, ProductRepository productRepo,
                             InventoryService inventoryService, SettingsService settingsService,
                             BusinessAccessHelper accessHelper) {
        this.countRepo = countRepo;
        this.lineRepo = lineRepo;
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.inventoryService = inventoryService;
        this.settingsService = settingsService;
        this.accessHelper = accessHelper;
    }

    public List<StockCount> list(Long businessId, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Set<Long> allowed = accessHelper.allowedShopIds(member);
        return countRepo.findByBusinessOrderByCreatedAtDesc(accessHelper.requireBusiness(businessId)).stream()
                .filter(c -> allowed == null || allowed.contains(c.getShop().getId())).toList();
    }

    @Transactional
    public CountView start(Long businessId, Long shopId, String notes, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.COUNT);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = accessHelper.requireShopAccess(shopId, businessId, member);
        if (countRepo.existsByShopAndStatusIn(shop, ACTIVE)) {
            throw new AppException(HttpStatus.CONFLICT, "A count is already in progress at " + shop.getShopName());
        }
        StockCount c = new StockCount();
        c.setBusiness(business);
        c.setShop(shop);
        c.setNotes(notes);
        c.setCreatedBy(userEmail);
        c.setCreatedAt(new Date());
        c = countRepo.save(c);
        c.setReference(String.format("CNT-%05d", c.getId()));
        c = countRepo.save(c);

        for (Inventory inv : inventoryRepo.findAllByShop(shop)) {
            if (Boolean.TRUE.equals(inv.getProduct().getDeleted())) continue;
            newLine(c, inv.getProduct(), inv.getQuantity(), userEmail);
        }
        log.info("Stock count {} started at shop {} by {}", c.getReference(), shop.getId(), userEmail);
        return view(c, member);
    }

    public CountView get(Long businessId, Long countId, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        return view(requireCount(businessId, countId, member), member);
    }

    /** Adds a product that is physically present but has no stock line here yet. */
    @Transactional
    public CountView addProduct(Long businessId, Long countId, Long productId, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.COUNT);
        StockCount c = requireStatus(businessId, countId, StockCountStatus.OPEN, member);
        Product product = productRepo.findByIdAndBusinessAndDeletedFalse(productId, c.getBusiness())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Product not found"));
        if (lineRepo.existsByStockCountAndProduct(c, product)) {
            throw new AppException(HttpStatus.CONFLICT, "'" + product.getProductName() + "' is already on this count");
        }
        double system = inventoryRepo.findByProductAndShop(product, c.getShop()).map(Inventory::getQuantity).orElse(0.0);
        newLine(c, product, system, userEmail);
        return view(c, member);
    }

    /** Saves counted quantities; null clears a line back to "not counted". */
    @Transactional
    public CountView saveCounts(Long businessId, Long countId, List<LineEntry> entries, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.COUNT);
        StockCount c = requireStatus(businessId, countId, StockCountStatus.OPEN, member);
        Map<Long, StockCountLine> lines = new HashMap<>();
        lineRepo.findByStockCountOrderByIdAsc(c).forEach(l -> lines.put(l.getId(), l));
        for (LineEntry e : entries) {
            StockCountLine line = lines.get(e.lineId());
            if (line == null) throw new AppException(HttpStatus.BAD_REQUEST, "Line " + e.lineId() + " is not on this count");
            if (e.countedQuantity() != null && e.countedQuantity() < 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Counted quantity cannot be negative");
            }
            line.setCountedQuantity(e.countedQuantity());
            line.setUpdatedBy(userEmail);
            line.setUpdatedAt(new Date());
            lineRepo.save(line);
        }
        return view(c, member);
    }

    /**
     * Finishes the count. Managers (or anyone, when approvals are off) post it straight away;
     * otherwise it is submitted for a manager to post.
     */
    @Transactional
    public CountView complete(Long businessId, Long countId, String userEmail) {
        BusinessUser member = accessHelper.requireCapability(businessId, userEmail, Capability.COUNT);
        StockCount c = requireCount(businessId, countId, member);
        if (c.getStatus() != StockCountStatus.OPEN && c.getStatus() != StockCountStatus.SUBMITTED) {
            throw new AppException(HttpStatus.CONFLICT, "This count is already " + c.getStatus().name().toLowerCase());
        }
        boolean mayPost = mayPost(c.getBusiness(), member);
        if (!mayPost) {
            if (c.getStatus() == StockCountStatus.SUBMITTED) {
                throw new AppException(HttpStatus.FORBIDDEN, "Waiting for a manager to post this count");
            }
            c.setStatus(StockCountStatus.SUBMITTED);
            c.setSubmittedBy(userEmail);
            c.setSubmittedAt(new Date());
            return view(countRepo.save(c), member);
        }

        String countedBy = c.getSubmittedBy() != null ? c.getSubmittedBy() : userEmail;
        String approvedBy = c.getSubmittedBy() != null ? userEmail : null;
        String notes = "Stock count " + c.getReference() + (c.getNotes() != null ? " | " + c.getNotes() : "");
        InventoryService.Ctx ctx = new InventoryService.Ctx(notes, countedBy, approvedBy, c.getReference(),
                InventoryService.Entered.NONE);
        int changed = 0;
        for (StockCountLine line : lineRepo.findByStockCountOrderByIdAsc(c)) {
            if (line.getCountedQuantity() == null) continue;
            double before = inventoryRepo.findByProductAndShop(line.getProduct(), c.getShop())
                    .map(Inventory::getQuantity).orElse(0.0);
            Inventory after = inventoryService.count(line.getProduct(), c.getShop(), null,
                    line.getCountedQuantity(), line.getSystemQuantity(), null, null, ctx);
            line.setPostedDifference(after.getQuantity() - before);
            lineRepo.save(line);
            if (Math.abs(after.getQuantity() - before) > 1e-9) changed++;
        }
        c.setStatus(StockCountStatus.POSTED);
        c.setPostedBy(userEmail);
        c.setPostedAt(new Date());
        log.info("Stock count {} posted by {}: {} line(s) changed", c.getReference(), userEmail, changed);
        return view(countRepo.save(c), member);
    }

    /** Manager sends a submitted count back for recounting. */
    @Transactional
    public CountView reopen(Long businessId, Long countId, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        if (!InventoryService.isManager(member)) throw new AppException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        StockCount c = requireStatus(businessId, countId, StockCountStatus.SUBMITTED, member);
        c.setStatus(StockCountStatus.OPEN);
        return view(countRepo.save(c), member);
    }

    @Transactional
    public CountView cancel(Long businessId, Long countId, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        StockCount c = requireCount(businessId, countId, member);
        if (!ACTIVE.contains(c.getStatus())) {
            throw new AppException(HttpStatus.CONFLICT, "This count is already " + c.getStatus().name().toLowerCase());
        }
        if (!InventoryService.isManager(member) && !userEmail.equalsIgnoreCase(c.getCreatedBy())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only a manager or the person who started it can cancel this count");
        }
        c.setStatus(StockCountStatus.CANCELLED);
        c.setUpdatedBy(userEmail);
        c.setUpdatedAt(new Date());
        return view(countRepo.save(c), member);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean mayPost(Business business, BusinessUser member) {
        return InventoryService.isManager(member)
                || !Boolean.TRUE.equals(settingsService.forBusiness(business).getRequireAdjustmentApproval());
    }

    private void newLine(StockCount c, Product product, double system, String by) {
        StockCountLine l = new StockCountLine();
        l.setStockCount(c);
        l.setProduct(product);
        l.setSystemQuantity(system);
        l.setCreatedBy(by);
        l.setCreatedAt(new Date());
        lineRepo.save(l);
    }

    private CountView view(StockCount c, BusinessUser member) {
        boolean manager = InventoryService.isManager(member);
        boolean blind = !manager && c.getStatus() == StockCountStatus.OPEN;
        List<LineView> lines = new ArrayList<>();
        int counted = 0;
        for (StockCountLine l : lineRepo.findByStockCountOrderByIdAsc(c)) {
            if (l.getCountedQuantity() != null) counted++;
            Double diff = l.getCountedQuantity() != null ? l.getCountedQuantity() - l.getSystemQuantity() : null;
            lines.add(new LineView(l.getId(), l.getProductId(), l.getProductName(), l.getSku(), l.getBaseUnit(),
                    blind ? null : l.getSystemQuantity(), l.getCountedQuantity(), blind ? null : diff,
                    l.getPostedDifference()));
        }
        lines.sort(Comparator.comparing(LineView::productName, String.CASE_INSENSITIVE_ORDER));
        boolean canPost = ACTIVE.contains(c.getStatus()) && mayPost(c.getBusiness(), member);
        return new CountView(c, blind, canPost, lines, counted, lines.size());
    }

    /** The count, if it belongs to the business and is at a location this member works at. */
    private StockCount requireCount(Long businessId, Long countId, BusinessUser member) {
        StockCount c = countRepo.findByIdAndBusiness(countId, accessHelper.requireBusiness(businessId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Stock count not found"));
        if (!accessHelper.canUseShop(member, c.getShop())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Stock count not found");
        }
        return c;
    }

    private StockCount requireStatus(Long businessId, Long countId, StockCountStatus status,
                                     BusinessUser member) {
        StockCount c = requireCount(businessId, countId, member);
        if (c.getStatus() != status) {
            throw new AppException(HttpStatus.CONFLICT, "This count is " + c.getStatus().name().toLowerCase());
        }
        return c;
    }
}
