package com.ultimate.inventory.Service;

import com.ultimate.inventory.DTO.BusinessMembershipDTO;
import com.ultimate.inventory.DTO.UpdateProfileDTO;
import com.ultimate.inventory.DTO.UserResponseDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepo;
    private final BusinessUserRepo businessUserRepo;

    public UserService(UserRepository userRepo, BusinessUserRepo businessUserRepo) {
        this.userRepo = userRepo;
        this.businessUserRepo = businessUserRepo;
    }

    public UserResponseDTO getMyProfile(String email) {
        User user = requireUser(email);
        return new UserResponseDTO(user);
    }

    @Transactional
    public UserResponseDTO updateProfile(String email, UpdateProfileDTO dto) {
        User user = requireUser(email);

        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            if (!dto.getUsername().equalsIgnoreCase(user.getUsername())) {
                User existing = userRepo.findByUsername(dto.getUsername());
                if (existing != null) {
                    throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
                }
            }
            user.setUsername(dto.getUsername());
        }

        user.setFirstName(dto.getFirstName());
        user.setOtherName(dto.getOtherName());
        user.setLastName(dto.getLastName());
        user.setTelephonePrimary(dto.getTelephonePrimary());
        user.setTelephoneSecondary(dto.getTelephoneSecondary());
        user.setUpdatedAt(new Date());
        user.setUpdatedBy(email);

        User saved = userRepo.save(user);
        log.info("Profile updated for {}", email);
        return new UserResponseDTO(saved);
    }

    public List<BusinessMembershipDTO> getMyBusinesses(String email) {
        User user = requireUser(email);
        List<BusinessUser> memberships = businessUserRepo.findByUser(user);
        return memberships.stream()
                .filter(bu -> !Boolean.TRUE.equals(bu.getBusiness().getDeleted()))
                .map(BusinessMembershipDTO::new)
                .collect(Collectors.toList());
    }

    public UserResponseDTO lookupUser(String query) {
        if (query == null || query.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Search query is required");
        }
        User user = userRepo.findByEmailOrUsername(query, query)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponseDTO(user);
    }

    private User requireUser(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) throw new AppException(HttpStatus.NOT_FOUND, "User not found");
        return user;
    }
}
