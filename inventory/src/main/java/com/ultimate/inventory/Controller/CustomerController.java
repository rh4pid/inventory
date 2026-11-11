package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.CustomerDTO;
import com.ultimate.inventory.Model.Customer;
import com.ultimate.inventory.Service.CustomerService;
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
@RequestMapping("/businesses/{businessId}/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long businessId,
                                    @RequestBody @Valid CustomerDTO dto,
                                    @AuthenticationPrincipal String email) {
        Customer customer = customerService.createCustomer(businessId, dto, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long businessId,
                                  @PageableDefault(size = 20) Pageable pageable,
                                  @AuthenticationPrincipal String email) {
        Page<Customer> page = customerService.listCustomers(businessId, email, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<?> get(@PathVariable Long businessId,
                                 @PathVariable Long customerId,
                                 @AuthenticationPrincipal String email) {
        Customer customer = customerService.getCustomer(businessId, customerId, email);
        return ResponseEntity.ok(customer);
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<?> update(@PathVariable Long businessId,
                                    @PathVariable Long customerId,
                                    @RequestBody @Valid CustomerDTO dto,
                                    @AuthenticationPrincipal String email) {
        Customer customer = customerService.updateCustomer(businessId, customerId, dto, email);
        return ResponseEntity.ok(customer);
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<?> delete(@PathVariable Long businessId,
                                    @PathVariable Long customerId,
                                    @AuthenticationPrincipal String email) {
        customerService.deleteCustomer(businessId, customerId, email);
        return ResponseEntity.ok(Map.of("success", true, "message", "Customer deleted"));
    }
}
