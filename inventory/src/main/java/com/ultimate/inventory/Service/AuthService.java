package com.ultimate.inventory.Service;

import com.ultimate.inventory.DTO.AuthResult;
import com.ultimate.inventory.DTO.SignUpDTO;
import com.ultimate.inventory.DTO.UserResponseDTO;
import com.ultimate.inventory.DTO.loginDTO;
import com.ultimate.inventory.Exception.AppException;
import com.ultimate.inventory.Model.BusinessUser;
import com.ultimate.inventory.Model.RefreshTokens;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.BusinessUserRepo;
import com.ultimate.inventory.Repository.RefreshTokenRepo;
import com.ultimate.inventory.Repository.UserRepository;
import com.ultimate.inventory.utils.Security;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired private UserRepository userRepo;
    @Autowired private RefreshTokenRepo refreshTokenRepo;
    @Autowired private BusinessUserRepo businessUserRepo;
    @Autowired private Security security;
    @Autowired private Usernames usernames;
    @org.springframework.beans.factory.annotation.Value("${app.self-signup:true}") private boolean selfSignUp;

    public void ensureSignUpOpen() {
        if (!selfSignUp) {
            throw new AppException(HttpStatus.FORBIDDEN, "Sign-up is closed right now. Ask a company administrator to add you.");
        }
    }

    /** Disabled, deleted or suspended accounts, and people whose companies are all suspended, cannot sign in. */
    private void ensureCanSignIn(User user) {
        if (Boolean.TRUE.equals(user.getDeleted()) || Boolean.TRUE.equals(user.getLocked()) || Boolean.TRUE.equals(user.getSuspended())) {
            throw new AppException(HttpStatus.FORBIDDEN, "This account is disabled. Contact your administrator.");
        }
        if (Boolean.TRUE.equals(user.getSuperAdmin())) return;
        var memberships = businessUserRepo.findByUser(user).stream()
                .filter(BusinessUser::isActiveMember)
                .filter(bu -> !Boolean.TRUE.equals(bu.getBusiness().getDeleted()))
                .toList();
        if (!memberships.isEmpty() && memberships.stream().allMatch(bu -> Boolean.TRUE.equals(bu.getBusiness().getSuspended()))) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your company account is suspended. Contact the system administrator.");
        }
    }

    @Transactional
    public AuthResult login(loginDTO dto, String deviceInfo, String ipAddress) {
        User user = findForSignIn(dto.getLogin());
        if (user == null || !security.isPassword(dto.getPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        ensureCanSignIn(user);
        log.info("User logged in: {}", user.getEmail());
        return startSession(user, deviceInfo, ipAddress);
    }

    /** "ama@shop.com" finds by email; "ama_mensah" or "@ama_mensah" by username. */
    private User findForSignIn(String login) {
        String l = login == null ? "" : login.trim();
        if (l.indexOf('@') > 0) {
            User exact = userRepo.findByEmail(l);
            return exact != null ? exact : userRepo.findFirstByEmailIgnoreCaseOrderByIdAsc(l);
        }
        String username = Usernames.clean(l);
        return username.isEmpty() ? null : userRepo.findByUsername(username);
    }

    /** Anyone can create an account; they then create a company or get invited to one. */
    @Transactional
    public AuthResult signUp(SignUpDTO dto, String deviceInfo, String ipAddress) {
        ensureSignUpOpen();
        if (dto == null) throw new AppException(HttpStatus.BAD_REQUEST, "Fill in the form");
        String firstName = required(dto.firstName(), "First name is required");
        String lastName = required(dto.lastName(), "Last name is required");
        String email = UserAccounts.normalizeEmail(dto.email());
        if (userRepo.existsByEmailIgnoreCase(email)) {
            throw new AppException(HttpStatus.CONFLICT, "An account with this email already exists. Sign in instead.");
        }
        String username = usernames.requireAvailable(dto.username(), null);
        UserAccounts.checkNewPassword(dto.password());

        User user = new User();
        user.setFirstName(firstName);
        user.setOtherName(blankToNull(dto.otherName()));
        user.setLastName(lastName);
        user.setEmail(email);
        user.setUsername(username);
        user.setTelephonePrimary(blankToNull(dto.telephonePrimary()));
        user.setPassword(security.hashPassword(dto.password()));
        user.setMustChangePassword(false);
        user.setSuperAdmin(false);
        user.setLocked(false);
        user.setSuspended(false);
        user.setDeleted(false);
        user.setCreatedBy(email);
        user.setCreatedAt(new Date());
        user = userRepo.save(user);

        log.info("New account: @{} ({})", username, email);
        return startSession(user, deviceInfo, ipAddress);
    }

    /** For the sign-up form: can this username be taken? (Usernames are public handles, so this reveals nothing private.) */
    public UsernameCheck checkUsername(String raw) {
        String u = Usernames.clean(raw);
        String problem = Usernames.problem(u);
        if (problem == null && !usernames.isFree(u, null)) problem = "@" + u + " is taken";
        return new UsernameCheck(u, problem == null, problem);
    }

    public record UsernameCheck(String username, boolean available, String reason) {}

    private AuthResult startSession(User user, String deviceInfo, String ipAddress) {
        String rawToken = security.generateRefreshToken();
        RefreshTokens refreshToken = new RefreshTokens();
        refreshToken.setUser(user);
        refreshToken.setToken(rawToken);
        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setExpiryDate(new Date(System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)));
        refreshTokenRepo.save(refreshToken);

        String accessToken = security.generateAccessToken(user.getId(), user.getEmail());
        return new AuthResult(accessToken, rawToken, new UserResponseDTO(user));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public AuthResult refreshToken(String cookieValue) {
        if (cookieValue == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "No session found. Please login.");
        }
        RefreshTokens token = refreshTokenRepo.findAllByToken(cookieValue);
        if (token == null || !token.getExpiryDate().after(new Date())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Session expired. Please login again.");
        }
        ensureCanSignIn(token.getUser());
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
