package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.*;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Everything the overview screen shows, computed in one pass for one location (or every
 * location the caller works at) over a period: order pipeline, stock health, stock flow,
 * what needs attention, the latest orders and the product list with demand.
 */
@Service
public class DashboardService {

    /** Stock with no sale and no new stock for this long counts as dead stock, whatever period is shown. */
    static final int DEAD_STOCK_DAYS = 90;
    /** Credit sales without a due date are overdue this many days after the sale. */
    static final int DEFAULT_CREDIT_DAYS = 30;
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final BusinessAccessHelper accessHelper;
    private final InventoryRepository inventoryRepo;
    private final StockMovementRepository movementRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceDetailRepository detailRepo;
    private final SupplierRepository supplierRepo;
    private final StockBatchRepository batchRepo;
    private final SettingsService settingsService;

    public DashboardService(BusinessAccessHelper accessHelper, InventoryRepository inventoryRepo,
                            StockMovementRepository movementRepo, InvoiceRepository invoiceRepo,
                            InvoiceDetailRepository detailRepo, SupplierRepository supplierRepo,
                            StockBatchRepository batchRepo, SettingsService settingsService) {
        this.accessHelper = accessHelper;
        this.inventoryRepo = inventoryRepo;
        this.movementRepo = movementRepo;
        this.invoiceRepo = invoiceRepo;
        this.detailRepo = detailRepo;
        this.supplierRepo = supplierRepo;
        this.batchRepo = batchRepo;
        this.settingsService = settingsService;
    }

    // ── Response shape ───────────────────────────────────────────────────────

    public record Orders(long completed, long inProgress, long returns, long overdue, long total) {}

    public record Stock(long inStock, long lowStock, long outOfStock, long deadStock, long products) {}

    public record FlowDay(LocalDate date, double in, double out) {}

    public record Attention(Long productId, String productName, String sku, String reason, String detail) {}

    public record RecentOrder(Long id, String invoiceNumber, String type, String status, String paymentStatus,
                              String headline, int itemCount, String party, String orderedBy,
                              BigDecimal total, String paymentMethod, Date createdAt, String shopName) {}

    public record ProductRow(Long productId, String productName, String sku, String category, String supplier,
                             String unit,
                             @tools.jackson.databind.annotation.JsonSerialize(using = com.ultimate.inventory.utils.CostVisibility.Serializer.class) Double cost,
                             double onHand,
                             @tools.jackson.databind.annotation.JsonSerialize(using = com.ultimate.inventory.utils.CostVisibility.Serializer.class) Double onHandValue,
                             double soldInPeriod,
                             String stockStatus, String demand) {}

    public record Dashboard(String period, int days, Long shopId, Orders orders, Stock stock,
                            List<FlowDay> flow, List<Attention> attention, int attentionTotal,
                            List<RecentOrder> recentOrders, List<ProductRow> products) {}

    // ── Build ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Dashboard build(Long businessId, Long shopId, String period, String userEmail) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Set<Long> allowed = accessHelper.allowedShopIds(member); // null = every location
        if (shopId != null) accessHelper.requireShopAccess(shopId, businessId, member);
        Predicate<Shop> inScope = s -> s != null
                && (shopId != null ? s.getId().equals(shopId) : allowed == null || allowed.contains(s.getId()));

        int days = switch (period == null ? "month" : period.toLowerCase()) {
            case "week" -> 7;
            case "quarter" -> 90;
            case "year" -> 365;
            default -> 30;
        };
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(days - 1L);
        Date since = toDate(from);
        Date deadSince = toDate(today.minusDays(DEAD_STOCK_DAYS - 1L));

        // Movements cover the longer of the period and the dead-stock window.
        List<StockMovement> moves = movementRepo.findSince(business, since.before(deadSince) ? since : deadSince)
                .stream().filter(m -> inScope.test(m.getShop())).toList();

        // ── Stock per product (summed over the locations in scope) ──
        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Double> onHand = new HashMap<>();
        Set<Long> newLines = new HashSet<>();
        for (Inventory i : inventoryRepo.findActiveByBusiness(business)) {
            if (!inScope.test(i.getShop())) continue;
            // A stock line opened inside the window is new stock too (covers stock loaded without movements).
            if (i.getCreatedAt() != null && !i.getCreatedAt().before(deadSince)) newLines.add(i.getProduct().getId());
            products.putIfAbsent(i.getProduct().getId(), i.getProduct());
            onHand.merge(i.getProduct().getId(), nz(i.getQuantity()), Double::sum);
        }
        Map<Long, Double> soldInPeriod = new HashMap<>();
        Set<Long> soldRecently = new HashSet<>();
        Set<Long> receivedRecently = new HashSet<>(); // new stock is not dead stock, even if unsold
        receivedRecently.addAll(newLines);
        Map<LocalDate, double[]> flow = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) flow.put(d, new double[2]);
        for (StockMovement m : moves) {
            Long pid = m.getProduct().getId();
            boolean inPeriod = !m.getCreatedAt().before(since);
            if (nz(m.getQuantityChange()) > 0 && m.getMovementType() != StockMovementType.SALE_RETURN) receivedRecently.add(pid);
            if (m.getMovementType() == StockMovementType.SALE) {
                soldRecently.add(pid);
                if (inPeriod) soldInPeriod.merge(pid, -m.getQuantityChange(), Double::sum);
            }
            if (inPeriod) {
                double[] day = flow.get(toLocalDate(m.getCreatedAt()));
                if (day != null) {
                    double q = nz(m.getQuantityChange());
                    if (q > 0) day[0] += q; else day[1] += -q;
                }
            }
        }

        // ── Stock health: each product in exactly one bucket (out > low > dead > in) ──
        Map<Long, String> status = new HashMap<>();
        long in = 0, low = 0, out = 0, dead = 0;
        for (Product p : products.values()) {
            double q = onHand.getOrDefault(p.getId(), 0.0);
            String s;
            if (q <= 1e-9) { s = "OUT"; out++; }
            else if (p.getReorderLevel() != null && q <= p.getReorderLevel()) { s = "LOW"; low++; }
            else if (!soldRecently.contains(p.getId()) && !receivedRecently.contains(p.getId())) { s = "DEAD"; dead++; }
            else { s = "IN"; in++; }
            status.put(p.getId(), s);
        }

        // ── Orders (sales): made in the period, plus anything still unpaid and overdue ──
        long completed = 0, inProgress = 0, returns = 0, overdue = 0, total = 0;
        Date now = new Date();
        for (Invoice inv : invoiceRepo.findForDashboard(business, InvoiceType.SALES, since)) {
            if (!inScope.test(inv.getShop()) || inv.getInvoiceStatus() == InvoiceStatus.VOID) continue;
            boolean inPeriod = inv.getCreatedAt() != null && !inv.getCreatedAt().before(since);
            boolean owing = InvoiceAdjustmentService.balanceDue(inv).signum() > 0;
            Date due = inv.getDueDate() != null ? inv.getDueDate()
                    : inv.getCreatedAt() != null ? toDate(toLocalDate(inv.getCreatedAt()).plusDays(DEFAULT_CREDIT_DAYS)) : null;
            boolean isOverdue = owing && due != null && due.before(now);
            if (isOverdue) overdue++;
            if (!inPeriod) continue;
            total++;
            if (nz(inv.getReturnedAmount()).signum() > 0) returns++;
            if (!owing) completed++;
            else if (!isOverdue) inProgress++;
        }

        // ── Needs attention: out of stock, low stock, expired / expiring batches ──
        List<Attention> attention = new ArrayList<>();
        for (Product p : products.values()) {
            String s = status.get(p.getId());
            if ("OUT".equals(s)) attention.add(new Attention(p.getId(), p.getProductName(), p.getSku(), "OUT", "Out of stock"));
            else if ("LOW".equals(s)) attention.add(new Attention(p.getId(), p.getProductName(), p.getSku(), "LOW",
                    InventoryService.fmt(onHand.get(p.getId())) + " left (reorder at " + InventoryService.fmt(p.getReorderLevel()) + ")"));
        }
        BusinessSettings settings = settingsService.forBusiness(business);
        if (Boolean.TRUE.equals(settings.getTrackExpiry())) {
            int alert = settings.getExpiryAlertDays() != null ? settings.getExpiryAlertDays() : 30;
            for (StockBatch b : batchRepo.findExpiring(business, null, today.plusDays(alert))) {
                if (!inScope.test(b.getShop()) || nz(b.getQuantityRemaining()) <= 1e-9) continue;
                boolean expired = b.getExpiryDate() != null && b.getExpiryDate().isBefore(today);
                attention.add(new Attention(b.getProduct().getId(), b.getProduct().getProductName(), b.getProduct().getSku(),
                        expired ? "EXPIRED" : "EXPIRING",
                        (expired ? "Expired " : "Expires ") + b.getExpiryDate() + " · batch " + b.getBatchNumber()));
            }
        }
        attention.sort(Comparator.comparingInt(a -> List.of("EXPIRED", "OUT", "EXPIRING", "LOW").indexOf(a.reason())));

        // ── Latest orders (sales and purchases) ──
        List<RecentOrder> recent = new ArrayList<>();
        for (InvoiceType type : InvoiceType.values()) {
            // Only the orders this person can open: sales for sellers, purchases for receivers.
            if (!BusinessAccessHelper.can(member, type == InvoiceType.SALES ? com.ultimate.inventory.Constants.Capability.SELL
                    : com.ultimate.inventory.Constants.Capability.RECEIVE)) continue;
            invoiceRepo.findByBusinessAndType(business, type,
                            PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "id")))
                    .forEach(inv -> { if (inScope.test(inv.getShop())) recent.add(recentView(inv)); });
        }
        recent.sort(Comparator.comparing(RecentOrder::id).reversed());

        // ── Product list with supplier and demand ──
        Map<Long, String> supplierOf = latestSuppliers(business, inScope);
        List<Long> sellers = soldInPeriod.entrySet().stream().filter(e -> e.getValue() > 1e-9)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()).map(Map.Entry::getKey).toList();
        int highCut = (int) Math.ceil(sellers.size() * 0.3);
        Set<Long> high = new HashSet<>(sellers.subList(0, Math.min(highCut, sellers.size())));
        List<ProductRow> rows = products.values().stream().map(p -> {
            double q = onHand.getOrDefault(p.getId(), 0.0);
            double cost = p.getCostPrice() != null ? p.getCostPrice() : 0.0;
            double sold = soldInPeriod.getOrDefault(p.getId(), 0.0);
            String demand = high.contains(p.getId()) ? "HIGH" : sold > 1e-9 ? "NORMAL" : "LOW";
            return new ProductRow(p.getId(), p.getProductName(), p.getSku(),
                    p.getCategory() != null ? p.getCategory().getCategoryName() : null,
                    supplierOf.get(p.getId()), p.getUnit() != null ? p.getUnit().getName() : null,
                    cost, q, Math.max(q, 0) * cost, sold, status.get(p.getId()), demand);
        }).sorted(Comparator.comparingDouble((ProductRow r) -> r.onHandValue()).reversed()).toList();

        int attentionTotal = attention.size();
        return new Dashboard(days == 7 ? "week" : days == 90 ? "quarter" : days == 365 ? "year" : "month",
                days, shopId,
                new Orders(completed, inProgress, returns, overdue, total),
                new Stock(in, low, out, dead, products.size()),
                flow.entrySet().stream().map(e -> new FlowDay(e.getKey(), e.getValue()[0], e.getValue()[1])).toList(),
                attention.subList(0, Math.min(12, attentionTotal)), attentionTotal,
                recent.subList(0, Math.min(6, recent.size())), rows);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RecentOrder recentView(Invoice inv) {
        List<InvoiceDetail> lines = detailRepo.findByInvoice(inv);
        String headline = lines.isEmpty() ? "No items" : lines.get(0).getProduct().getProductName();
        String party = inv.getType() == InvoiceType.SALES
                ? (inv.getCustomer() != null ? (inv.getCustomer().getFirstName() + " " +
                    Objects.toString(inv.getCustomer().getLastName(), "")).trim() : "Walk-in customer")
                : (inv.getSupplier() != null ? inv.getSupplier().getCompanyName() : "No supplier");
        String status = inv.getInvoiceStatus() == InvoiceStatus.VOID ? "VOID"
                : nz(inv.getReturnedAmount()).compareTo(nz(inv.getTotalAmount())) >= 0 && nz(inv.getTotalAmount()).signum() > 0 ? "RETURNED"
                : nz(inv.getReturnedAmount()).signum() > 0 ? "PART_RETURNED"
                : inv.getType() == InvoiceType.SALES ? "SOLD" : "RECEIVED";
        return new RecentOrder(inv.getId(), inv.getInvoiceNumber(), inv.getType().name(), status,
                inv.getPaymentStatus() != null ? inv.getPaymentStatus().name() : null,
                headline, lines.size(), party, inv.getCreatedBy(), inv.getTotalAmount(),
                inv.getPaymentMethod() != null ? inv.getPaymentMethod().name() : null,
                inv.getCreatedAt(), inv.getShop() != null ? inv.getShop().getShopName() : null);
    }

    /** Each product's most recent supplier, from deliveries at the locations in scope. */
    private Map<Long, String> latestSuppliers(Business business, Predicate<Shop> inScope) {
        Map<Long, Long> supplierIdOf = new HashMap<>();
        for (StockMovement m : movementRepo.findWithSupplier(business, PageRequest.of(0, 5000))) {
            if (inScope.test(m.getShop())) supplierIdOf.putIfAbsent(m.getProduct().getId(), m.getSupplierId());
        }
        Map<Long, String> names = supplierRepo.findAllById(new HashSet<>(supplierIdOf.values())).stream()
                .filter(s -> s.getBusiness() == null || s.getBusiness().getId().equals(business.getId()))
                .collect(Collectors.toMap(Supplier::getId, Supplier::getCompanyName, (a, b) -> a));
        Map<Long, String> out = new HashMap<>();
        supplierIdOf.forEach((pid, sid) -> { if (names.containsKey(sid)) out.put(pid, names.get(sid)); });
        return out;
    }

    private static Date toDate(LocalDate d) { return Date.from(d.atStartOfDay(ZONE).toInstant()); }

    private static LocalDate toLocalDate(Date d) {
        return d instanceof java.sql.Date sd ? sd.toLocalDate() : d.toInstant().atZone(ZONE).toLocalDate();
    }

    private static double nz(Double v) { return v != null ? v : 0.0; }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
