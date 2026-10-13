package com.ultimate.inventory.Controller;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.*;
import com.ultimate.inventory.Model.*;
import com.ultimate.inventory.Repository.*;
import com.ultimate.inventory.utils.FileStorage;
import com.ultimate.inventory.utils.Security;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.Date;

@RestController
@RequestMapping("/auth")
public class Auth {

    @Autowired
    private Security security;
    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private UserRepository userRepo;
    @Autowired
    private RefreshTokenRepo refreshTokenRepo;
    @Autowired
    private BusinessRepository businessRepo;
    @Autowired
    private ShopRepository shopRepo;
    @Autowired
    private ShopUsersRepository shopUserRepo;
    @Autowired
    private BusinessUserRepo businessUserRepo;


    @GetMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody @Validated loginDTO requestBody, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
        String email = requestBody.getEmail();
        String password = requestBody.getPassword();

        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ipAddress = security.getClientAddress(httpRequest);
        String token = security.generateRefreshToken();

        User userDetails = userRepo.findByEmail(email);
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid credentials");
        }
        String accessToken = security.generateAccessToken(userDetails.getId(), userDetails.getEmail());

        if (!security.isPassword(password, userDetails.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Check credentials");
        }


        //create the cookie
        Cookie cookie = new Cookie("uploadFiles", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 60 * 60);
        httpResponse.addCookie(cookie);


        //save refresh token
        RefreshTokens refreshToken = new RefreshTokens();
        refreshToken.setUser(userDetails);
        refreshToken.setToken(token);
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setExpiryDate(new Date(System.currentTimeMillis() + (1000 * 60 * 60)));
        refreshTokenRepo.save(refreshToken);

        UserResponseDTO userResponseDTO = new UserResponseDTO(userDetails);
        loginResponseDTO.setAccessToken(accessToken);
        loginResponseDTO.setUser(userResponseDTO);


        return ResponseEntity.status(HttpStatus.OK).body(loginResponseDTO);
    }

    @Transactional
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    private ResponseEntity<?> register(@RequestPart("data") @Validated RegisterDTO requestBody,
                                       @RequestPart(value = "businessLogo", required = false) MultipartFile businessLogo) throws IOException {
        String newPassword = security.hashPassword(requestBody.getPassword());
        String businessCode = "ULT";

        User userDetails = userRepo.findByEmail(requestBody.getEmail());
        if (userDetails != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("User Already exist");
        }

        //User creation
        User user = new User();
        user.setPassword(newPassword);
        user.setEmail(requestBody.getEmail());
        user.setFirstName(requestBody.getFirstName());
        user.setOtherName(requestBody.getOtherName());
        user.setLastName(requestBody.getLastName());
        user = userRepo.save(user);


        Business companyDetails = businessRepo.findByBusinessEmail(requestBody.getBusinessEmail());
        if (companyDetails != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Business Already exist");
        }
        //Business creation
        Business company = new Business();
        company.setName(requestBody.getBusinessName());
        company.setBusinessEmail(requestBody.getBusinessEmail());
        company.setBusinessContact(requestBody.getBusinessContact());
        company.setBusinessSecondaryContact(requestBody.getBusinessSecondaryContact());
        //save the file
        ResponseDTO savedFile = fileStorage.saveFile(businessLogo, requestBody.getBusinessEmail());
        String fileName = savedFile.getStatus() ? savedFile.getMessage() : null;
        company.setBusinessLogo(fileName);
        company = businessRepo.save(company);
        businessCode = businessCode + company.getId();
        company.setCode(businessCode);
        company = businessRepo.save(company);

        //business user creation
        BusinessUser businessUser = new BusinessUser();
        businessUser.setBusiness(company);
        businessUser.setUser(user);
        businessUser.setRoles(UserRoles.OWNER);
        businessUserRepo.save(businessUser);
        return ResponseEntity.status(HttpStatus.CREATED).body("User successfully registered. Login to continue");
    }

    @GetMapping("/refresh")
    private ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            Cookie cookie = WebUtils.getCookie(request, "_ultimate_");
            if (cookie != null) {
                RefreshTokens token = refreshTokenRepo.findAllByToken(cookie.getValue());

                if (token != null && token.getExpiryDate().after(new Date())) {
                    //generate an access token
                    String accessToken = security.generateAccessToken(token.getUser().getId(), token.getUser().getEmail());

                    LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
                    UserResponseDTO userResponseDTO = new UserResponseDTO(token.getUser());
                    loginResponseDTO.setAccessToken(accessToken);
                    loginResponseDTO.setUser(userResponseDTO);
                    response.setHeader("Authorization", "Bearer " + accessToken);
                    return ResponseEntity.status(HttpStatus.OK).body(loginResponseDTO);
                }
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Session ended. Login again");
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Login again");
        } catch (Exception e) {
            System.out.println("Refresh Token method " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,HttpServletResponse response) {
        try {
            Cookie cookie = WebUtils.getCookie(request, "_ultimate_");
            if (cookie != null) {
                RefreshTokens refreshToken =refreshTokenRepo.findAllByToken(cookie.getValue());
                if (refreshToken != null) {
                    refreshTokenRepo.delete(refreshToken);
                }
            }

            // Clear the refresh-token cookie
            Cookie clearCookie = new Cookie("_ultimate_", null);
            clearCookie.setHttpOnly(true);
            clearCookie.setSecure(true);
            clearCookie.setPath("/");
            clearCookie.setMaxAge(0);
            response.addCookie(clearCookie);

            return ResponseEntity.ok("Logged out successfully");
        } catch (Exception e) {
            System.out.println("Logout error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unable to log out");
        }
    }

}
