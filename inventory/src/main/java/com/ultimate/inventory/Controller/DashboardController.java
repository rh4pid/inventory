package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The overview screen's figures for one location (or all the caller's locations) over a period. */
@RestController
@RequestMapping("/businesses/{businessId}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** period: week | month (default) | quarter | year. */
    @GetMapping
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @RequestParam(required = false) Long shopId,
                                 @RequestParam(defaultValue = "month") String period,
                                 @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(dashboardService.build(businessId, shopId, period, email));
    }
}
