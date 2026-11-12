package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Constants.StockRequestStatus;
import com.ultimate.inventory.Service.StockCountService;
import com.ultimate.inventory.Service.StockRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Approvals for stock changes, and stock counts (stocktakes). */
@RestController
@RequestMapping("/businesses/{businessId}")
public class StockControlController {

    private final StockRequestService requestService;
    private final StockCountService countService;

    public StockControlController(StockRequestService requestService, StockCountService countService) {
        this.requestService = requestService;
        this.countService = countService;
    }

    public record DecisionBody(String note) {}
    public record StartCountBody(Long shopId, String notes) {}
    public record AddProductBody(Long productId) {}

    // ── Approvals ────────────────────────────────────────────────────────────

    /** ?status=PENDING|APPROVED|REJECTED|CANCELLED; omit for all. */
    @GetMapping("/stock-requests")
    public ResponseEntity<?> listRequests(@PathVariable Long businessId,
                                          @RequestParam(required = false) StockRequestStatus status,
                                          @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(requestService.list(businessId, status, email));
    }

    @GetMapping("/stock-requests/pending-count")
    public ResponseEntity<?> pendingCount(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(Map.of("count", requestService.pendingCount(businessId, email)));
    }

    @PostMapping("/stock-requests/{requestId}/approve")
    public ResponseEntity<?> approve(@PathVariable Long businessId, @PathVariable Long requestId,
                                     @RequestBody(required = false) DecisionBody body,
                                     @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(requestService.approve(businessId, requestId, body != null ? body.note() : null, email));
    }

    @PostMapping("/stock-requests/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable Long businessId, @PathVariable Long requestId,
                                    @RequestBody(required = false) DecisionBody body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(requestService.reject(businessId, requestId, body != null ? body.note() : null, email));
    }

    @PostMapping("/stock-requests/{requestId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long businessId, @PathVariable Long requestId,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(requestService.cancel(businessId, requestId, email));
    }

    // ── Stock counts ─────────────────────────────────────────────────────────

    @GetMapping("/stock-counts")
    public ResponseEntity<?> listCounts(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.list(businessId, email));
    }

    @PostMapping("/stock-counts")
    public ResponseEntity<?> startCount(@PathVariable Long businessId, @RequestBody StartCountBody body,
                                        @AuthenticationPrincipal String email) {
        if (body == null || body.shopId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Location is required"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(countService.start(businessId, body.shopId(), body.notes(), email));
    }

    @GetMapping("/stock-counts/{countId}")
    public ResponseEntity<?> getCount(@PathVariable Long businessId, @PathVariable Long countId,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.get(businessId, countId, email));
    }

    @PostMapping("/stock-counts/{countId}/lines")
    public ResponseEntity<?> addProduct(@PathVariable Long businessId, @PathVariable Long countId,
                                        @RequestBody AddProductBody body, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.addProduct(businessId, countId, body.productId(), email));
    }

    /** Body: [{lineId, countedQuantity}] — countedQuantity null clears the line. */
    @PutMapping("/stock-counts/{countId}/lines")
    public ResponseEntity<?> saveCounts(@PathVariable Long businessId, @PathVariable Long countId,
                                        @RequestBody List<StockCountService.LineEntry> entries,
                                        @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.saveCounts(businessId, countId, entries, email));
    }

    /** Posts the count, or submits it for a manager when approval is required. */
    @PostMapping("/stock-counts/{countId}/complete")
    public ResponseEntity<?> complete(@PathVariable Long businessId, @PathVariable Long countId,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.complete(businessId, countId, email));
    }

    @PostMapping("/stock-counts/{countId}/reopen")
    public ResponseEntity<?> reopen(@PathVariable Long businessId, @PathVariable Long countId,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.reopen(businessId, countId, email));
    }

    @PostMapping("/stock-counts/{countId}/cancel")
    public ResponseEntity<?> cancelCount(@PathVariable Long businessId, @PathVariable Long countId,
                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(countService.cancel(businessId, countId, email));
    }
}
