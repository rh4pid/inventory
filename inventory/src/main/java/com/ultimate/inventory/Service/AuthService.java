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
    @org.springframework.beans.factory.annotation.Value("${app.google.client-id:}") private String googleClientId;

    private final java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private final tools.jackson.databind.ObjectMapper json = new tools.jackson.databind.ObjectMapper();

    /** What the sign-in page may offer (read at runtime, so no frontend rebuild is needed to switch Google on). */
    public record AuthOptions(String googleClientId, boolean signUpOpen) {}

    public AuthOptions options() {
        return new AuthOptions(googleClientId == null || googleClientId.isBlank() ? null : googleClientId.trim(), selfSignUp);
    }

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

    /**
     * "Continue with Google": the browser gets an ID token from Google and we check it with Google.
     * An account with the same (Google-verified) email is signed in and linked; otherwise a new
     * account is created with a username made from the name. Google-only accounts can set a
     * password later with "Forgot password".
     */
    @Transactional
    public AuthResult googleSignIn(String credential, String deviceInfo, String ipAddress) {
        String clientId = options().googleClientId();
        if (clientId == null) throw new AppException(HttpStatus.NOT_FOUND, "Google sign-in is not set up");
        if (credential == null || credential.isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Google did not send a sign-in");
        java.util.Map<?, ?> claims = verifyGoogleToken(credential.trim());
        if (!clientId.equals(claim(claims, "aud"))) throw googleRefused();
        String iss = String.valueOf(claim(claims, "iss"));
        if (!iss.equals("accounts.google.com") && !iss.equals("https://accounts.google.com")) throw googleRefused();
        long exp;
        try { exp = Long.parseLong(String.valueOf(claim(claims, "exp"))); } catch (NumberFormatException e) { throw googleRefused(); }
        if (exp * 1000 < System.currentTimeMillis()) throw googleRefused();
        if (!"true".equalsIgnoreCase(claim(claims, "email_verified"))) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Your Google email is not verified");
        }
        String subject = claim(claims, "sub");
        String email = UserAccounts.normalizeEmail(claim(claims, "email"));
        if (subject == null || subject.isBlank()) throw googleRefused();

        User user = userRepo.findByGoogleSubject(subject);
        if (user == null) {
            user = userRepo.findByEmail(email);
            if (user == null) user = userRepo.findFirstByEmailIgnoreCaseOrderByIdAsc(email);
        }
        if (user != null) {
            if (Boolean.TRUE.equals(user.getSuperAdmin())) {
                throw new AppException(HttpStatus.FORBIDDEN, "The system administrator signs in with a password");
            }
            ensureCanSignIn(user);
            if (user.getGoogleSubject() == null) {
                user.setGoogleSubject(subject);
                user.setUpdatedAt(new Date());
                user.setUpdatedBy(email);
                user = userRepo.save(user);
            }
            log.info("User signed in with Google: {}", user.getEmail());
            return startSession(user, deviceInfo, ipAddress);
        }

        ensureSignUpOpen();
        String first = blankToNull(claim(claims, "given_name"));
        String last = blankToNull(claim(claims, "family_name"));
        if (first == null) first = email.substring(0, email.indexOf('@'));
        if (last == null) last = "";
        User created = new User();
        created.setFirstName(first);
        created.setLastName(last);
        created.setEmail(email);
        created.setUsername(usernames.suggest(first, last));
        created.setGoogleSubject(subject);
        String picture = blankToNull(claim(claims, "picture"));
        if (picture != null && picture.length() <= 255) created.setProfilePicture(picture);
        // No password of their own yet: a random one nobody knows (they can set one with "Forgot password").
        created.setPassword(security.hashPassword(security.generateRefreshToken().substring(0, 40)));
        created.setMustChangePassword(false);
        created.setSuperAdmin(false);
        created.setLocked(false);
        created.setSuspended(false);
        created.setDeleted(false);
        created.setCreatedBy(email);
        created.setCreatedAt(new Date());
        created = userRepo.save(created);
        log.info("New account from Google: @{} ({})", created.getUsername(), email);
        return startSession(created, deviceInfo, ipAddress);
    }

    private static String claim(java.util.Map<?, ?> claims, String key) {
        Object v = claims.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private java.util.Map<?, ?> verifyGoogleToken(String idToken) {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                            "https://oauth2.googleapis.com/tokeninfo?id_token=" + java.net.URLEncoder.encode(idToken, java.nio.charset.StandardCharsets.UTF_8)))
                    .timeout(java.time.Duration.ofSeconds(15)).GET().build();
            var res = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw googleRefused();
            return json.readValue(res.body(), java.util.Map.class);
        } catch (AppException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't reach Google. Try again.");
        } catch (Exception e) {
            log.warn("Google token check failed: {}", e.toString());
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Couldn't reach Google. Try again.");
        }
    }

    private static AppException googleRefused() {
        return new AppException(HttpStatus.UNAUTHORIZED, "Google sign-in failed. Try again.");
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
