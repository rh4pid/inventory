package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.ShopUsers;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessRepository;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Repository.ShopUsersRepository;
import com.ultimate.inventory.Repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BusinessAccessHelper {

    /** Roles that work across every location of the business. */
    public static final Set<UserRoles> MANAGERS = EnumSet.of(UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);

    private final UserRepository userRepo;
    private final BusinessRepository businessRepo;
    private final BusinessUserRepo businessUserRepo;
    private final ShopRepository shopRepo;
    private final ShopUsersRepository shopUsersRepo;

    public BusinessAccessHelper(UserRepository userRepo, BusinessRepository businessRepo,
                                BusinessUserRepo businessUserRepo, ShopRepository shopRepo,
                                ShopUsersRepository shopUsersRepo) {
        this.userRepo = userRepo;
        this.businessRepo = businessRepo;
        this.businessUserRepo = businessUserRepo;
        this.shopRepo = shopRepo;
        this.shopUsersRepo = shopUsersRepo;
    }

    public static boolean isManager(BusinessUser member) {
        return member != null && MANAGERS.contains(member.getRoles());
    }

    /** The platform operator. Super admins manage companies; they are not members of them. */
    public User requireSuperAdmin(String email) {
        User user = requireUser(email);
        if (!Boolean.TRUE.equals(user.getSuperAdmin())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Super admin only");
        }
        return user;
    }

    public User requireUser(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) throw new AppException(HttpStatus.UNAUTHORIZED, "User not found");
        return user;
    }

    public Business requireBusiness(Long businessId) {
        return businessRepo.findById(businessId)
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    public BusinessUser requireMembership(Long businessId, String email) {
        User user = requireUser(email);
        Business business = requireBusiness(businessId);
        BusinessUser bu = businessUserRepo.findByBusinessAndUser(business, user);
        if (bu == null || !bu.isActiveMember()) {
            throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this business");
        }
        if (Boolean.TRUE.equals(business.getSuspended())) {
            throw new AppException(HttpStatus.FORBIDDEN, business.getName() + " is suspended. Contact the system administrator.");
        }
        return bu;
    }

    public BusinessUser requireRole(Long businessId, String email, UserRoles... roles) {
        BusinessUser bu = requireMembership(businessId, email);
        boolean allowed = Arrays.asList(roles).contains(bu.getRoles());
        if (!allowed) throw new AppException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        return bu;
    }

    public BusinessUser requireManager(Long businessId, String email) {
        return requireRole(businessId, email, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
    }

    public Shop requireShopInBusiness(Long shopId, Long businessId) {
        if (shopId == null) throw new AppException(HttpStatus.BAD_REQUEST, "Location is required");
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));
        if (!shop.getBusiness().getId().equals(businessId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Shop does not belong to this business");
        }
        return shop;
    }

    /**
     * The location, checked to belong to the business and to be one this user works at.
     * Owners, admins and managers can use every location; other staff only the locations
     * they are assigned to.
     */
    public Shop requireShopAccess(Long shopId, Long businessId, String email) {
        return requireShopAccess(shopId, businessId, requireMembership(businessId, email));
    }

    public Shop requireShopAccess(Long shopId, Long businessId, BusinessUser member) {
        Shop shop = requireShopInBusiness(shopId, businessId);
        if (!canUseShop(member, shop)) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "You are not assigned to " + shop.getShopName() + ". Ask a manager to give you access.");
        }
        return shop;
    }

    public boolean canUseShop(BusinessUser member, Shop shop) {
        return isManager(member) || assignedShopIds(member).contains(shop.getId());
    }

    /** Ids of the locations a non-manager is assigned to; null means every location (managers). */
    public Set<Long> allowedShopIds(BusinessUser member) {
        return isManager(member) ? null : assignedShopIds(member);
    }

    private Set<Long> assignedShopIds(BusinessUser member) {
        Set<Long> ids = new HashSet<>();
        for (ShopUsers su : shopUsersRepo.findActiveByBusinessUser(member)) {
            if (su.getShop() != null) ids.add(su.getShop().getId());
        }
        return ids;
    }
}
