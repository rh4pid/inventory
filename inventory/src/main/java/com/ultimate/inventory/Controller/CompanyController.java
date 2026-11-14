package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /** Create a company of your own; you become its owner. */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CompanyService.NewCompany req, @AuthenticationPrincipal String email) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(req, email));
    }
}
