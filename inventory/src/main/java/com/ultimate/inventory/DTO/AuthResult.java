package com.ultimate.inventory.DTO;

/**
 * Internal result returned by AuthService — not sent to clients directly.
 * refreshToken is non-null only on login; null on token refresh (no new cookie needed).
 */
public record AuthResult(String accessToken, String refreshToken, UserResponseDTO user) {}
