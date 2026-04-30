package com.miniurl.identity.service;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating RS256 signed JWTs.
 * Only the Identity Service should use this as it requires the private key.
 */
@Service
public class JwtService {

    private final KeyService keyService;
    private final Long expirationMs;

    public JwtService(KeyService keyService, 
                      @Value("${jwt.expiration-ms:3600000}") Long expirationMs) {
        this.keyService = keyService;
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    public String generateToken(UserDetails userDetails, int tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenVersion", tokenVersion);
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expirationMs);
        PrivateKey privateKey = keyService.getPrivateKey();

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(privateKey)
                .compact();
    }

    /**
     * Extracts the username (subject) from a JWT token.
     * Used for operations that must identify the caller from their token,
     * such as delete-account (prevents userId spoofing from request body).
     */
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(keyService.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
