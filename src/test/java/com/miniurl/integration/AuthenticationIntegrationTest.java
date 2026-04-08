package com.miniurl.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniurl.dto.JwtAuthRequest;
import com.miniurl.entity.Role;
import com.miniurl.entity.User;
import com.miniurl.entity.UserStatus;
import com.miniurl.repository.RoleRepository;
import com.miniurl.repository.UrlRepository;
import com.miniurl.repository.UserRepository;
import com.miniurl.util.TestJwtUtil;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for JWT Authentication flow.
 *
 * Tests cover:
 * - Login with valid credentials
 * - Login with invalid credentials
 * - JWT token validation
 * - Accessing protected endpoints with valid/invalid tokens
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Authentication Integration Tests")
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Role userRole;

    @BeforeEach
    void setUp() {
        // Clean up database before each test - delete URLs first to avoid FK constraints
        try {
            urlRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        // Create USER role if not exists
        userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        // Create test user with strong password
        User testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .firstName("Test")
            .lastName("User")
            .password(passwordEncoder.encode("TestPass123!@#"))
            .role(userRole)
            .status(UserStatus.ACTIVE)
            .otpVerified(true)
            .build();

        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Login with valid credentials should return JWT token")
    void loginWithValidCredentials_shouldReturnJwtToken() throws Exception {
        // Arrange
        JwtAuthRequest request = new JwtAuthRequest("testuser", "TestPass123!@#");

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").exists())
            .andExpect(jsonPath("$.data.username").value("testuser"))
            .andExpect(jsonPath("$.data.userId").exists())
            .andReturn();

        // Verify token is returned
        String responseContent = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseContent).get("data").get("token").asText();

        assertNotNull(token);
        assertTrue(token.length() > 50); // JWT tokens are typically long
    }

    @Test
    @DisplayName("Login with invalid credentials should return 400")
    void loginWithInvalidCredentials_shouldReturnBadRequest() throws Exception {
        // Arrange
        JwtAuthRequest request = new JwtAuthRequest("testuser", "wrongpassword");

        // Act & Assert - AuthController returns 400 for bad credentials
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Login with non-existent user should return 400")
    void loginWithNonExistentUser_shouldReturnBadRequest() throws Exception {
        // Arrange
        JwtAuthRequest request = new JwtAuthRequest("nonexistent", "password");

        // Act & Assert - AuthController returns 400 for bad credentials
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Access protected endpoint with valid JWT token should succeed")
    void accessProtectedEndpointWithValidToken_shouldSucceed() throws Exception {
        // Get JWT token first
        String jwtToken = TestJwtUtil.getJwtToken(mockMvc, "testuser", "TestPass123!@#");

        // Act & Assert - Access protected endpoint with JWT authentication
        mockMvc.perform(get("/api/urls")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Access protected endpoint without token should return 401")
    void accessProtectedEndpointWithoutToken_shouldReturnUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/urls"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access protected endpoint with invalid JWT token should return 401")
    void accessProtectedEndpointWithInvalidToken_shouldReturnUnauthorized() throws Exception {
        // Act & Assert - Use an invalid token
        mockMvc.perform(get("/api/urls")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Health endpoint should be accessible without authentication")
    void healthEndpoint_shouldBeAccessibleWithoutAuth() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Service is running"));
    }
}
