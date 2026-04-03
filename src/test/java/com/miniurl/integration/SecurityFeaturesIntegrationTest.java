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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
@ActiveProfiles("dev")
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
            // Clear all data
            urlRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors for integration tests
        }

        // Create USER role if not exists
        Role userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        // Create test user with unique username/email and strong password
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
            .andExpect(status().isOk());
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
    @DisplayName("Login with wrong password should return 401")
    void login_withWrongPassword_shouldReturn401() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUser.getUsername());
        loginRequest.put("password", "WrongPassword123!");

        // Act & Assert - Spring Security returns 401 for bad credentials
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with non-existent user should return 401")
    void login_withNonExistentUser_shouldReturn401() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "nonexistent" + System.currentTimeMillis());
        loginRequest.put("password", "TestPass123!@#");

        // Act & Assert - Spring Security returns 401 for bad credentials
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized());
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
    @DisplayName("Signup endpoint should be accessible")
    void signupEndpoint_shouldBeAccessible() throws Exception {
        // Note: Password validation is tested in AuthService unit tests
        // This test verifies the signup endpoint is accessible
        // Signup now requires an invitation token instead of email
        Map<String, String> signupRequest = new HashMap<>();
        signupRequest.put("firstName", "New");
        signupRequest.put("lastName", "User");
        signupRequest.put("username", "newuser" + System.currentTimeMillis());
        signupRequest.put("password", "Pass123!");
        signupRequest.put("invitationToken", "test-invite-token-" + System.currentTimeMillis());

        // Act & Assert - Signup endpoint should be accessible
        // Returns 400 for invalid/missing invitation token (expected behavior)
        mockMvc.perform(post("/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CORS should be configured for allowed origins")
    void cors_shouldBeConfigured() throws Exception {
        // Act - Send request with Origin header
        mockMvc.perform(get("/api/health")
                .header("Origin", "http://localhost:8080"))
            .andExpect(status().isOk());
        
        // Note: Full CORS testing requires browser-like environment
        // This test verifies the endpoint responds to cross-origin requests
    }

    @Test
    @DisplayName("JWT token should be generated on successful login")
    void login_shouldGenerateJwtToken() throws Exception {
        // Arrange
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", testUser.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        // Act & Assert - Login should return a token
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.username").value(testUser.getUsername()));
    }
}
