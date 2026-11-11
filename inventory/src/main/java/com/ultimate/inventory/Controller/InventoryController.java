package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.*;
import com.ultimate.inventory.Model.Inventory;
import com.ultimate.inventory.Service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
        Inventory inventory = inventoryService.stockOut(businessId, dto, email);
        return ResponseEntity.ok(inventory);
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
        Inventory inventory = inventoryService.adjust(businessId, dto, email);
        return ResponseEntity.ok(inventory);
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
}
