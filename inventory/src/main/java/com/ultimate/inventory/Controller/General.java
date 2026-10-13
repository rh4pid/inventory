package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.ExcelUpload.UploadShop;
import com.ultimate.inventory.DTO.ExcelUpload.UploadShoppers;
import com.ultimate.inventory.DTO.ResponseDTO;
import com.ultimate.inventory.DTO.ShopDTO;
import com.ultimate.inventory.DTO.ShopKeepDTO;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import com.ultimate.inventory.utils.ExcelService;
import jakarta.websocket.server.PathParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Pageable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/general")
public class General {

    @Autowired
    private BusinessRepository businessRepo;

    @Autowired
    private RepositoryManager repoManager;

    @Autowired
    private ShopRepository shopRepo;
    @Autowired
    private ShopUsersRepository shopUserRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private ExcelService excelService;
    @Autowired
    private BusinessUserRepo businessUserRepo;

    //===================SHOP CRUD FUNCTIONALITIES ================================================
    @PostMapping("/shop")
    public ResponseEntity<?> createShop(@RequestBody @Validated ShopDTO requestBody, @AuthenticationPrincipal String email) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Integer businessId = requestBody.getBusinessId();
            Optional<Business> business = businessRepo.findById(businessId.longValue());

            if (business.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Business does not exist");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }

            String shopCode = business.get().getCode();
            //Default Shop creation
            Shop shop = new Shop();
            shop.setShopName(requestBody.getShopName());
            shop.setShopLocation(requestBody.getShopLocation());
            shop.setBusiness(business.get());
            shop = shopRepo.save(shop);
            shopCode = "SHP" + shop.getId() + shopCode;
            shop.setShopCode(shopCode);
            shopRepo.save(shop);


            User user = userRepo.findByEmail(email);
            BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(business.get(), user);

            //create shop users
            ShopUsers shopUsers = new ShopUsers();
            shopUsers.setShop(shop);
            shopUsers.setBusinessUser(businessUser);
            repoManager.save(shopUsers);

            responseDTO.setStatus(true);
            responseDTO.setMessage("Shop created Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
        } catch (Exception e) {
            System.out.println("CREATING SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //bulk shop creates
    @PostMapping("/shop/bulk/{businessId}")
    public ResponseEntity<?> createShopBulk(@PathVariable Integer businessId, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal String email) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Optional<Business> business = businessRepo.findById(businessId.longValue());
            if (business.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Business does not exist");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }
            User user = userRepo.findByEmail(email);
            BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(business.get(), user);

            if (businessUser == null) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Business does not exist");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }


            if (file == null || file.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Excel file is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(responseDTO);
            }

            List<UploadShop> shopDetails = excelService.readExcel(file, UploadShop.class);
            String shopCode = business.get().getCode();


            for (UploadShop s : shopDetails) {
                Shop shop = new Shop();
                shop.setShopName(s.getShopName());
                shop.setShopLocation(s.getShopLocation());
                shop.setBusiness(business.get());
                shop = shopRepo.save(shop);

                shopCode = "SHP" + shop.getId() + shopCode;
                shop.setShopCode(shopCode);
                shop = shopRepo.save(shop);

                //create shop users
                ShopUsers shopUsers = new ShopUsers();
                shopUsers.setShop(shop);
                shopUsers.setBusinessUser(businessUser);
                repoManager.save(shopUsers);
            }

            responseDTO.setStatus(true);
            responseDTO.setMessage("Shop created Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
        } catch (Exception e) {
            System.out.println("CREATING SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //FETCH shop
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<?> fetchOneShop(@PathVariable String shopId, @AuthenticationPrincipal String email) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Long shopID = Long.parseLong(shopId);
            Optional<Shop> shops = shopRepo.findById(shopID);

            if (shops.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Cannot find shop");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }

            return ResponseEntity.status(HttpStatus.OK).body(shops.get());
        } catch (Exception er) {
            System.out.println("CREATING SHOP ERROR :" + er.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //fetch all shops
    @GetMapping("/shop/{businessId}")
    public ResponseEntity<?> fetchShops(@PathVariable String businessId,
                                        @AuthenticationPrincipal String email,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        ResponseDTO responseDTO = new ResponseDTO();
        List<User> response = new ArrayList<>();
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

            User user = userRepo.findByEmail(email);
            Optional<Business> businesses = businessRepo.findById(Long.parseLong(businessId));

            if (user == null || businesses.isEmpty()) {
                System.out.println("BUSINESS ID OR USER AUTHENTICATION DOES NOT EXIST");
                responseDTO.setStatus(false);
                responseDTO.setMessage("BUSINESS ID OR USER AUTHENTICATION DOES NOT EXIST");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }
            BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(businesses.get(), user);

            if (businessUser == null) {
                System.out.println("This user does not exist in this business");
                responseDTO.setStatus(false);
                responseDTO.setMessage("This user does not exist in this business");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }

            Page<Shop> shops = shopRepo.findByBusiness(businesses.get(), pageable);
            return ResponseEntity.status(HttpStatus.OK).body(shops);
        } catch (Exception er) {
            System.out.println("CREATING SHOP ERROR :" + er.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //update shop
    @PutMapping("/shop/{shopId}")
    public ResponseEntity<?> updateShop(@PathVariable String shopId, @RequestBody @Validated ShopDTO requestBody) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Optional<Shop> shops = shopRepo.findById(Long.parseLong(shopId));
            if (shops.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Failed to delete shop");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
            }

            //Default Shop creation
            Shop shop = shops.get();
            shop.setShopName(requestBody.getShopName());
            shop.setShopLocation(requestBody.getShopLocation());
            shop.setActive(requestBody.getActive());
            shopRepo.save(shop);

            responseDTO.setStatus(true);
            responseDTO.setMessage("Shop updated Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
        } catch (Exception e) {
            System.out.println("Updating SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to update shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //delete shop
    @DeleteMapping("/shop/{shopId}")
    public ResponseEntity<?> deleteShop(@PathVariable @Validated String shopId) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Optional<Shop> shops = shopRepo.findById(Long.parseLong(shopId));
            if (shops.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Failed to delete shop");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
            }

            Shop shop = shops.get();
            shop.setDeleted(true);
            shopRepo.save(shop);

            responseDTO.setStatus(true);
            responseDTO.setMessage("Shop deleted Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);

        } catch (Exception e) {
            System.out.println("Deleting SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to delete shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }


    //===================ShopKeep CRUD FUNCTIONALITIES ================================================
    @PostMapping("/shopkeep/{businessId}")
    public ResponseEntity<?> createShopKeeper(@RequestBody @Validated ShopKeepDTO requestBody, @PathVariable String businessId, @AuthenticationPrincipal String email) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            String shopCode = requestBody.getShopCode();
            String userEmail = requestBody.getEmail();

            Optional<Business> businesses = businessRepo.findById(Long.parseLong(businessId));
            Optional<Shop> shop = shopRepo.findByShopCode(shopCode);
            User user = userRepo.findByEmail(userEmail);

            if (shop.isEmpty() || user == null || businesses.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Invalid shop or user");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }


            //create BusinessUser
            BusinessUser businessUser = new BusinessUser();
            businessUser.setBusiness(businesses.get());
            businessUser.setUser(user);
            businessUser.setRoles(UserRoles.valueOf(requestBody.getRoles()));
            businessUser = businessUserRepo.save(businessUser);

            ShopUsers shopUsers = new ShopUsers();
            shopUsers.setShop(shop.get());
            shopUsers.setBusinessUser(businessUser);
            shopUsers.setRoles(UserRoles.valueOf(requestBody.getRoles()));
            shopUserRepo.save(shopUsers);

            responseDTO.setStatus(true);
            responseDTO.setMessage("Shopkeep created Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
        } catch (Exception e) {
            System.out.println("CREATING SHOPkeep ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shopkeep");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //bulk shopkeep create
    @PostMapping("/shopkeep/bulk/{businessId}")
    public ResponseEntity<?> createShopkeepBulk(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal String email, @PathVariable Integer businessId) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Optional<Business> business = businessRepo.findById(businessId.longValue());
            if (business.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Business does not exist");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }
            User userUploader = userRepo.findByEmail(email);
            BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(business.get(), userUploader);

            if (businessUser == null) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Business does not exist");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }


            if (file == null || file.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Excel file is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(responseDTO);
            }

            List<UploadShoppers> shopDetails = excelService.readExcel(file, UploadShoppers.class);

            for (UploadShoppers shoppers : shopDetails) {
                String shopCode = shoppers.getShopCode();
                String userEmail = shoppers.getUserEmail();
                UserRoles role = UserRoles.valueOf(shoppers.getRoles());

                Optional<Shop> shop = shopRepo.findByShopCode(shopCode);
                User user = userRepo.findByEmail(userEmail);

                if (shop.isEmpty() || user == null) {
                    responseDTO.setStatus(false);
                    responseDTO.setMessage("Invalid shop or user");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
                }


                //create BusinessUser
                businessUser = new BusinessUser();
                businessUser.setBusiness(business.get());
                businessUser.setUser(user);
                businessUser.setRoles(role);
                businessUser = businessUserRepo.save(businessUser);

                ShopUsers shopUsers = new ShopUsers();
                shopUsers.setShop(shop.get());
                shopUsers.setBusinessUser(businessUser);
                shopUsers.setRoles(role);
                shopUserRepo.save(shopUsers);
            }


            responseDTO.setStatus(true);
            responseDTO.setMessage("Shop created Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
        } catch (Exception e) {
            System.out.println("CREATING SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }


    //FETCH shopKeep
    @GetMapping("/shopkeep/{businessUserId}")
    public ResponseEntity<?> fetchOneShopKeep(@PathVariable Integer businessUserId, @AuthenticationPrincipal String email) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Long businessUserID = businessUserId.longValue();
            Optional<BusinessUser> businessUsers = businessUserRepo.findById(businessUserID);

            if (businessUsers.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Cannot find shopkeep");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }

            return ResponseEntity.status(HttpStatus.OK).body(businessUsers.get());
        } catch (Exception er) {
            System.out.println("CREATING SHOP ERROR :" + er.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //update shopkeeper .eg Role or re-assigning to another shop
    @PutMapping("/shopkeep/{shopUserId}")
    public ResponseEntity<?> updateShop(@PathVariable Integer shopUserId, @RequestBody @Validated ShopKeepDTO requestBody) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Optional<ShopUsers> shopUsers = shopUserRepo.findById(shopUserId.longValue());

            if (shopUsers.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("ShopUser does not exist");
                return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
            }

            ShopUsers shopUser = shopUsers.get();

            String shopCode = requestBody.getShopCode();
            //String userEmail = requestBody.getEmail();
            UserRoles role = UserRoles.valueOf(requestBody.getRoles());

            Optional<Shop> shops = shopRepo.findByShopCode(shopCode);
            if (shops.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("ShopUser does not exist");
                return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
            }

            shopUser.setShop(shops.get());
            shopUser.setRoles(role);
            shopUserRepo.save(shopUser);

            responseDTO.setStatus(true);
            responseDTO.setMessage("Shop updated Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);
        } catch (Exception e) {
            System.out.println("Updating SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to update shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }


    //fetch all shopkeeps
    @GetMapping("/shopkeep/{businessId}")
    public ResponseEntity<?> fetchShopKeep(@PathVariable String businessId,
                                           @AuthenticationPrincipal String email,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        ResponseDTO responseDTO = new ResponseDTO();
        List<User> response = new ArrayList<>();
        try {
            Pageable pageable =  PageRequest.of(page, size, Sort.by("id").ascending());

            User user = userRepo.findByEmail(email);
            Optional<Business> businesses = businessRepo.findById(Long.parseLong(businessId));

            if (user == null || businesses.isEmpty()) {
                System.out.println("BUSINESS ID OR USER AUTHENTICATION DOES NOT EXIST");
                responseDTO.setStatus(false);
                responseDTO.setMessage("BUSINESS ID OR USER AUTHENTICATION DOES NOT EXIST");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }
            BusinessUser businessUser = businessUserRepo.findByBusinessAndUser(businesses.get(), user);

            if (businessUser == null) {
                System.out.println("This user does not exist in this business");
                responseDTO.setStatus(false);
                responseDTO.setMessage("This user does not exist in this business");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseDTO);
            }

            Page<BusinessUser> shops = businessUserRepo.findByBusiness(businesses.get(), pageable);
            return ResponseEntity.status(HttpStatus.OK).body(shops);
        } catch (Exception er) {
            System.out.println("CREATING SHOP ERROR :" + er.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to create shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }


    //delete shopkeeper
    @DeleteMapping("/shopkeep/{shopUserId}")
    public ResponseEntity<?> deleteShopKeep(@PathVariable Integer shopUserId) {
        ResponseDTO responseDTO = new ResponseDTO();
        try {
            Optional<ShopUsers> shopUsers = shopUserRepo.findById(shopUserId.longValue());
            if (shopUsers.isEmpty()) {
                responseDTO.setStatus(false);
                responseDTO.setMessage("Failed to remove shopkeep");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
            }

            shopUserRepo.delete(shopUsers.get());
            responseDTO.setStatus(true);
            responseDTO.setMessage("Shopkeep removed Successfully");
            return ResponseEntity.status(HttpStatus.OK).body(responseDTO);

        } catch (Exception e) {
            System.out.println("Deleting SHOP ERROR :" + e.getMessage());
            responseDTO.setStatus(false);
            responseDTO.setMessage("Failed to delete shop");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseDTO);
        }
    }

    //add product to shop
    //bulk product add to shop

    //update product in shop
    //bulk product update in shop

    //delete product in shop
    //bulk delete product in shop


}
