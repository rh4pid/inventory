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
import com.ultimate.inventory.Repository.BusinessRepository;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.ShopRepository;
import com.ultimate.inventory.Repository.ShopUsersRepository;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.ExcelService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    @Autowired private BusinessRepository businessRepo;
    @Autowired private ShopRepository shopRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private BusinessUserRepo businessUserRepo;
    @Autowired private ShopUsersRepository shopUsersRepo;
    @Autowired private ExcelService excelService;

    @Transactional
    public void addStaff(ShopKeepDTO dto, Long businessId) {
        Business business = businessRepo.findById(businessId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));

        Shop shop = shopRepo.findByShopCode(dto.getShopCode())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));

        User user = userRepo.findByEmail(dto.getEmail());
        if (user == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "User not found with email: " + dto.getEmail());
        }

        UserRoles role = parseRole(dto.getRoles());
        BusinessUser businessUser = upsertBusinessUser(business, user, role);

        ShopUsers shopUsers = new ShopUsers();
        shopUsers.setShop(shop);
        shopUsers.setBusinessUser(businessUser);
        shopUsers.setRoles(role);
        shopUsersRepo.save(shopUsers);

        log.info("Staff added: {} to shop: {} in business: {}", user.getEmail(), shop.getShopCode(), business.getCode());
    }

    @Transactional
    public void addStaffBulk(MultipartFile file, Long businessId, String uploaderEmail) {
        Business business = businessRepo.findById(businessId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));

        User uploader = userRepo.findByEmail(uploaderEmail);
        if (businessUserRepo.findByBusinessAndUser(business, uploader) == null) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not belong to this business");
        }

        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Excel file is required");
        }

        List<UploadShoppers> rows = excelService.readExcel(file, UploadShoppers.class);
        if (rows == null || rows.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "No valid data found in the uploaded file");
        }

        for (UploadShoppers row : rows) {
            Shop shop = shopRepo.findByShopCode(row.getShopCode())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found: " + row.getShopCode()));

            User user = userRepo.findByEmail(row.getUserEmail());
            if (user == null) {
                throw new AppException(HttpStatus.NOT_FOUND, "User not found: " + row.getUserEmail());
            }

            UserRoles role = parseRole(row.getRoles());
            BusinessUser businessUser = upsertBusinessUser(business, user, role);

            ShopUsers shopUsers = new ShopUsers();
            shopUsers.setShop(shop);
            shopUsers.setBusinessUser(businessUser);
            shopUsers.setRoles(role);
            shopUsersRepo.save(shopUsers);
        }

        log.info("Bulk added {} staff members to business: {}", rows.size(), business.getCode());
    }

    public BusinessUser getStaff(Long businessUserId) {
        return businessUserRepo.findById(businessUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff member not found"));
    }

    public Page<BusinessUser> getStaffByBusiness(Long businessId, String userEmail, Pageable pageable) {
        Business business = businessRepo.findById(businessId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));

        User user = userRepo.findByEmail(userEmail);
        if (businessUserRepo.findByBusinessAndUser(business, user) == null) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not belong to this business");
        }

        return businessUserRepo.findByBusiness(business, pageable);
    }

    @Transactional
    public void updateStaff(Long shopUserId, ShopKeepDTO dto) {
        ShopUsers shopUser = shopUsersRepo.findById(shopUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff assignment not found"));

        Shop shop = shopRepo.findByShopCode(dto.getShopCode())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));

        shopUser.setShop(shop);
        shopUser.setRoles(parseRole(dto.getRoles()));
        shopUsersRepo.save(shopUser);
    }

    @Transactional
    public void removeStaff(Long shopUserId) {
        ShopUsers shopUser = shopUsersRepo.findById(shopUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Staff assignment not found"));
        shopUsersRepo.delete(shopUser);
        log.info("Staff assignment removed: {}", shopUserId);
    }

    private BusinessUser upsertBusinessUser(Business business, User user, UserRoles role) {
        BusinessUser bu = businessUserRepo.findByBusinessAndUser(business, user);
        if (bu == null) {
            bu = new BusinessUser();
            bu.setBusiness(business);
            bu.setUser(user);
        }
        bu.setRoles(role);
        return businessUserRepo.save(bu);
    }

    private UserRoles parseRole(String role) {
        try {
            return UserRoles.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid role: " + role);
        }
    }
}
