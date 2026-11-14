package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.LocationType;
import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.ExcelUpload.UploadShop;
import com.ultimate.inventory.DTO.ShopDTO;
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
import java.util.UUID;

// Reads share one read-only transaction (one connection, one commit, cached lookups); writes declare their own.
@Transactional(readOnly = true)
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    @Autowired private BusinessRepository businessRepo;
    @Autowired private ShopRepository shopRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private BusinessUserRepo businessUserRepo;
    @Autowired private ShopUsersRepository shopUsersRepo;
    @Autowired private ExcelService excelService;
    @Autowired private BusinessAccessHelper accessHelper;

    @Transactional
    public Shop createShop(ShopDTO dto, String userEmail) {
        Long businessId = dto.getBusinessId().longValue();
        accessHelper.requireRole(businessId, userEmail, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
        Business business = accessHelper.requireBusiness(businessId);

        Shop shop = new Shop();
        shop.setShopName(dto.getShopName());
        shop.setShopLocation(dto.getShopLocation());
        shop.setLocationType(dto.getLocationType() != null ? dto.getLocationType() : LocationType.SHOP);
        shop.setActive(true);
        shop.setDeleted(false);
        shop.setBusiness(business);
        shop.setCreatedBy(userEmail);
        shop.setCreatedAt(new Date());
        // shop_code is NOT NULL; use a temporary unique code until the id is known.
        shop.setShopCode("TMP-" + UUID.randomUUID());
        shop = shopRepo.save(shop);
        shop.setShopCode("SHP" + shop.getId() + business.getCode());
        shop = shopRepo.save(shop);

        User user = userRepo.findByEmail(userEmail);
        BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(business, user);
        if (businessUser != null) {
            ShopUsers shopUsers = new ShopUsers();
            shopUsers.setShop(shop);
            shopUsers.setBusinessUser(businessUser);
            shopUsersRepo.save(shopUsers);
        }

        log.info("Shop created: {} for business: {}", shop.getShopCode(), business.getCode());
        return shop;
    }

    @Transactional
    public void createShopsBulk(Long businessId, MultipartFile file, String userEmail) {
        // Same rule as creating one location (it used to accept any member, cashiers included).
        BusinessUser businessUser = accessHelper.requireRole(businessId, userEmail, UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
        Business business = accessHelper.requireBusiness(businessId);

        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Excel file is required");
        }

        List<UploadShop> rows = excelService.readExcel(file, UploadShop.class);
        if (rows == null || rows.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No valid data found in the uploaded file");
        }

        for (UploadShop row : rows) {
            Shop shop = new Shop();
            shop.setShopName(row.getShopName());
            shop.setShopLocation(row.getShopLocation());
            shop.setLocationType(LocationType.SHOP);
            shop.setActive(true);
            shop.setDeleted(false);
            shop.setBusiness(business);
            shop.setShopCode("TMP-" + UUID.randomUUID());
            shop = shopRepo.save(shop);
            shop.setShopCode("SHP" + shop.getId() + business.getCode());
            shop = shopRepo.save(shop);

            ShopUsers shopUsers = new ShopUsers();
            shopUsers.setShop(shop);
            shopUsers.setBusinessUser(businessUser);
            shopUsersRepo.save(shopUsers);
        }

        log.info("Bulk created {} shops for business: {}", rows.size(), business.getCode());
    }

    public Shop getShop(Long shopId, String userEmail) {
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));
        accessHelper.requireMembership(shop.getBusiness().getId(), userEmail);
        return shop;
    }

    /**
     * Locations of the business. With {@code mine}, only those the caller can work at: every
     * location for managers, the assigned ones for other staff.
     */
    public Page<Shop> getShopsByBusiness(Long businessId, String userEmail, Pageable pageable, boolean mine) {
        BusinessUser member = accessHelper.requireMembership(businessId, userEmail);
        Business business = accessHelper.requireBusiness(businessId);
        java.util.Set<Long> allowed = mine ? accessHelper.allowedShopIds(member) : null;
        if (allowed != null) {
            if (allowed.isEmpty()) return Page.empty(pageable);
            return shopRepo.findActiveByBusinessAndIdIn(business, allowed, pageable);
        }
        return shopRepo.findActiveByBusiness(business, pageable);
    }

    @Transactional
    public Shop updateShop(Long shopId, ShopDTO dto, String userEmail) {
        Shop shop = requireManagedShop(shopId, userEmail);
        shop.setShopName(dto.getShopName());
        shop.setShopLocation(dto.getShopLocation());
        if (dto.getLocationType() != null) shop.setLocationType(dto.getLocationType());
        if (dto.getActive() != null) shop.setActive(dto.getActive());
        shop.setUpdatedBy(userEmail);
        shop.setUpdatedAt(new Date());
        return shopRepo.save(shop);
    }

    @Transactional
    public void deleteShop(Long shopId, String userEmail) {
        Shop shop = requireManagedShop(shopId, userEmail);
        shop.setDeleted(true);
        shop.setUpdatedBy(userEmail);
        shop.setUpdatedAt(new Date());
        shopRepo.save(shop);
        log.info("Shop soft-deleted: {} by {}", shopId, userEmail);
    }

    /** Loads a shop and checks the caller may manage locations in its business. */
    private Shop requireManagedShop(Long shopId, String userEmail) {
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));
        accessHelper.requireRole(shop.getBusiness().getId(), userEmail,
                UserRoles.OWNER, UserRoles.ADMIN, UserRoles.MANAGER);
        return shop;
    }
}
