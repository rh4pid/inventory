package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.DTO.CustomerDTO;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.CustomerRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.CustomerService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;

/** Customer contact list. */
@Component
public class CustomerImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("firstName", "First name", "Or the business name, for company customers.", "Ama"),
            ImportColumn.optional("lastName", "Last name", "", "Mensah"),
            ImportColumn.optional("phone", "Phone", "Must not belong to another customer.", "0244123456"),
            ImportColumn.optional("email", "Email", "Must not belong to another customer.", "ama@example.com"),
            ImportColumn.optional("address", "Address", "", "Madina, Accra"));

    private final BusinessAccessHelper access;
    private final CustomerRepository customerRepo;
    private final CustomerService customerService;

    public CustomerImporter(BusinessAccessHelper access, CustomerRepository customerRepo, CustomerService customerService) {
        this.access = access;
        this.customerRepo = customerRepo;
        this.customerService = customerService;
    }

    @Override public ImportKind kind() { return ImportKind.CUSTOMERS; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of("Balances customers owe you can't be uploaded yet; record those as credit sales.");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_CUSTOMERS);
    }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        Set<String> phones = new HashSet<>();
        Set<String> emails = new HashSet<>();
        customerRepo.findByBusinessAndDeletedFalse(business, Pageable.unpaged()).forEach(cu -> {
            if (cu.getPhone() != null) phones.add(digits(cu.getPhone()));
            if (cu.getEmail() != null) emails.add(cu.getEmail().trim().toLowerCase(Locale.ROOT));
        });
        Set<String> filePhones = new HashSet<>();
        Set<String> fileEmails = new HashSet<>();
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String first = c.text("firstName", 100);
            String last = c.text("lastName", 100);
            String phone = c.text("phone", 30);
            String mail = c.text("email", 150);
            String address = c.text("address", 255);
            if (phone != null) {
                String d = digits(phone);
                if (phones.contains(d)) c.error("phone", "Another customer already has " + phone);
                else if (!filePhones.add(d)) c.error("phone", phone + " appears more than once in this file");
            }
            if (mail != null) {
                String m = mail.toLowerCase(Locale.ROOT);
                if (!m.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) c.error("email", "\"" + mail + "\" isn't an email address");
                else if (emails.contains(m)) c.error("email", "Another customer already has " + mail);
                else if (!fileEmails.add(m)) c.error("email", mail + " appears more than once in this file");
            }
            if (!c.ok()) continue;
            CustomerDTO dto = new CustomerDTO();
            dto.setFirstName(first);
            dto.setLastName(last);
            dto.setPhone(phone);
            dto.setEmail(mail);
            dto.setAddress(address);
            plan.add(row.number(), r -> {
                customerService.createCustomer(businessId, dto, email);
                r.addCreated();
            });
        }
        return plan;
    }

    private static String digits(String phone) {
        return phone.replaceAll("[^0-9+]", "");
    }
}
