package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.BusinessType;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The super admin's console: companies and their administrators. The super admin manages the
 * platform, not the companies' data: nothing here reads sales, stock or customers.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Set<UserRoles> ADMIN_ROLES = EnumSet.of(UserRoles.OWNER, UserRoles.ADMIN);

    private final BusinessAccessHelper accessHelper;
    private final BusinessRepository businessRepo;
    private final BusinessUserRepo businessUserRepo;
    private final ShopRepository shopRepo;
    private final ProductRepository productRepo;
    private final StockMovementRepository movementRepo;
    private final SettingsService settingsService;
    private final UserAccounts accounts;

    public AdminService(BusinessAccessHelper accessHelper, BusinessRepository businessRepo,
                        BusinessUserRepo businessUserRepo, ShopRepository shopRepo, ProductRepository productRepo,
                        StockMovementRepository movementRepo, SettingsService settingsService, UserAccounts accounts) {
        this.accessHelper = accessHelper;
        this.businessRepo = businessRepo;
        this.businessUserRepo = businessUserRepo;
        this.shopRepo = shopRepo;
        this.productRepo = productRepo;
        this.movementRepo = movementRepo;
        this.settingsService = settingsService;
        this.accounts = accounts;
    }

    // ── Shapes ───────────────────────────────────────────────────────────────

    public record AdminView(Long membershipId, Long userId, String name, String email, String phone, UserRoles role,
                            boolean pendingFirstSignIn) {}

    public record CompanyView(Long id, String name, String code, String email, String contact,
                              BusinessType businessType, String businessTypeLabel, boolean suspended, Date createdAt,
                              List<AdminView> admins, long locations, long members, long products, Date lastActivity) {}

    public record NewPerson(String firstName, String lastName, String email, String phone) {}

    public record CreateCompany(String name, String email, String contact, BusinessType businessType, NewPerson admin) {}

    public record UpdateCompany(String name, String email, String contact) {}

    /** What to hand over to the new admin. temporaryPassword is null when they already had an account. */
    public record Credentials(String name, String email, String temporaryPassword, boolean existingAccount) {}

    public record Result(CompanyView company, Credentials credentials) {}

    // ── Queries ──────────────────────────────────────────────────────────────

    public List<CompanyView> list(String email) {
        accessHelper.requireSuperAdmin(email);
        return businessRepo.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .sorted(Comparator.comparing(Business::getId).reversed())
                .map(this::view).toList();
    }

    public CompanyView get(Long companyId, String email) {
        accessHelper.requireSuperAdmin(email);
        return view(requireCompany(companyId));
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /** Creates a company, applies its business type's feature preset, and gives it its first admin. */
    @Transactional
    public Result create(CreateCompany req, String email) {
        accessHelper.requireSuperAdmin(email);
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Company name is required");
        }
        if (req.admin() == null) throw new AppException(HttpStatus.BAD_REQUEST, "The company needs an admin");
        String bizEmail = req.email() != null && !req.email().isBlank() ? UserAccounts.normalizeEmail(req.email()) : null;
        if (bizEmail != null && businessRepo.findByBusinessEmail(bizEmail) != null) {
            throw new AppException(HttpStatus.CONFLICT, "Another company already uses " + bizEmail);
        }

        Business b = new Business();
        b.setName(req.name().trim());
        b.setBusinessEmail(bizEmail);
        b.setBusinessContact(blankToNull(req.contact()));
        b.setSuspended(false);
        b.setLocked(false);
        b.setDeleted(false);
        b.setCreatedBy(email);
        b.setCreatedAt(new Date());
        // Code needs the id: save with a unique placeholder first (the column is NOT NULL).
        b.setCode("INIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        b = businessRepo.save(b);
        b.setCode("BIZ" + String.format("%05d", b.getId()));
        b = businessRepo.save(b);
        settingsService.applyBusinessType(b, req.businessType() != null ? req.businessType() : BusinessType.GENERAL_RETAIL, email);

        Credentials creds = addAdminTo(b, req.admin(), email);
        log.info("Company {} ({}) created by super admin {} with admin {}", b.getCode(), b.getName(), email, creds.email());
        return new Result(view(b), creds);
    }

    @Transactional
    public CompanyView update(Long companyId, UpdateCompany req, String email) {
        accessHelper.requireSuperAdmin(email);
        Business b = requireCompany(companyId);
        if (req.name() != null) {
            if (req.name().isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Company name is required");
            b.setName(req.name().trim());
        }
        if (req.email() != null) {
            String e = req.email().isBlank() ? null : UserAccounts.normalizeEmail(req.email());
            Business other = e != null ? businessRepo.findByBusinessEmail(e) : null;
            if (other != null && !other.getId().equals(b.getId())) {
                throw new AppException(HttpStatus.CONFLICT, "Another company already uses " + e);
            }
            b.setBusinessEmail(e);
        }
        if (req.contact() != null) b.setBusinessContact(blankToNull(req.contact()));
        b.setUpdatedBy(email);
        b.setUpdatedAt(new Date());
        return view(businessRepo.save(b));
    }

    /** Suspended companies: nobody in them can sign in or use them until reactivated. Data is kept. */
    @Transactional
    public CompanyView setSuspended(Long companyId, boolean suspended, String email) {
        accessHelper.requireSuperAdmin(email);
        Business b = requireCompany(companyId);
        b.setSuspended(suspended);
        b.setUpdatedBy(email);
        b.setUpdatedAt(new Date());
        log.info("Company {} {} by super admin {}", b.getCode(), suspended ? "suspended" : "reactivated", email);
        return view(businessRepo.save(b));
    }

    @Transactional
    public Result addAdmin(Long companyId, NewPerson person, String email) {
        accessHelper.requireSuperAdmin(email);
        Business b = requireCompany(companyId);
        Credentials creds = addAdminTo(b, person, email);
        return new Result(view(b), creds);
    }

    /** New temporary password for one of the company's admins (signs them out everywhere). */
    @Transactional
    public Result resetAdminPassword(Long companyId, Long membershipId, String email) {
        accessHelper.requireSuperAdmin(email);
        Business b = requireCompany(companyId);
        BusinessUser bu = businessUserRepo.findById(membershipId)
                .filter(m -> m.getBusiness().getId().equals(b.getId()) && m.isActiveMember() && ADMIN_ROLES.contains(m.getRoles()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Admin not found in this company"));
        String temp = accounts.resetToTemporary(bu.getUser(), email);
        log.info("Super admin {} reset the password of {} ({})", email, bu.getUser().getEmail(), b.getCode());
        return new Result(view(b), new Credentials(UserAccounts.displayName(bu.getUser()), bu.getUser().getEmail(), temp, false));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Credentials addAdminTo(Business b, NewPerson person, String by) {
        if (person == null) throw new AppException(HttpStatus.BAD_REQUEST, "Admin details are required");
        UserAccounts.Provisioned p = accounts.findOrCreate(person.firstName(), person.lastName(), person.email(), person.phone(), by);
        BusinessUser existing = businessUserRepo.findByBusinessAndUser(b, p.user());
        if (existing != null && existing.isActiveMember() && ADMIN_ROLES.contains(existing.getRoles())) {
            throw new AppException(HttpStatus.CONFLICT, UserAccounts.displayName(p.user()) + " is already an admin of " + b.getName());
        }
        BusinessUser bu = existing != null ? existing : new BusinessUser();
        bu.setBusiness(b);
        bu.setUser(p.user());
        bu.setRoles(UserRoles.ADMIN);
        bu.setActive(true);
        if (existing == null) { bu.setCreatedBy(by); bu.setCreatedAt(new Date()); }
        else { bu.setUpdatedBy(by); bu.setUpdatedAt(new Date()); }
        businessUserRepo.save(bu);
        return new Credentials(UserAccounts.displayName(p.user()), p.user().getEmail(), p.temporaryPassword(), p.existing());
    }

    private CompanyView view(Business b) {
        List<BusinessUser> members = businessUserRepo.findAllByBusiness(b).stream().filter(BusinessUser::isActiveMember).toList();
        List<AdminView> admins = members.stream()
                .filter(m -> ADMIN_ROLES.contains(m.getRoles()))
                .map(m -> new AdminView(m.getId(), m.getUser().getId(), UserAccounts.displayName(m.getUser()),
                        m.getUser().getEmail(), m.getUser().getTelephonePrimary(), m.getRoles(),
                        Boolean.TRUE.equals(m.getUser().getMustChangePassword())))
                .toList();
        BusinessType type = settingsService.forBusiness(b).getBusinessType();
        return new CompanyView(b.getId(), b.getName(), b.getCode(), b.getBusinessEmail(), b.getBusinessContact(),
                type, type != null ? type.getLabel() : null, Boolean.TRUE.equals(b.getSuspended()), b.getCreatedAt(),
                admins, shopRepo.countActiveByBusiness(b), members.size(), productRepo.countByBusinessAndDeletedFalse(b),
                movementRepo.lastActivity(b));
    }

    private Business requireCompany(Long id) {
        return businessRepo.findById(id).filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
