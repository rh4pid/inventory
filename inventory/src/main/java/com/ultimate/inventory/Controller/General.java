package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.ResponseDTO;
import com.ultimate.inventory.DTO.ShopDTO;
import com.ultimate.inventory.DTO.ShopKeepDTO;
import com.ultimate.inventory.Service.ShopService;
import com.ultimate.inventory.Service.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/general")
public class General {

    @Autowired private ShopService shopService;
    @Autowired private StaffService staffService;

    // ===== SHOP =====

    @PostMapping("/shop")
    public ResponseEntity<?> createShop(@RequestBody @Validated ShopDTO dto,
                                        @AuthenticationPrincipal String email) {
        shopService.createShop(dto, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Shop created successfully"));
    }

    @PostMapping("/shop/bulk/{businessId}")
    public ResponseEntity<?> createShopsBulk(@PathVariable Long businessId,
                                             @RequestPart("file") MultipartFile file,
                                             @AuthenticationPrincipal String email) {
        shopService.createShopsBulk(businessId, file, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Shops created successfully"));
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<?> getShop(@PathVariable Long shopId,
                                     @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(shopService.getShop(shopId, email));
    }

    @GetMapping("/business/{businessId}/shops")
    public ResponseEntity<?> getShops(@PathVariable Long businessId,
                                      @AuthenticationPrincipal String email,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(defaultValue = "false") boolean mine) {
        return ResponseEntity.ok(shopService.getShopsByBusiness(businessId, email,
                PageRequest.of(page, size, Sort.by("id").ascending()), mine));
    }

    @PutMapping("/shop/{shopId}")
    public ResponseEntity<?> updateShop(@PathVariable Long shopId,
                                        @RequestBody @Validated ShopDTO dto,
                                        @AuthenticationPrincipal String email) {
        shopService.updateShop(shopId, dto, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Shop updated successfully"));
    }

    @DeleteMapping("/shop/{shopId}")
    public ResponseEntity<?> deleteShop(@PathVariable Long shopId,
                                        @AuthenticationPrincipal String email) {
        shopService.deleteShop(shopId, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Shop deleted successfully"));
    }

    // ===== STAFF =====

    @PostMapping("/shopkeep/{businessId}")
    public ResponseEntity<?> addStaff(@RequestBody @Validated ShopKeepDTO dto,
                                      @PathVariable Long businessId,
                                      @AuthenticationPrincipal String email) {
        staffService.addStaff(dto, businessId, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Staff added successfully"));
    }

    @PostMapping("/shopkeep/bulk/{businessId}")
    public ResponseEntity<?> addStaffBulk(@RequestPart("file") MultipartFile file,
                                          @PathVariable Long businessId,
                                          @AuthenticationPrincipal String email) {
        staffService.addStaffBulk(file, businessId, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Staff added successfully"));
    }

    @GetMapping("/shopkeep/{businessUserId}")
    public ResponseEntity<?> getStaff(@PathVariable Long businessUserId,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(staffService.getStaff(businessUserId, email));
    }

    @GetMapping("/business/{businessId}/shopkeeps")
    public ResponseEntity<?> getStaffByBusiness(@PathVariable Long businessId,
                                                @AuthenticationPrincipal String email,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(staffService.getStaffByBusiness(businessId, email,
                PageRequest.of(page, size, Sort.by("id").ascending())));
    }

    @PutMapping("/shopkeep/{shopUserId}")
    public ResponseEntity<?> updateStaff(@PathVariable Long shopUserId,
                                         @RequestBody @Validated ShopKeepDTO dto,
                                         @AuthenticationPrincipal String email) {
        staffService.updateStaff(shopUserId, dto, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Staff updated successfully"));
    }

    @DeleteMapping("/shopkeep/{shopUserId}")
    public ResponseEntity<?> removeStaff(@PathVariable Long shopUserId,
                                         @AuthenticationPrincipal String email) {
        staffService.removeStaff(shopUserId, email);
        return ResponseEntity.ok(new ResponseDTO(true, "Staff removed successfully"));
    }
}
