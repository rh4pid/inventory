package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.StaffService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Which staff work at a location. Non-manager staff can only use the locations they are
 * assigned to; owners, admins and managers can use all of them.
 */
@RestController
@RequestMapping("/businesses/{businessId}/shops/{shopId}/staff")
public class LocationStaffController {

    private final StaffService staffService;

    public LocationStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    public record AssignBody(Long businessUserId) {}

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId, @PathVariable Long shopId,
                                  @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(staffService.listLocationStaff(businessId, shopId, email));
    }

    @PostMapping
    public ResponseEntity<?> assign(@PathVariable Long businessId, @PathVariable Long shopId,
                                    @RequestBody AssignBody body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staffService.assignToLocation(businessId, shopId, body.businessUserId(), email));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<?> unassign(@PathVariable Long businessId, @PathVariable Long shopId,
                                      @PathVariable Long assignmentId,
                                      @AuthenticationPrincipal String email) {
        staffService.unassignFromLocation(businessId, shopId, assignmentId, email);
        return ResponseEntity.noContent().build();
    }
}
