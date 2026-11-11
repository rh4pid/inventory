package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.EmployeeDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Employee;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepo;
    private final BusinessUserRepo businessUserRepo;
    private final BusinessAccessHelper accessHelper;

    public EmployeeService(EmployeeRepository employeeRepo, BusinessUserRepo businessUserRepo,
                           BusinessAccessHelper accessHelper) {
        this.employeeRepo = employeeRepo;
        this.businessUserRepo = businessUserRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Employee createEmployee(Long businessId, EmployeeDTO dto, String userEmail) {
        accessHelper.requireRole(businessId, userEmail, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
        Business business = accessHelper.requireBusiness(businessId);

        BusinessUser businessUser = businessUserRepo.findById(dto.getBusinessUserId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff member not found"));

        if (!businessUser.getBusiness().getId().equals(businessId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Staff member does not belong to this business");
        }

        Employee employee = new Employee();
        employee.setBusinessUser(businessUser);
        employee.setPosition(dto.getPosition());
        employee.setDepartment(dto.getDepartment());
        employee.setSalary(dto.getSalary());
        employee.setSalaryType(dto.getSalaryType());
        employee.setStartDate(dto.getStartDate() != null ? dto.getStartDate() : new Date());
        employee.setEndDate(dto.getEndDate());
        employee.setCreatedBy(userEmail);
        employee.setCreatedAt(new Date());

        Employee saved = employeeRepo.save(employee);
        log.info("Employee record created for businessUser={} in business={}", dto.getBusinessUserId(), businessId);
        return saved;
    }

    public Page<Employee> listEmployees(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return employeeRepo.findByBusinessUser_Business(business, pageable);
    }

    public Employee getEmployee(Long businessId, Long employeeId, String userEmail) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return employeeRepo.findByIdAndBusinessUser_Business(employeeId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    @Transactional
    public Employee updateEmployee(Long businessId, Long employeeId, EmployeeDTO dto, String userEmail) {
        accessHelper.requireRole(businessId, userEmail, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
        Business business = accessHelper.requireBusiness(businessId);

        Employee employee = employeeRepo.findByIdAndBusinessUser_Business(employeeId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Employee not found"));

        if (dto.getPosition() != null)   employee.setPosition(dto.getPosition());
        if (dto.getDepartment() != null) employee.setDepartment(dto.getDepartment());
        if (dto.getSalary() != null)     employee.setSalary(dto.getSalary());
        if (dto.getSalaryType() != null) employee.setSalaryType(dto.getSalaryType());
        if (dto.getStartDate() != null)  employee.setStartDate(dto.getStartDate());
        if (dto.getEndDate() != null)    employee.setEndDate(dto.getEndDate());
        employee.setUpdatedBy(userEmail);
        employee.setUpdatedAt(new Date());

        return employeeRepo.save(employee);
    }

    @Transactional
    public void terminateEmployee(Long businessId, Long employeeId, String userEmail) {
        accessHelper.requireRole(businessId, userEmail, UserRoles.OWNER, UserRoles.ADMIN);
        Business business = accessHelper.requireBusiness(businessId);

        Employee employee = employeeRepo.findByIdAndBusinessUser_Business(employeeId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Employee not found"));

        employee.setEndDate(new Date());
        employee.setUpdatedBy(userEmail);
        employee.setUpdatedAt(new Date());
        employeeRepo.save(employee);
        log.info("Employee terminated: id={} in business={}", employeeId, businessId);
    }

    public Page<BusinessUser> listStaff(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        return businessUserRepo.findByBusiness(business, pageable);
    }
}
