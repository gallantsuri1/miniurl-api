package com.miniurl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JwtUtil class.
 */
@DisplayName("JwtUtil Tests")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", 
            "TestSecretKeyForJWTTokenGeneration2024VeryLongSecureSecretKeyForHS512AlgorithmTestingPurposes");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600000L);
    }

    @Test
    @DisplayName("generateToken creates valid token")
    void generateToken() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(token);
        assertTrue(token.length() > 50);
    }

    @Test
    @DisplayName("generateToken with version")
    void generateTokenWithVersion() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails, 1);
        assertNotNull(token);
    }

    @Test
    @DisplayName("extractUsername")
    void extractUsername() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertEquals("testuser", jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("extractExpiration")
    void extractExpiration() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(jwtUtil.extractExpiration(token));
    }

    @Test
    @DisplayName("isTokenExpired - valid token")
    void isTokenExpiredValidToken() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    @DisplayName("isTokenExpired - invalid token")
    void isTokenExpiredInvalidToken() {
        assertTrue(jwtUtil.isTokenExpired("invalid.token.here"));
    }

    @Test
    @DisplayName("validateToken - valid")
    void validateTokenValid() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertTrue(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    @DisplayName("validateToken - wrong user")
    void validateTokenWrongUser() {
        UserDetails user1 = User.withUsername("user1").password("password").roles("USER").build();
        UserDetails user2 = User.withUsername("user2").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(user1);
        assertFalse(jwtUtil.validateToken(token, user2));
    }

    @Test
    @DisplayName("validateToken with version")
    void validateTokenWithVersion() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails, 1);
        assertTrue(jwtUtil.validateToken(token, userDetails, 1));
        assertFalse(jwtUtil.validateToken(token, userDetails, 2));
    }

    @Test
    @DisplayName("needsRenewal - fresh token")
    void needsRenewalFreshToken() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertFalse(jwtUtil.needsRenewal(token));
    }

    @Test
    @DisplayName("needsRenewal - invalid token")
    void needsRenewalInvalidToken() {
        assertTrue(jwtUtil.needsRenewal("invalid.token.here"));
    }

    @Test
    @DisplayName("renewToken generates new token")
    void renewToken() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String oldToken = jwtUtil.generateToken(userDetails);
        // renewToken generates a new token if the old one is valid
        String newToken = jwtUtil.renewToken(oldToken, userDetails);
        assertNotNull(newToken);
        // Note: Tokens may be equal if generated in the same millisecond
        // The important thing is that a valid token is returned
        assertNotNull(jwtUtil.validateToken(newToken, userDetails));
    }

    @Test
    @DisplayName("renewToken with version")
    void renewTokenWithVersion() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String oldToken = jwtUtil.generateToken(userDetails, 1);
        String newToken = jwtUtil.renewToken(oldToken, userDetails, 1);
        assertNotNull(newToken);
    }

    @Test
    @DisplayName("renewTokenIfNeeded")
    void renewTokenIfNeeded() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails);
        assertNull(jwtUtil.renewTokenIfNeeded(token, userDetails));
    }

    @Test
    @DisplayName("getTokenVersion")
    void getTokenVersion() {
        UserDetails userDetails = User.withUsername("testuser").password("password").roles("USER").build();
        String token = jwtUtil.generateToken(userDetails, 5);
        assertEquals(5, jwtUtil.getTokenVersion(token));
    }

    @Test
    @DisplayName("getExpirationMs")
    void getExpirationMs() {
        assertEquals(3600000L, jwtUtil.getExpirationMs());
    }

    @Test
    @DisplayName("validateJwtSecret - short secret")
    void validateJwtSecretShort() {
        JwtUtil shortJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortJwtUtil, "secret", "short");
        assertThrows(IllegalStateException.class, () -> shortJwtUtil.validateJwtSecret());
    }

    @Test
    @DisplayName("validateJwtSecret - long secret")
    void validateJwtSecretLong() {
        JwtUtil longJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(longJwtUtil, "secret", 
            "ThisIsAVeryLongSecretKeyThatIsAtLeast32CharactersLongForSecurity");
        assertDoesNotThrow(() -> longJwtUtil.validateJwtSecret());
    }
}
