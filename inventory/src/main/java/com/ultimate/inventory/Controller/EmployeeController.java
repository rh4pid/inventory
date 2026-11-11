package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.EmployeeDTO;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Employee;
import com.ultimate.inventory.Service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/businesses/{businessId}")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/employees")
    public ResponseEntity<Employee> create(
            @PathVariable Long businessId,
            @Valid @RequestBody EmployeeDTO dto,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(employeeService.createEmployee(businessId, dto, email));
    }

    @GetMapping("/employees")
    public ResponseEntity<Page<Employee>> list(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(employeeService.listEmployees(
                businessId, email, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/employees/{employeeId}")
    public ResponseEntity<Employee> get(
            @PathVariable Long businessId,
            @PathVariable Long employeeId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(employeeService.getEmployee(businessId, employeeId, email));
    }

    @PutMapping("/employees/{employeeId}")
    public ResponseEntity<Employee> update(
            @PathVariable Long businessId,
            @PathVariable Long employeeId,
            @RequestBody EmployeeDTO dto,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(employeeService.updateEmployee(businessId, employeeId, dto, email));
    }

    @PostMapping("/employees/{employeeId}/terminate")
    public ResponseEntity<Void> terminate(
            @PathVariable Long businessId,
            @PathVariable Long employeeId,
            @AuthenticationPrincipal String email) {
        employeeService.terminateEmployee(businessId, employeeId, email);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff")
    public ResponseEntity<Page<BusinessUser>> listStaff(
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(employeeService.listStaff(
                businessId, email, PageRequest.of(page, size)));
    }
}
