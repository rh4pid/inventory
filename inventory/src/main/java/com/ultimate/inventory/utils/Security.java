package com.ultimate.inventory.utils;


import com.ultimate.inventory.DTO.ResponseDTO;
import com.ultimate.inventory.Model.User;
import com.ultimate.inventory.Repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
public class Security {
    private static final BCryptPasswordEncoder hasher = new BCryptPasswordEncoder();
    private static final SecureRandom random = new SecureRandom();
    private static final long ACCESS_TOKEN_EXP = 15 * 60 * 1000; // 15 min

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    @Autowired
    private UserRepository userRepo;


    public String hashPassword(String password) {
        return hasher.encode(password);
    }

    public boolean isPassword(String original, String hashedPassword) {
        return hasher.matches(original, hashedPassword);
    }

    //generate RefreshToken
    public String generateRefreshToken() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    //generate access token
    public String generateAccessToken(Long userId, String email) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .addClaims(Map.of("email", email))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXP))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    //decode accessToken
    public ResponseDTO decodeAccessToken(String token) {
        ResponseDTO response = new ResponseDTO();

        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token)
                    .getBody();

            String email = claims.get("email", String.class);
            Long userId = Long.parseLong(claims.getSubject());

            Optional<User> user = userRepo.findById(userId);


            if (user.isEmpty()) {
                response.setStatus(false);
                response.setMessage("email does not exist");
                return response;
            }

            if (!user.get().getEmail().matches(email)) {
                response.setStatus(false);
                response.setMessage("email does not match");
                return response;
            }

            response.setStatus(true);
            response.setMessage(email);
            return response;

        } catch (Exception e) {
            response.setStatus(false);
            response.setMessage("Invalid token");
            return response;
        }
    }

    public ResponseDTO retrieveAccessToken(HttpServletRequest httpRequest) {
        String authorizationHeader = httpRequest.getHeader("Authorization");
        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            ResponseDTO response = new ResponseDTO();
            response.setStatus(false);
            response.setMessage("No Authorization");
            return response;
        }
        return decodeAccessToken(authorizationHeader.split(" ")[1]);
    }

    public String getClientAddress(HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            return httpRequest.getRemoteAddr();
        }
        return ipAddress.split(",")[0].trim();
    }

}
