package com.miniurl.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniurl.entity.Feature;
import com.miniurl.entity.GlobalFlag;
import com.miniurl.entity.Role;
import com.miniurl.entity.User;
import com.miniurl.entity.UserStatus;
import com.miniurl.repository.FeatureRepository;
import com.miniurl.repository.GlobalFlagRepository;
import com.miniurl.repository.RoleRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for global feature flags.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Global Feature Flags Integration Tests")
class FeatureFlagIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FeatureRepository featureRepository;

    @Autowired
    private GlobalFlagRepository globalFlagRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Clean up
        try {
            globalFlagRepository.deleteAll();
            featureRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @DisplayName("GET /api/features/global should return global flags without authentication")
    void getGlobalFlags_NoAuthRequired() throws Exception {
        // Arrange - Create a global flag
        Feature feature = new Feature("GLOBAL_USER_SIGNUP", "User Sign Up", "Allow new user registration");
        feature = featureRepository.save(feature);

        GlobalFlag globalFlag = new GlobalFlag(feature, true);
        globalFlagRepository.save(globalFlag);

        // Act & Assert - Should be accessible without auth
        mockMvc.perform(get("/api/features/global"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.flags").isArray());
    }

    @Test
    @DisplayName("POST /api/self-invite/send should work without authentication when GLOBAL_USER_SIGNUP enabled")
    void selfInvite_NoAuth_WhenEnabled() throws Exception {
        // Arrange - Create GLOBAL_USER_SIGNUP global flag
        Feature feature = new Feature("GLOBAL_USER_SIGNUP", "User Sign Up", "Allow new user registration");
        feature = featureRepository.save(feature);

        GlobalFlag globalFlag = new GlobalFlag(feature, true);
        globalFlagRepository.save(globalFlag);

        // Act & Assert - Should work without auth
        mockMvc.perform(post("/api/self-invite/send")
                .with(csrf())
                .param("email", "test@example.com")
                .param("baseUrl", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/self-invite/send should fail when GLOBAL_USER_SIGNUP disabled")
    void selfInvite_ShouldFail_WhenDisabled() throws Exception {
        // Arrange - Create disabled GLOBAL_USER_SIGNUP flag
        Feature feature = new Feature("GLOBAL_USER_SIGNUP", "User Sign Up", "Allow new user registration");
        feature = featureRepository.save(feature);

        GlobalFlag globalFlag = new GlobalFlag(feature, false);
        globalFlagRepository.save(globalFlag);

        // Act & Assert - Should fail
        mockMvc.perform(post("/api/self-invite/send")
                .with(csrf())
                .param("email", "test@example.com")
                .param("baseUrl", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Self-signup is currently disabled"));
    }

    @Test
    @DisplayName("POST /api/self-invite/send should fail with existing email")
    void selfInvite_ShouldFail_WithEmailExists() throws Exception {
        // Arrange - Create GLOBAL_USER_SIGNUP flag and existing user
        Feature feature = new Feature("GLOBAL_USER_SIGNUP", "User Sign Up", "Allow new user registration");
        feature = featureRepository.save(feature);
        
        GlobalFlag globalFlag = new GlobalFlag(feature, true);
        globalFlagRepository.save(globalFlag);

        Role userRole = roleRepository.findByName("USER").orElseThrow();
        User existingUser = User.builder()
            .email("existing@example.com")
            .username("existinguser")
            .password(passwordEncoder.encode("Test123!"))
            .firstName("Existing")
            .lastName("User")
            .role(userRole)
            .otpVerified(true)
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(existingUser);

        // Act & Assert - Should fail
        mockMvc.perform(post("/api/self-invite/send")
                .with(csrf())
                .param("email", "existing@example.com")
                .param("baseUrl", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Email already registered: existing@example.com"));
    }
}
