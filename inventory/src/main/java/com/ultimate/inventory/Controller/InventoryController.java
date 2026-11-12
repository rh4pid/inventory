package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.*;
import com.ultimate.inventory.Model.Inventory;
import com.ultimate.inventory.Service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/businesses/{businessId}")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Batches holding stock. Filter by ?shopId= and/or ?productId=; ?includeEmpty=true for used-up lots. */
    @GetMapping("/inventory/batches")
    public ResponseEntity<?> batches(@PathVariable Long businessId,
                                     @RequestParam(required = false) Long shopId,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(defaultValue = "false") boolean includeEmpty,
                                     @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(inventoryService.listBatches(businessId, shopId, productId, includeEmpty, email));
    }

    /** Expired batches and those expiring within ?days= (default: the business's alert window). */
    @GetMapping("/inventory/expiring")
    public ResponseEntity<?> expiring(@PathVariable Long businessId,
                                      @RequestParam(required = false) Long shopId,
                                      @RequestParam(required = false) Integer days,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(inventoryService.listExpiring(businessId, shopId, days, email));
    }

    /** Stock movement history (newest first). Filter by ?shopId= and/or ?productId=. */
    @GetMapping("/inventory/movements")
    public ResponseEntity<?> movements(@PathVariable Long businessId,
                                       @RequestParam(required = false) Long shopId,
                                       @RequestParam(required = false) Long productId,
                                       @PageableDefault(size = 50) Pageable pageable,
                                       @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(inventoryService.listMovements(businessId, shopId, productId, email, pageable));
    }

    @PostMapping("/shops/{shopId}/inventory/stock-in")
    public ResponseEntity<?> stockIn(@PathVariable Long businessId,
                                     @PathVariable Long shopId,
                                     @RequestBody @Valid StockInDTO dto,
                                     @AuthenticationPrincipal String email) {
        dto.setShopId(shopId);
        Inventory inventory = inventoryService.stockIn(businessId, dto, email);
        return ResponseEntity.ok(inventory);
    }

    @PostMapping("/shops/{shopId}/inventory/stock-out")
    public ResponseEntity<?> stockOut(@PathVariable Long businessId,
                                      @PathVariable Long shopId,
                                      @RequestBody @Valid StockOutDTO dto,
                                      @AuthenticationPrincipal String email) {
        dto.setShopId(shopId);
        return toResponse(inventoryService.stockOut(businessId, dto, email));
    }

    @PostMapping("/inventory/transfer")
    public ResponseEntity<?> transfer(@PathVariable Long businessId,
                                      @RequestBody @Valid StockTransferDTO dto,
                                      @AuthenticationPrincipal String email) {
        Map<String, Inventory> result = inventoryService.transfer(businessId, dto, email);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/shops/{shopId}/inventory/adjust")
    public ResponseEntity<?> adjust(@PathVariable Long businessId,
                                    @PathVariable Long shopId,
                                    @RequestBody @Valid StockAdjustmentDTO dto,
                                    @AuthenticationPrincipal String email) {
        dto.setShopId(shopId);
        return toResponse(inventoryService.adjust(businessId, dto, email));
    }

    @GetMapping("/shops/{shopId}/inventory")
    public ResponseEntity<?> listShopInventory(@PathVariable Long businessId,
                                               @PathVariable Long shopId,
                                               @PageableDefault(size = 20) Pageable pageable,
                                               @AuthenticationPrincipal String email) {
        Page<Inventory> page = inventoryService.listShopInventory(businessId, shopId, email, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/inventory/low-stock")
    public ResponseEntity<?> lowStock(@PathVariable Long businessId,
                                      @AuthenticationPrincipal String email) {
        List<Inventory> items = inventoryService.getLowStock(businessId, email);
        return ResponseEntity.ok(items);
    }

    /** 200 with the stock line when applied; 202 {pending: true, request} when it awaits approval. */
    private static ResponseEntity<?> toResponse(InventoryService.ActionResult result) {
        if (result.pending() != null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("pending", true, "request", result.pending()));
        }
        return ResponseEntity.ok(result.inventory());
    }
}
