package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.TeamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** A company's people: sign-ins, roles and locations. Owners/admins/managers only (see TeamService rules). */
@RestController
@RequestMapping("/businesses/{businessId}/team")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(teamService.list(businessId, email));
    }

    /** Creates the person's sign-in (or adds an existing one); the response carries a temporary password, shown once. */
    @PostMapping
    public ResponseEntity<?> add(@PathVariable Long businessId, @RequestBody TeamService.NewMember body,
                                 @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.add(businessId, body, email));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<?> update(@PathVariable Long businessId, @PathVariable Long memberId,
                                    @RequestBody TeamService.Change body, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(teamService.update(businessId, memberId, body, email));
    }

    @PostMapping("/{memberId}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long businessId, @PathVariable Long memberId,
                                           @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(teamService.resetPassword(businessId, memberId, email));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<?> remove(@PathVariable Long businessId, @PathVariable Long memberId,
                                    @AuthenticationPrincipal String email) {
        teamService.remove(businessId, memberId, email);
        return ResponseEntity.noContent().build();
    }

    // ── Invitations (people who already have an account) ─────────────────────

    @PostMapping("/invitations")
    public ResponseEntity<?> invite(@PathVariable Long businessId, @RequestBody TeamService.Invite body,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.invite(businessId, body, email));
    }

    @PostMapping("/invitations/{invitationId}/resend")
    public ResponseEntity<?> resend(@PathVariable Long businessId, @PathVariable Long invitationId,
                                    @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(teamService.resendInvitation(businessId, invitationId, email));
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<?> cancel(@PathVariable Long businessId, @PathVariable Long invitationId,
                                    @AuthenticationPrincipal String email) {
        teamService.cancelInvitation(businessId, invitationId, email);
        return ResponseEntity.noContent().build();
    }
}
