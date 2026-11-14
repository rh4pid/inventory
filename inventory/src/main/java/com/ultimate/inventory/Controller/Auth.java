package com.ultimate.inventory.Controller;

import com.ultimate.inventory.DTO.AuthResult;
import com.ultimate.inventory.DTO.LoginResponseDTO;
import com.ultimate.inventory.DTO.RegisterDTO;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
public class Auth {

    @Autowired private AuthService authService;
    @Autowired private Security security;
    @Autowired private jakarta.validation.Validator validator;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(@RequestPart("data") RegisterDTO dto,
                                      @RequestPart(value = "businessLogo", required = false) MultipartFile logo)
            throws IOException {
        // Closed by default: say so before complaining about the form.
        authService.ensureSignUpOpen();
        var problems = validator.validate(dto);
        if (!problems.isEmpty()) {
            throw new com.ultimate.inventory.Exception.AppException(HttpStatus.BAD_REQUEST, problems.iterator().next().getMessage());
        }
        authService.register(dto, logo);
        return ResponseEntity.status(HttpStatus.CREATED).body("Registered successfully. Please login.");
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
