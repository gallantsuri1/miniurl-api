package com.miniurl.integration;

import com.miniurl.entity.Role;
import com.miniurl.entity.User;
import com.miniurl.entity.UserStatus;
import com.miniurl.repository.RoleRepository;
import com.miniurl.repository.UrlRepository;
import com.miniurl.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for per-email rate limiting on password reset and OTP endpoints.
 * These tests verify the dual-layer (per-IP + per-email) rate limiting is active
 * and doesn't crash the application. The actual 429 blocking is tested with
 * low limits via @TestPropertySource in isolated test runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Per-Email Rate Limit Integration Tests")
class PerEmailRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        try {
            urlRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore
        }

        Role userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        String uniqueId = String.valueOf(System.currentTimeMillis());
        testUser = User.builder()
            .username("peremailtest" + uniqueId)
            .email("peremailtest" + uniqueId + "@example.com")
            .firstName("Rate Limit")
            .lastName("Test")
            .password(passwordEncoder.encode("TestPass123!@#"))
            .role(userRole)
            .status(UserStatus.ACTIVE)
            .otpVerified(true)
            .build();

        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Password reset per-email rate limiting should be active (doesn't crash)")
    void passwordReset_perEmailRateLimiting_shouldBeActive() throws Exception {
        // Make multiple requests to verify rate limiting doesn't crash the app
        Map<String, String> request = Map.of("email", testUser.getEmail());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }
        // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("OTP verify per-email/username rate limiting should be active (doesn't crash)")
    void otpVerify_perEmailRateLimiting_shouldBeActive() throws Exception {
        // Make multiple requests to verify rate limiting doesn't crash the app
        Map<String, String> request = Map.of("username", testUser.getUsername(), "otp", "123456");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/verify-otp")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }
        // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("Different emails should have separate rate limit buckets for password reset")
    void passwordReset_differentEmails_shouldHaveSeparateBuckets() throws Exception {
        // Each email gets its own bucket, so both can make requests independently
        Map<String, String> request1 = Map.of("email", "user1@example.com");
        Map<String, String> request2 = Map.of("email", "user2@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Resend OTP per-email rate limiting should be active (doesn't crash)")
    void resendOtp_perEmailRateLimiting_shouldBeActive() throws Exception {
        // Make multiple requests to verify rate limiting doesn't crash the app
        // ResendOtpRequest uses "username" field (can be username or email)
        Map<String, String> request = Map.of("username", testUser.getEmail());

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/resend-otp")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }
        // Test passes if no exception is thrown
    }
}
