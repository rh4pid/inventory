package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Super admin console: companies and their administrators. Every call checks the caller is a super admin. */
@RestController
@RequestMapping("/admin/companies")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adminService.list(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adminService.get(id, email));
    }

    /** Creates the company and its first admin; the response carries the admin's temporary password (shown once). */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody AdminService.CreateCompany body, @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(body, email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody AdminService.UpdateCompany body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adminService.update(id, body, email));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<?> suspend(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adminService.setSuspended(id, true, email));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adminService.setSuspended(id, false, email));
    }

    @PostMapping("/{id}/admins")
    public ResponseEntity<?> addAdmin(@PathVariable Long id, @RequestBody AdminService.NewPerson body,
                                      @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addAdmin(id, body, email));
    }

    @PostMapping("/{id}/admins/{membershipId}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @PathVariable Long membershipId,
                                           @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(adminService.resetAdminPassword(id, membershipId, email));
    }
}
