package com.ultimate.inventory.Service;

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
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    @Autowired private BusinessRepository businessRepo;
    @Autowired private ShopRepository shopRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private BusinessUserRepo businessUserRepo;
    @Autowired private ShopUsersRepository shopUsersRepo;
    @Autowired private ExcelService excelService;

    @Transactional
    public Shop createShop(ShopDTO dto, String userEmail) {
        Business business = businessRepo.findById(dto.getBusinessId().longValue())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));

        Shop shop = new Shop();
        shop.setShopName(dto.getShopName());
        shop.setShopLocation(dto.getShopLocation());
        shop.setBusiness(business);
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
        Business business = businessRepo.findById(businessId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));

        User user = userRepo.findByEmail(userEmail);
        BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(business, user);
        if (businessUser == null) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not belong to this business");
        }

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
            shop.setBusiness(business);
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

    public Shop getShop(Long shopId) {
        return shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));
    }

    public Page<Shop> getShopsByBusiness(Long businessId, String userEmail, Pageable pageable) {
        Business business = businessRepo.findById(businessId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Business not found"));

        User user = userRepo.findByEmail(userEmail);
        if (businessUserRepo.findByBusinessAndUser(business, user) == null) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not belong to this business");
        }

        return shopRepo.findByBusiness(business, pageable);
    }

    @Transactional
    public Shop updateShop(Long shopId, ShopDTO dto) {
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));

        shop.setShopName(dto.getShopName());
        shop.setShopLocation(dto.getShopLocation());
        if (dto.getActive() != null) shop.setActive(dto.getActive());
        return shopRepo.save(shop);
    }

    @Transactional
    public void deleteShop(Long shopId) {
        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Shop not found"));
        shop.setDeleted(true);
        shopRepo.save(shop);
        log.info("Shop soft-deleted: {}", shopId);
    }
}
