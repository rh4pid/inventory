package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Constants.BusinessType;
import com.ultimate.inventory.Constants.LocationType;
import com.ultimate.inventory.DTO.BusinessSettingsDTO;
import com.ultimate.inventory.DTO.ProductUnitDTO;
import com.ultimate.inventory.DTO.UnitDTO;
import com.ultimate.inventory.Service.SettingsService;
import com.ultimate.inventory.Service.UnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SettingsController {

    private final SettingsService settingsService;
    private final UnitService unitService;

    public SettingsController(SettingsService settingsService, UnitService unitService) {
        this.settingsService = settingsService;
        this.unitService = unitService;
    }

    // ── Reference data ───────────────────────────────────────────────────────

    /** Business types with the feature switches each one turns on by default. */
    @GetMapping("/reference/business-types")
    public ResponseEntity<?> businessTypes() {
        List<Map<String, Object>> list = Arrays.stream(BusinessType.values()).map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", t.name());
            m.put("label", t.getLabel());
            m.put("trackBatches", t.isTrackBatches());
            m.put("trackExpiry", t.isTrackExpiry());
            m.put("multipleUnits", t.isMultipleUnits());
            m.put("requireAdjustmentApproval", t.isRequireAdjustmentApproval());
            return m;
        }).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/reference/location-types")
    public ResponseEntity<?> locationTypes() {
        return ResponseEntity.ok(Arrays.stream(LocationType.values()).map(Enum::name).toList());
    }

    // ── Business settings ────────────────────────────────────────────────────

    @GetMapping("/businesses/{businessId}/settings")
    public ResponseEntity<?> getSettings(@PathVariable Long businessId,
                                         @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(settingsService.getSettings(businessId, email));
    }

    @PutMapping("/businesses/{businessId}/settings")
    public ResponseEntity<?> updateSettings(@PathVariable Long businessId,
                                            @RequestBody @Valid BusinessSettingsDTO dto,
                                            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(settingsService.updateSettings(businessId, dto, email));
    }

    // ── Business profile (name and contact details) ─────────────────────────

    public record BusinessProfile(Long id, String name, String code, String email, String contact,
                                  String secondaryContact, String logo) {}

    @GetMapping("/businesses/{businessId}/profile")
    public ResponseEntity<?> getProfile(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(settingsService.getProfile(businessId, email));
    }

    /** Owners and admins update the company's name and contact details. */
    @PutMapping("/businesses/{businessId}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable Long businessId, @RequestBody BusinessProfile body,
                                           @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(settingsService.updateProfile(businessId, body, email));
    }

    // ── Units of measure ─────────────────────────────────────────────────────

    @GetMapping("/businesses/{businessId}/units")
    public ResponseEntity<?> listUnits(@PathVariable Long businessId,
                                       @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(unitService.listUnits(businessId, email));
    }

    @PostMapping("/businesses/{businessId}/units")
    public ResponseEntity<?> createUnit(@PathVariable Long businessId,
                                        @RequestBody @Valid UnitDTO dto,
                                        @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unitService.createUnit(businessId, dto, email));
    }

    @PutMapping("/businesses/{businessId}/units/{unitId}")
    public ResponseEntity<?> updateUnit(@PathVariable Long businessId, @PathVariable Long unitId,
                                        @RequestBody @Valid UnitDTO dto,
                                        @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(unitService.updateUnit(businessId, unitId, dto, email));
    }

    @DeleteMapping("/businesses/{businessId}/units/{unitId}")
    public ResponseEntity<?> deleteUnit(@PathVariable Long businessId, @PathVariable Long unitId,
                                        @AuthenticationPrincipal String email) {
        unitService.deleteUnit(businessId, unitId, email);
        return ResponseEntity.ok(Map.of("success", true, "message", "Unit deleted"));
    }

    // ── Product pack sizes ───────────────────────────────────────────────────

    @GetMapping("/businesses/{businessId}/products/{productId}/units")
    public ResponseEntity<?> listProductUnits(@PathVariable Long businessId, @PathVariable Long productId,
                                              @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(unitService.listProductUnits(businessId, productId, email));
    }

    @PutMapping("/businesses/{businessId}/products/{productId}/units")
    public ResponseEntity<?> setProductUnits(@PathVariable Long businessId, @PathVariable Long productId,
                                             @RequestBody @Valid List<ProductUnitDTO> dtos,
                                             @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(unitService.setProductUnits(businessId, productId, dtos, email));
    }
}
