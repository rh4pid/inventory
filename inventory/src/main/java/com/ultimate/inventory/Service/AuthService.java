package com.ultimate.inventory.Service;

import com.ultimate.inventory.Constants.UserRoles;
import com.ultimate.inventory.DTO.AuthResult;
import com.ultimate.inventory.DTO.LoginResponseDTO;
import com.ultimate.inventory.DTO.RegisterDTO;
import com.ultimate.inventory.DTO.UserResponseDTO;
import com.ultimate.inventory.DTO.loginDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.Business;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.RefreshTokens;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessRepository;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.RefreshTokenRepo;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.FileStorage;
import com.ultimate.inventory.utils.Security;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired private UserRepository userRepo;
    @Autowired private RefreshTokenRepo refreshTokenRepo;
    @Autowired private BusinessRepository businessRepo;
    @Autowired private BusinessUserRepo businessUserRepo;
    @Autowired private Security security;
    @Autowired private FileStorage fileStorage;

    @Transactional
    public AuthResult login(loginDTO dto, String deviceInfo, String ipAddress) {
        User user = userRepo.findByEmail(dto.getEmail());
        if (user == null || !security.isPassword(dto.getPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String rawToken = security.generateRefreshToken();
        RefreshTokens refreshToken = new RefreshTokens();
        refreshToken.setUser(user);
        refreshToken.setToken(rawToken);
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setExpiryDate(new Date(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)));
        refreshTokenRepo.save(refreshToken);

        String accessToken = security.generateAccessToken(user.getId(), user.getEmail());
        log.info("User logged in: {}", user.getEmail());
        return new AuthResult(accessToken, rawToken, new UserResponseDTO(user));
    }

    @Transactional
    public void register(RegisterDTO dto, MultipartFile businessLogo) throws IOException {
        if (userRepo.findByEmail(dto.getEmail()) != null) {
            throw new AppException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (businessRepo.findByBusinessEmail(dto.getBusinessEmail()) != null) {
            throw new AppException(HttpStatus.CONFLICT, "Business email already registered");
        }

        User user = new User();
        user.setPassword(security.hashPassword(dto.getPassword()));
        user.setEmail(dto.getEmail());
        user.setFirstName(dto.getFirstName());
        user.setOtherName(dto.getOtherName());
        user.setLastName(dto.getLastName());
        user = userRepo.save(user);

        Business business = new Business();
        business.setName(dto.getBusinessName());
        business.setBusinessEmail(dto.getBusinessEmail());
        business.setBusinessContact(dto.getBusinessContact());
        business.setBusinessSecondaryContact(dto.getBusinessSecondaryContact());

        if (businessLogo != null && !businessLogo.isEmpty()) {
            var saved = fileStorage.saveFile(businessLogo, dto.getBusinessEmail());
            if (Boolean.TRUE.equals(saved.getStatus())) {
                business.setBusinessLogo(saved.getMessage());
            }
        }

        // Satisfy NOT NULL constraint with a unique placeholder; replace after ID is assigned
        business.setCode("INIT-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        business = businessRepo.save(business);
        business.setCode("BIZ" + String.format("%05d", business.getId()));
        businessRepo.save(business);

        BusinessUser businessUser = new BusinessUser();
        businessUser.setBusiness(business);
        businessUser.setUser(user);
        businessUser.setRoles(UserRoles.OWNER);
        businessUserRepo.save(businessUser);

        log.info("Business registered: {} by user: {}", business.getCode(), user.getEmail());
    }

    public AuthResult refreshToken(String cookieValue) {
        if (cookieValue == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "No session found. Please login.");
        }
        RefreshTokens token = refreshTokenRepo.findAllByToken(cookieValue);
        if (token == null || !token.getExpiryDate().after(new Date())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Session expired. Please login again.");
        }
        String accessToken = security.generateAccessToken(token.getUser().getId(), token.getUser().getEmail());
        return new AuthResult(accessToken, null, new UserResponseDTO(token.getUser()));
    }

    @Transactional
    public void logout(String cookieValue) {
        if (cookieValue == null) return;
        RefreshTokens token = refreshTokenRepo.findAllByToken(cookieValue);
        if (token != null) {
            refreshTokenRepo.delete(token);
        }
    }
}
