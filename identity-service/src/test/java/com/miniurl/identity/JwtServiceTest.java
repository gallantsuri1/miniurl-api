package com.miniurl.identity;

import com.miniurl.identity.service.JwtService;
import com.miniurl.identity.service.KeyService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private KeyService keyService;
    private java.security.PublicKey publicKey;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        String privPath = tempDir.resolve("private.pem").toString();
        String pubPath = tempDir.resolve("public.pem").toString();
        keyService = new KeyService(privPath, pubPath, "test-key-id");
        keyService.init();
        jwtService = new JwtService(keyService, 3600000L);
        publicKey = keyService.getPublicKey();
    }

    @Test
    @DisplayName("generateToken creates valid RS256-signed token with correct subject")
    void generateToken() {
        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = jwtService.generateToken(userDetails);

        Claims claims = Jwts.parser()
                .verifyWith((java.security.interfaces.RSAPublicKey) publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("testuser", claims.getSubject());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("generateToken with version claim")
    void generateTokenWithVersion() {
        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = jwtService.generateToken(userDetails, 2);

        Claims claims = Jwts.parser()
                .verifyWith((java.security.interfaces.RSAPublicKey) publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("testuser", claims.getSubject());
        assertEquals(2, claims.get("tokenVersion", Integer.class));
    }

    @Test
    @DisplayName("token signed by one key is rejected by a different key")
    void tokenFromDifferentKeyIsRejected(@TempDir Path tempDir) {
        String otherPrivPath = tempDir.resolve("other-private.pem").toString();
        String otherPubPath = tempDir.resolve("other-public.pem").toString();
        KeyService otherKeyService = new KeyService(otherPrivPath, otherPubPath, "other-key-id");
        otherKeyService.init();
        JwtService otherJwtService = new JwtService(otherKeyService, 3600000L);

        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = otherJwtService.generateToken(userDetails);

        assertThrows(Exception.class, () -> {
            Jwts.parser()
                    .verifyWith((java.security.interfaces.RSAPublicKey) publicKey)
                    .build()
                    .parseSignedClaims(token);
        }, "Token signed by a different key should be rejected");
    }

    @Test
    @DisplayName("token expiration is set correctly")
    void tokenExpiration() {
        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = jwtService.generateToken(userDetails);

        Claims claims = Jwts.parser()
                .verifyWith((java.security.interfaces.RSAPublicKey) publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long ttl = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(3600000L, ttl, 1000, "Token TTL should be approximately 1 hour");
    }

    @Test
    @DisplayName("extractUsername returns the subject from a valid token")
    void extractUsernameReturnsSubject() {
        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = jwtService.generateToken(userDetails);

        String username = jwtService.extractUsername(token);
        assertEquals("testuser", username);
    }
}
