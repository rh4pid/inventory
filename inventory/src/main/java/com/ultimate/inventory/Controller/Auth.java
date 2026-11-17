package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.AuthResult;
import com.ultimate.inventory.DTO.LoginResponseDTO;
import com.ultimate.inventory.DTO.SignUpDTO;
import com.ultimate.inventory.DTO.loginDTO;
import com.ultimate.inventory.Service.AuthService;
import com.ultimate.inventory.utils.Security;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;


@RestController
@RequestMapping("/auth")
public class Auth {

    @Autowired private AuthService authService;
    @Autowired private Security security;
    @Autowired private com.ultimate.inventory.Service.PasswordResetService passwordResets;

    public record GoogleSignIn(String credential) {}
    public record ForgotPassword(String login) {}
    public record ResetPassword(String token, String password) {}

    /** What the sign-in page can offer (Google button, sign-up link). */
    @GetMapping("/options")
    public ResponseEntity<?> options() {
        return ResponseEntity.ok(authService.options());
    }

    /** "Continue with Google": signs in, or creates the account on first use. */
    @PostMapping(value = "/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> google(@RequestBody GoogleSignIn body, HttpServletRequest request, HttpServletResponse response) {
        AuthResult result = authService.googleSignIn(body == null ? null : body.credential(),
                request.getHeader("User-Agent"), security.getClientAddress(request));
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(new LoginResponseDTO(result.accessToken(), result.user()));
    }

    /** Emails a reset link from the site's own address. Same answer whether or not the account exists. */
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPassword body) {
        passwordResets.requestReset(body == null ? null : body.login());
        return ResponseEntity.ok(java.util.Map.of("message",
                "If an account matches, we've emailed a link to reset the password. Check your inbox (and spam)."));
    }

    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resetPassword(@RequestBody ResetPassword body) {
        if (body == null) throw new com.ultimate.inventory.Exception.AppException(HttpStatus.BAD_REQUEST, "Fill in the form");
        passwordResets.reset(body.token(), body.password());
        return ResponseEntity.ok(java.util.Map.of("message", "Password changed. Sign in with your new password."));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody @Validated loginDTO dto,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        AuthResult result = authService.login(dto,
                request.getHeader("User-Agent"),
                security.getClientAddress(request));
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(new LoginResponseDTO(result.accessToken(), result.user()));
    }

    /** Creates a personal account and signs it in. */
    @PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> signUp(@RequestBody SignUpDTO dto,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        AuthResult result = authService.signUp(dto,
                request.getHeader("User-Agent"),
                security.getClientAddress(request));
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponseDTO(result.accessToken(), result.user()));
    }

    @GetMapping("/username-available")
    public ResponseEntity<?> usernameAvailable(@RequestParam String username) {
        return ResponseEntity.ok(authService.checkUsername(username));
    }

    @GetMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, "_ultimate_");
        AuthResult result = authService.refreshToken(cookie != null ? cookie.getValue() : null);
        return ResponseEntity.ok(new LoginResponseDTO(result.accessToken(), result.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, "_ultimate_");
        authService.logout(cookie != null ? cookie.getValue() : null);
        clearRefreshCookie(response);
        return ResponseEntity.ok("Logged out successfully");
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("_ultimate_", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 60 * 60);
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("_ultimate_", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
