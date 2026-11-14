package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.ExcelUpload.UploadShoppers;
import com.ultimate.inventory.DTO.ShopKeepDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.ShopUsers;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Repository.ShopUsersRepository;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.ExcelService;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;

/**
 * Business members and which locations they work at. Owners, admins and managers work at every
 * location; other staff (cashiers, stock staff…) only at the locations they are assigned to.
 */
// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    /** A member assigned to a location, as shown on the locations screen. */
    public record LocationStaff(Long id, Long businessUserId, String name, String email, UserRoles role) {}

    @Autowired private ShopRepository shopRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private BusinessUserRepo businessUserRepo;
    @Autowired private ShopUsersRepository shopUsersRepo;
    @Autowired private ExcelService excelService;
    @Autowired private BusinessAccessHelper accessHelper;

    @Transactional
    public void addStaff(ShopKeepDTO dto, Long businessId, String actorEmail) {
        BusinessUser actor = accessHelper.requireManager(businessId, actorEmail);
        Business business = accessHelper.requireBusiness(businessId);
        Shop shop = requireShopByCode(dto.getShopCode(), businessId);

        User user = userRepo.findByEmail(dto.getEmail());
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "User not found with email: " + dto.getEmail());
        }

        UserRoles role = parseRole(dto.getRoles());
        BusinessUser businessUser = upsertBusinessUser(business, user, role, actor);
        assign(businessUser, shop, actorEmail);

        log.info("Staff added: {} to shop: {} in business: {}", user.getEmail(), shop.getShopCode(), business.getCode());
    }

    @Transactional
    public void addStaffBulk(MultipartFile file, Long businessId, String uploaderEmail) {
        BusinessUser actor = accessHelper.requireManager(businessId, uploaderEmail);
        Business business = accessHelper.requireBusiness(businessId);

        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Excel file is required");
        }

        List<UploadShoppers> rows = excelService.readExcel(file, UploadShoppers.class);
        if (rows == null || rows.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No valid data found in the uploaded file");
        }

        for (UploadShoppers row : rows) {
            Shop shop = requireShopByCode(row.getShopCode(), businessId);

            User user = userRepo.findByEmail(row.getUserEmail());
            if (user == null) {
                throw new AppException(HttpStatus.NOT_FOUND, "User not found: " + row.getUserEmail());
            }

            UserRoles role = parseRole(row.getRoles());
            BusinessUser businessUser = upsertBusinessUser(business, user, role, actor);
            assign(businessUser, shop, uploaderEmail);
        }

        log.info("Bulk added {} staff members to business: {}", rows.size(), business.getCode());
    }

    public BusinessUser getStaff(Long businessUserId, String actorEmail) {
        BusinessUser bu = businessUserRepo.findById(businessUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff member not found"));
        accessHelper.requireMembership(bu.getBusiness().getId(), actorEmail);
        return bu;
    }

    public Page<BusinessUser> getStaffByBusiness(Long businessId, String userEmail, Pageable pageable) {
        accessHelper.requireMembership(businessId, userEmail);
        return businessUserRepo.findByBusiness(accessHelper.requireBusiness(businessId), pageable);
    }

    @Transactional
    public void updateStaff(Long shopUserId, ShopKeepDTO dto, String actorEmail) {
        ShopUsers shopUser = shopUsersRepo.findById(shopUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff assignment not found"));
        Long businessId = shopUser.getBusinessUser().getBusiness().getId();
        BusinessUser actor = accessHelper.requireManager(businessId, actorEmail);
        Shop shop = requireShopByCode(dto.getShopCode(), businessId);
        UserRoles role = parseRole(dto.getRoles());
        checkMayChange(actor, shopUser.getBusinessUser(), role);

        shopUser.setShop(shop);
        shopUser.setRoles(role);
        shopUser.getBusinessUser().setRoles(role);
        businessUserRepo.save(shopUser.getBusinessUser());
        shopUsersRepo.save(shopUser);
    }

    @Transactional
    public void removeStaff(Long shopUserId, String actorEmail) {
        ShopUsers shopUser = shopUsersRepo.findById(shopUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff assignment not found"));
        accessHelper.requireManager(shopUser.getBusinessUser().getBusiness().getId(), actorEmail);
        shopUsersRepo.delete(shopUser);
        log.info("Staff assignment removed: {} by {}", shopUserId, actorEmail);
    }

    // ── Location assignments ─────────────────────────────────────────────────

    public List<LocationStaff> listLocationStaff(Long businessId, Long shopId, String actorEmail) {
        accessHelper.requireManager(businessId, actorEmail);
        Shop shop = accessHelper.requireShopInBusiness(shopId, businessId);
        return shopUsersRepo.findActiveByShop(shop).stream().map(StaffService::view).toList();
    }

    @Transactional
    public LocationStaff assignToLocation(Long businessId, Long shopId, Long businessUserId, String actorEmail) {
        accessHelper.requireManager(businessId, actorEmail);
        Shop shop = accessHelper.requireShopInBusiness(shopId, businessId);
        if (businessUserId == null) throw new AppException(HttpStatus.BAD_REQUEST, "Choose a staff member");
        BusinessUser member = businessUserRepo.findById(businessUserId)
                .filter(bu -> bu.getBusiness().getId().equals(businessId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff member not found in this business"));
        boolean already = shopUsersRepo.findActiveByShop(shop).stream()
                .anyMatch(su -> su.getBusinessUser().getId().equals(member.getId()));
        if (already) {
            throw new AppException(HttpStatus.CONFLICT,
                    displayName(member.getUser()) + " is already assigned to " + shop.getShopName());
        }
        ShopUsers su = assign(member, shop, actorEmail);
        log.info("{} assigned to location {} by {}", member.getUser().getEmail(), shop.getId(), actorEmail);
        return view(su);
    }

    @Transactional
    public void unassignFromLocation(Long businessId, Long shopId, Long shopUserId, String actorEmail) {
        accessHelper.requireManager(businessId, actorEmail);
        Shop shop = accessHelper.requireShopInBusiness(shopId, businessId);
        ShopUsers su = shopUsersRepo.findById(shopUserId)
                .filter(x -> x.getShop() != null && x.getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Assignment not found"));
        shopUsersRepo.delete(su);
        log.info("Assignment {} removed from location {} by {}", shopUserId, shopId, actorEmail);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ShopUsers assign(BusinessUser member, Shop shop, String by) {
        ShopUsers su = new ShopUsers();
        su.setShop(shop);
        su.setBusinessUser(member);
        su.setRoles(member.getRoles());
        su.setDeleted(false);
        su.setCreatedBy(by);
        su.setCreatedAt(new Date());
        return shopUsersRepo.save(su);
    }

    private Shop requireShopByCode(String code, Long businessId) {
        Shop shop = shopRepo.findByShopCode(code)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found: " + code));
        if (!shop.getBusiness().getId().equals(businessId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Shop does not belong to this business");
        }
        return shop;
    }

    private BusinessUser upsertBusinessUser(Business business, User user, UserRoles role, BusinessUser actor) {
        BusinessUser bu = businessUserRepo.findByBusinessAndUser(business, user);
        checkMayChange(actor, bu, role);
        if (bu == null) {
            bu = new BusinessUser();
            bu.setBusiness(business);
            bu.setUser(user);
        }
        bu.setRoles(role);
        return businessUserRepo.save(bu);
    }

    /**
     * Managers may only give or change non-manager roles; owners and admins may give any role
     * except owner; only an owner may make or change an owner.
     */
    private static void checkMayChange(BusinessUser actor, BusinessUser target, UserRoles newRole) {
        UserRoles a = actor.getRoles();
        boolean touchesOwner = newRole == UserRoles.OWNER || (target != null && target.getRoles() == UserRoles.OWNER);
        if (touchesOwner && a != UserRoles.OWNER) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only an owner can grant or change the owner role");
        }
        boolean managerRole = BusinessAccessHelper.MANAGERS.contains(newRole)
                || (target != null && BusinessAccessHelper.MANAGERS.contains(target.getRoles()));
        if (managerRole && a == UserRoles.MANAGER) {
            throw new AppException(HttpStatus.FORBIDDEN, "Managers can only add or change non-manager staff");
        }
    }

    private static LocationStaff view(ShopUsers su) {
        BusinessUser bu = su.getBusinessUser();
        return new LocationStaff(su.getId(), bu.getId(), displayName(bu.getUser()),
                bu.getUser().getEmail(), bu.getRoles());
    }

    private static String displayName(User u) {
        String n = ((u.getFirstName() != null ? u.getFirstName() : "") + " " +
                (u.getLastName() != null ? u.getLastName() : "")).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }

    private static UserRoles parseRole(String role) {
        try {
            return UserRoles.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid role: " + role);
        }
    }
}
