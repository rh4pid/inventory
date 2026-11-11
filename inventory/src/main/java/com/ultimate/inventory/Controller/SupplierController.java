package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.SupplierDTO;
import com.ultimate.inventory.Model.Supplier;
import com.ultimate.inventory.Service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/businesses/{businessId}/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid SupplierDTO dto,
                                    @AuthenticationPrincipal String email) {
        Supplier supplier = supplierService.createSupplier(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(supplier);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @PageableDefault(size = 20) Pageable pageable,
                                  @AuthenticationPrincipal String email) {
        Page<Supplier> page = supplierService.listSuppliers(businessId, email, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{supplierId}")
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @PathVariable Long supplierId,
                                 @AuthenticationPrincipal String email) {
        Supplier supplier = supplierService.getSupplier(businessId, supplierId, email);
        return ResponseEntity.ok(supplier);
    }

    @PutMapping("/{supplierId}")
    public ResponseEntity<?> update(@PathVariable Long businessId,
                                    @PathVariable Long supplierId,
                                    @RequestBody @Valid SupplierDTO dto,
                                    @AuthenticationPrincipal String email) {
        Supplier supplier = supplierService.updateSupplier(businessId, supplierId, dto, email);
        return ResponseEntity.ok(supplier);
    }

    @DeleteMapping("/{supplierId}")
    public ResponseEntity<?> delete(@PathVariable Long businessId,
                                    @PathVariable Long supplierId,
                                    @AuthenticationPrincipal String email) {
        supplierService.deleteSupplier(businessId, supplierId, email);
        return ResponseEntity.ok(Map.of("success", true, "message", "Supplier deleted"));
    }
}
