package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.Shop;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessRepository;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class BusinessAccessHelper {

    private final UserRepository userRepo;
    private final BusinessRepository businessRepo;
    private final BusinessUserRepo businessUserRepo;
    private final ShopRepository shopRepo;

    public BusinessAccessHelper(UserRepository userRepo, BusinessRepository businessRepo,
                                BusinessUserRepo businessUserRepo, ShopRepository shopRepo) {
        this.userRepo = userRepo;
        this.businessRepo = businessRepo;
        this.businessUserRepo = businessUserRepo;
        this.shopRepo = shopRepo;
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
        if (bu == null) throw new AppException(HttpStatus.FORBIDDEN, "You are not a member of this business");
        return bu;
    }

    public BusinessUser requireRole(Long businessId, String email, UserRoles... roles) {
        BusinessUser bu = requireMembership(businessId, email);
        boolean allowed = Arrays.asList(roles).contains(bu.getRoles());
        if (!allowed) throw new AppException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        return bu;
    }

    public Shop requireShopInBusiness(Long shopId, Long businessId) {
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));
        if (!shop.getBusiness().getId().equals(businessId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Shop does not belong to this business");
        }
        return shop;
    }
}
