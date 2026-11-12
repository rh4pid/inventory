package com.ultimate.inventory.Service;

import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.StockBatchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Low-level batch bookkeeping. Every method here must be called while the caller holds the
 * row lock on the matching {@link Inventory} line (see InventoryService), which serialises all
 * batch changes for one product at one location.
 */
@Service
public class BatchService {

    /** Details for a batch being received. Any field may be null. */
    public record BatchInfo(String batchNumber, LocalDate expiryDate, Double unitCost,
                            Date receivedAt, Long supplierId, String source) {
        public static BatchInfo of(String batchNumber, LocalDate expiryDate, Double unitCost,
                                   Long supplierId, String source) {
            return new BatchInfo(batchNumber, expiryDate, unitCost, null, supplierId, source);
        }
        /** Same lot arriving at another location (keeps number, expiry, cost and original receive date). */
        public static BatchInfo copyOf(StockBatch b, String source) {
            return new BatchInfo(b.getBatchNumber(), b.getExpiryDate(), b.getUnitCost(),
                    b.getReceivedAt(), b.getSupplierId(), source);
        }
    }

    /** A quantity taken from one batch. {@code batch} is null for stock taken beyond what batches hold. */
    public record Slice(StockBatch batch, double quantity) {}

    /** Read model returned to clients. */
    public record BatchView(Long id, Long productId, String productName, String sku, String baseUnit,
                            Long shopId, String shopName, String batchNumber, LocalDate expiryDate,
                            Long daysToExpiry, String status, Date receivedAt, Double unitCost,
                            Double quantityReceived, Double quantityRemaining, String source) {}

    private final StockBatchRepository batchRepo;

    public BatchService(StockBatchRepository batchRepo) {
        this.batchRepo = batchRepo;
    }

    /** Creates a new batch holding {@code quantity} base units. */
    public StockBatch receive(Product product, Shop shop, double quantity, BatchInfo info, String by) {
        BatchInfo i = info != null ? info : BatchInfo.of(null, null, null, null, null);
        StockBatch b = new StockBatch();
        b.setProduct(product);
        b.setShop(shop);
        b.setBatchNumber(i.batchNumber() != null && !i.batchNumber().isBlank()
                ? i.batchNumber().trim() : "PENDING");
        b.setExpiryDate(i.expiryDate());
        b.setUnitCost(i.unitCost() != null ? i.unitCost() : product.getCostPrice());
        b.setReceivedAt(i.receivedAt() != null ? i.receivedAt() : new Date());
        b.setSupplierId(i.supplierId());
        b.setSource(i.source());
        b.setQuantityReceived(quantity);
        b.setQuantityRemaining(quantity);
        b.setCreatedBy(by);
        b.setCreatedAt(new Date());
        b = batchRepo.save(b);
        if ("PENDING".equals(b.getBatchNumber())) {
            b.setBatchNumber("LOT-" + new SimpleDateFormat("yyMMdd").format(b.getReceivedAt()) + "-" + b.getId());
            b = batchRepo.save(b);
        }
        return b;
    }

    /**
     * Makes the batches at a location add up to the stock line: sum(remaining) == max(quantity, 0).
     * Missing stock becomes a new batch labelled {@code label}; excess is trimmed oldest-first.
     * This absorbs stock that existed before batch tracking was switched on, and stock sold
     * while it was off or while the location was negative.
     */
    public void reconcile(Inventory inv, String label, String by) {
        double target = Math.max(inv.getQuantity(), 0);
        double sum = batchRepo.sumRemaining(inv.getProduct(), inv.getShop());
        double diff = target - sum;
        if (diff > 1e-9) {
            receive(inv.getProduct(), inv.getShop(), diff, BatchInfo.of(null, null, null, null, label), by);
        } else if (diff < -1e-9) {
            double excess = -diff;
            List<StockBatch> list = batchRepo.findAvailable(inv.getProduct(), inv.getShop());
            list.sort(Comparator.comparing(StockBatch::getReceivedAt).thenComparing(StockBatch::getId));
            for (StockBatch b : list) {
                if (excess <= 1e-9) break;
                double take = Math.min(excess, b.getQuantityRemaining());
                b.setQuantityRemaining(b.getQuantityRemaining() - take);
                batchRepo.save(b);
                excess -= take;
            }
        }
    }

    /** Quantity that automatic picking may use (excludes expired batches when {@code skipExpired}). */
    public double available(Product product, Shop shop, boolean skipExpired) {
        LocalDate today = LocalDate.now();
        return batchRepo.findAvailable(product, shop).stream()
                .filter(b -> !skipExpired || !b.isExpired(today))
                .mapToDouble(StockBatch::getQuantityRemaining).sum();
    }

    /**
     * Takes up to {@code quantity} from batches and returns what was taken.
     * With {@code batch} given, only that batch is used. Otherwise batches are used
     * earliest-expiry-first ({@code fefo}) or oldest-first, skipping expired ones when asked.
     */
    public List<Slice> pick(Product product, Shop shop, double quantity, StockBatch batch,
                            boolean fefo, boolean skipExpired) {
        LocalDate today = LocalDate.now();
        List<StockBatch> candidates;
        if (batch != null) {
            candidates = List.of(batch);
        } else {
            candidates = new ArrayList<>(batchRepo.findAvailable(product, shop));
            candidates.removeIf(b -> skipExpired && b.isExpired(today));
            Comparator<StockBatch> fifo = Comparator.comparing(StockBatch::getReceivedAt)
                    .thenComparing(StockBatch::getId);
            Comparator<StockBatch> order = fefo
                    ? Comparator.comparing(StockBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(fifo)
                    : fifo;
            candidates.sort(order);
        }
        List<Slice> slices = new ArrayList<>();
        double left = quantity;
        for (StockBatch b : candidates) {
            if (left <= 1e-9) break;
            double take = Math.min(left, b.getQuantityRemaining());
            if (take <= 0) continue;
            b.setQuantityRemaining(b.getQuantityRemaining() - take);
            batchRepo.save(b);
            slices.add(new Slice(b, take));
            left -= take;
        }
        return slices;
    }

    public StockBatch requireBatch(Long batchId, Product product, Shop shop) {
        StockBatch b = batchRepo.findById(batchId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Batch not found"));
        if (!b.getProduct().getId().equals(product.getId()) || !b.getShop().getId().equals(shop.getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Batch " + b.getBatchNumber() + " is not stock of this product at " + shop.getShopName());
        }
        return b;
    }

    public void save(StockBatch b) {
        batchRepo.save(b);
    }

    public BatchView view(StockBatch b, int alertDays) {
        LocalDate today = LocalDate.now();
        Long days = b.getExpiryDate() != null ? ChronoUnit.DAYS.between(today, b.getExpiryDate()) : null;
        String status = days == null ? "NO_EXPIRY" : days < 0 ? "EXPIRED" : days <= alertDays ? "EXPIRING" : "OK";
        Product p = b.getProduct();
        return new BatchView(b.getId(), p.getId(), p.getProductName(), p.getSku(),
                p.getUnit() != null ? p.getUnit().getName() : null,
                b.getShop().getId(), b.getShop().getShopName(), b.getBatchNumber(), b.getExpiryDate(),
                days, status, b.getReceivedAt(), b.getUnitCost(),
                b.getQuantityReceived(), b.getQuantityRemaining(), b.getSource());
    }
}
