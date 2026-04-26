package com.miniurl.identity;

import com.miniurl.identity.service.JwtService;
import com.miniurl.identity.service.KeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        KeyService keyService = new KeyService();
        keyService.init();
        jwtService = new JwtService(keyService, 3600000L);
    }

    @Test
    @DisplayName("generateToken creates valid RS256-signed token")
    void generateToken() {
        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT should have 3 parts");
    }

    @Test
    @DisplayName("generateToken with version claims")
    void generateTokenWithVersion() {
        UserDetails userDetails = User.withUsername("testuser").password("pass").roles("USER").build();
        String token = jwtService.generateToken(userDetails, 2);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "JWT should have 3 parts");
    }
}
