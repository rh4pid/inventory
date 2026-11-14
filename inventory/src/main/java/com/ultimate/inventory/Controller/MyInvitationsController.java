package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Invitations sent to you: see them, accept (you join the company) or decline. */
@RestController
@RequestMapping("/users/me/invitations")
public class MyInvitationsController {

    private final TeamService teamService;

    public MyInvitationsController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(teamService.myInvitations(email));
    }

    /** Returns your new membership in the company. */
    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long invitationId, @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(teamService.acceptInvitation(invitationId, email));
    }

    @PostMapping("/{invitationId}/decline")
    public ResponseEntity<?> decline(@PathVariable Long invitationId, @AuthenticationPrincipal String email) {
        teamService.declineInvitation(invitationId, email);
        return ResponseEntity.noContent().build();
    }
}
