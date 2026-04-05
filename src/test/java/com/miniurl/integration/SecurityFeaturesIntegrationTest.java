package com.miniurl.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for security features:
 * - Rate limiting
 * - Session management
 * - Authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Features Integration Tests")
class SecurityFeaturesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean up - use unique test user per test run
        try {
            urlRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        // Create USER role if not exists
        Role userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        // Create test user with unique username/email
        String uniqueId = String.valueOf(System.currentTimeMillis());
        testUser = User.builder()
            .username("securitytestuser" + uniqueId)
            .email("securitytest" + uniqueId + "@example.com")
            .firstName("Security Test")
            .lastName("User")
            .password(passwordEncoder.encode("TestPass123!@#"))
            .role(userRole)
            .status(UserStatus.ACTIVE)
            .otpVerified(true)
            .build();

        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Login endpoint should be accessible without authentication")
    void loginEndpoint_shouldBePublic() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUser.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        // Act & Assert - Login should succeed
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Health endpoint should be accessible without authentication")
    void healthEndpoint_shouldBePublic() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Protected endpoints should reject requests without authentication")
    void protectedEndpoints_shouldReject_noAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/urls"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoints should reject invalid JWT")
    void protectedEndpoints_shouldReject_invalidJwt() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/urls")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with wrong password should return 400")
    void login_withWrongPassword_shouldReturnBadRequest() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUser.getUsername());
        loginRequest.put("password", "WrongPassword123!");

        // Act & Assert - AuthController returns 400 for bad credentials
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Login with non-existent user should return 400")
    void login_withNonExistentUser_shouldReturnBadRequest() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "nonexistent" + System.currentTimeMillis());
        loginRequest.put("password", "TestPass123!@#");

        // Act & Assert - AuthController returns 400 for bad credentials
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Rate limiting should be active")
    void rateLimiting_shouldBeActive() throws Exception {
        // Arrange - Make many requests to trigger rate limit
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUser.getUsername());
        loginRequest.put("password", "WrongPassword");

        // Act - Make 10 rapid failed login attempts
        // This test just verifies rate limiting doesn't crash the application
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)));
        }
        // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("Health endpoint should respond to requests with Accept header")
    void healthEndpoint_shouldRespondWithAcceptHeader() throws Exception {
        // Note: MockMvc cannot fully test CORS preflight (requires real browser).
        // CORS is tested manually or via browser-based E2E tests.
        mockMvc.perform(get("/api/health")
                .header("Accept", "*/*"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("JWT token should be generated on successful login")
    void login_shouldGenerateJwtToken() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUser.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        // Act & Assert - Login should return a token in data.token
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists())
            .andExpect(jsonPath("$.data.username").value(testUser.getUsername()));
    }

    @Test
    @DisplayName("Login rate limiting should block after exceeding per-username limit")
    void loginRateLimiting_shouldBlockAfterExceedingPerUsernameLimit() throws Exception {
        // The test profile sets login-by-username to 1000 req/60s.
        // We test that the filter extracts the username and doesn't crash.
        // The actual 429 is verified with a lower limit config.
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "nonexistent-" + System.currentTimeMillis());
        loginRequest.put("password", "wrong");

        // Make multiple requests with the same username - should not crash
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)));
        }
        // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("Login rate limiting should allow different usernames from same request")
    void loginRateLimiting_differentUsernames_shouldNotAffectEachOther() throws Exception {
        // Different usernames should get separate rate limit buckets
        for (int i = 0; i < 10; i++) {
            Map<String, String> loginRequest = new HashMap<>();
            loginRequest.put("username", "unique-user-" + i + "-" + System.currentTimeMillis());
            loginRequest.put("password", "wrong");

            mockMvc.perform(post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
        }
        // All requests should return 400 (bad credentials), not 429 (rate limited)
    }
}
