package com.ultimate.inventory.Service;


import com.ultimate.inventory.Constants.Capability;import com.ultimate.inventory.DTO.CustomerDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.Customer;
import com.ultimate.inventory.Repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepo;
    private final BusinessAccessHelper accessHelper;

    public CustomerService(CustomerRepository customerRepo, BusinessAccessHelper accessHelper) {
        this.customerRepo = customerRepo;
        this.accessHelper = accessHelper;
    }

    @Transactional
    public Customer createCustomer(Long businessId, CustomerDTO dto, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CUSTOMERS);
        Business business = accessHelper.requireBusiness(businessId);

        Customer customer = new Customer();
        customer.setBusiness(business);
        customer.setCreatedBy(userEmail);
        customer.setCreatedAt(new Date());
        applyDto(customer, dto);

        Customer saved = customerRepo.save(customer);
        log.info("Customer created: {} by {}", saved.getId(), userEmail);
        return saved;
    }

    public Page<Customer> listCustomers(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CUSTOMERS, Capability.SELL);
        Business business = accessHelper.requireBusiness(businessId);
        return customerRepo.findByBusinessAndDeletedFalse(business, pageable);
    }

    public Customer getCustomer(Long businessId, Long customerId, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CUSTOMERS, Capability.SELL);
        Business business = accessHelper.requireBusiness(businessId);
        return customerRepo.findByIdAndBusinessAndDeletedFalse(customerId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    @Transactional
    public Customer updateCustomer(Long businessId, Long customerId, CustomerDTO dto, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.MANAGE_CUSTOMERS);
        Business business = accessHelper.requireBusiness(businessId);

        Customer customer = customerRepo.findByIdAndBusinessAndDeletedFalse(customerId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Customer not found"));

        customer.setUpdatedBy(userEmail);
        customer.setUpdatedAt(new Date());
        applyDto(customer, dto);
        return customerRepo.save(customer);
    }

    @Transactional
    public void deleteCustomer(Long businessId, Long customerId, String userEmail) {
        accessHelper.requireCapability(businessId, userEmail, Capability.DELETE_CUSTOMERS);
        Business business = accessHelper.requireBusiness(businessId);

        Customer customer = customerRepo.findByIdAndBusinessAndDeletedFalse(customerId, business)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Customer not found"));

        customer.setDeleted(true);
        customer.setUpdatedBy(userEmail);
        customer.setUpdatedAt(new Date());
        customerRepo.save(customer);
        log.info("Customer soft-deleted: {} by {}", customerId, userEmail);
    }

    private void applyDto(Customer customer, CustomerDTO dto) {
        customer.setFirstName(dto.getFirstName());
        customer.setLastName(dto.getLastName());
        customer.setEmail(dto.getEmail());
        customer.setPhone(dto.getPhone());
        customer.setAddress(dto.getAddress());
    }
}
