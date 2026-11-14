package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.DTO.SupplierDTO;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Repository.SupplierRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.SupplierService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;

/** Supplier contact list. */
@Component
public class SupplierImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("companyName", "Company name", "Must be new.", "Kasapreko Ltd"),
            ImportColumn.optional("contactPerson", "Contact person", "Who you deal with there.", "Kofi Owusu"),
            ImportColumn.optional("phone", "Phone", "", "0302123456"),
            ImportColumn.optional("phone2", "Other phone", "", "0244000000"),
            ImportColumn.optional("companyEmail", "Company email", "", "sales@kasapreko.com"),
            ImportColumn.optional("contactEmail", "Contact person's email", "", "kofi@kasapreko.com"));

    private final BusinessAccessHelper access;
    private final SupplierRepository supplierRepo;
    private final SupplierService supplierService;

    public SupplierImporter(BusinessAccessHelper access, SupplierRepository supplierRepo, SupplierService supplierService) {
        this.access = access;
        this.supplierRepo = supplierRepo;
        this.supplierService = supplierService;
    }

    @Override public ImportKind kind() { return ImportKind.SUPPLIERS; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public List<String> notes() {
        return List.of("Balances you owe suppliers can't be uploaded yet; record those as credit purchases.");
    }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_SUPPLIERS);
    }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        Set<String> names = new HashSet<>();
        supplierRepo.findByBusinessAndDeletedFalse(business, Pageable.unpaged())
                .forEach(s -> names.add(s.getCompanyName().trim().toLowerCase(Locale.ROOT)));
        Set<String> seen = new HashSet<>();
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String name = c.text("companyName", 150);
            String contact = c.text("contactPerson", 150);
            String phone = c.text("phone", 30);
            String phone2 = c.text("phone2", 30);
            String companyEmail = email(c, "companyEmail");
            String contactEmail = email(c, "contactEmail");
            if (name != null) {
                String k = name.toLowerCase(Locale.ROOT);
                if (names.contains(k)) c.error("companyName", "You already have a supplier called " + name);
                else if (!seen.add(k)) c.error("companyName", name + " appears more than once in this file");
            }
            if (!c.ok()) continue;
            SupplierDTO dto = new SupplierDTO();
            dto.setCompanyName(name);
            dto.setRelationshipOfficer(contact);
            dto.setTelephonePrimary(phone);
            dto.setTelephoneSecondary(phone2);
            dto.setCompanyEmail(companyEmail);
            dto.setRmEmail(contactEmail);
            plan.add(row.number(), r -> {
                supplierService.createSupplier(businessId, dto, email);
                r.addCreated();
            });
        }
        return plan;
    }

    private static String email(Cells c, String key) {
        String v = c.text(key, 150);
        if (v != null && !v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            c.error(key, "\"" + v + "\" isn't an email address");
            return null;
        }
        return v;
    }
}
