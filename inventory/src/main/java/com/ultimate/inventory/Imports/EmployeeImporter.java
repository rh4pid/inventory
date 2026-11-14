package com.ultimate.inventory.Imports;

import com.ultimate.inventory.Constants.Capability;
import com.ultimate.inventory.Constants.SalaryType;
import com.ultimate.inventory.DTO.EmployeeDTO;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.EmployeeRepository;
import com.ultimate.inventory.Service.BusinessAccessHelper;
import com.ultimate.inventory.Service.EmployeeService;
import com.ultimate.inventory.Service.UserAccounts;
import com.ultimate.inventory.Service.Usernames;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Payroll records for people already on the team (add them first, by hand or with the team upload). */
@Component
public class EmployeeImporter implements Importer {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("person", "Username or email", "Someone already on the team.", "ama_mensah"),
            ImportColumn.optional("position", "Position", "", "Sales assistant"),
            ImportColumn.optional("department", "Department", "", "Sales"),
            ImportColumn.optional("salary", "Salary", "Pay per period (see Salary type).", "2500.00"),
            ImportColumn.optional("salaryType", "Salary type", "How often the salary is paid. Blank = monthly.", "Monthly")
                    .withOptions(Arrays.stream(SalaryType.values()).map(Cells::pretty).toList()),
            ImportColumn.optional("start", "Start date", "yyyy-mm-dd. Blank = today.", "2025-03-01"));

    private final BusinessAccessHelper access;
    private final BusinessUserRepo memberRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeService employeeService;

    public EmployeeImporter(BusinessAccessHelper access, BusinessUserRepo memberRepo, EmployeeRepository employeeRepo,
                            EmployeeService employeeService) {
        this.access = access;
        this.memberRepo = memberRepo;
        this.employeeRepo = employeeRepo;
        this.employeeService = employeeService;
    }

    @Override public ImportKind kind() { return ImportKind.EMPLOYEES; }
    @Override public List<ImportColumn> columns() { return COLUMNS; }

    @Override
    public void authorize(Long businessId, String email) {
        access.requireCapability(businessId, email, Capability.MANAGE_PAYROLL);
    }

    @Override
    public Plan prepare(Long businessId, List<SheetRow> rows, ImportResult result, String email) {
        Business business = access.requireBusiness(businessId);
        // Team members by username and by email.
        Map<String, BusinessUser> members = new HashMap<>();
        for (BusinessUser bu : memberRepo.findAllByBusiness(business)) {
            if (!bu.isActiveMember()) continue;
            if (bu.getUser().getUsername() != null) members.put(bu.getUser().getUsername().toLowerCase(Locale.ROOT), bu);
            members.put(bu.getUser().getEmail().toLowerCase(Locale.ROOT), bu);
        }
        Set<Long> onPayroll = new HashSet<>();
        employeeRepo.findByBusinessUser_Business(business, Pageable.unpaged())
                .forEach(e -> { if (e.getEndDate() == null) onPayroll.add(e.getBusinessUser().getId()); });

        Set<Long> seen = new HashSet<>();
        Plan plan = new Plan();
        for (SheetRow row : rows) {
            Cells c = new Cells(row, result, COLUMNS);
            String who = c.text("person");
            String position = c.text("position", 120);
            String department = c.text("department", 120);
            BigDecimal salary = c.positive("salary");
            SalaryType type = c.choice("salaryType", SalaryType.class, Cells::pretty);
            LocalDate start = c.date("start");

            BusinessUser bu = null;
            if (who != null) {
                String k = who.contains("@") && who.indexOf('@') > 0 ? who.trim().toLowerCase(Locale.ROOT) : Usernames.clean(who);
                bu = members.get(k);
                if (bu == null) c.error("person", "\"" + who + "\" isn't on the team. Add them under Settings → Team first.");
                else if (onPayroll.contains(bu.getId())) c.error("person", UserAccounts.displayName(bu.getUser()) + " is already on the payroll");
                else if (!seen.add(bu.getId())) c.error("person", UserAccounts.displayName(bu.getUser()) + " appears more than once in this file");
            }
            if (!c.ok()) continue;
            EmployeeDTO dto = new EmployeeDTO();
            dto.setBusinessUserId(bu.getId());
            dto.setPosition(position);
            dto.setDepartment(department);
            dto.setSalary(salary != null ? salary.doubleValue() : null);
            dto.setSalaryType(type != null ? type : SalaryType.MONTHLY);
            if (start != null) dto.setStartDate(Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            plan.add(row.number(), r -> {
                employeeService.createEmployee(businessId, dto, email);
                r.addCreated();
            });
        }
        return plan;
    }
}
