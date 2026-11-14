package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.BusinessMembershipDTO;
import com.ultimate.inventory.DTO.UpdateProfileDTO;
import com.ultimate.inventory.DTO.UserResponseDTO;
import com.ultimate.inventory.Service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal String email) {
        UserResponseDTO profile = userService.getMyProfile(email);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal String email,
                                           @RequestBody @Valid UpdateProfileDTO dto) {
        UserResponseDTO updated = userService.updateProfile(email, dto);
        return ResponseEntity.ok(updated);
    }

    public record PasswordChange(String currentPassword, String newPassword) {}

    @PostMapping("/me/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal String email, @RequestBody PasswordChange body) {
        return ResponseEntity.ok(userService.changePassword(email, body.currentPassword(), body.newPassword()));
    }

    @GetMapping("/me/businesses")
    public ResponseEntity<?> getMyBusinesses(@AuthenticationPrincipal String email) {
        List<BusinessMembershipDTO> businesses = userService.getMyBusinesses(email);
        return ResponseEntity.ok(businesses);
    }

    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestParam String q) {
        UserResponseDTO user = userService.lookupUser(q);
        return ResponseEntity.ok(user);
    }
}
