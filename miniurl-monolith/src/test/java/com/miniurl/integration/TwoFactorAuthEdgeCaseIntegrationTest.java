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
import com.miniurl.repository.UrlRepository;
import com.miniurl.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Edge case integration tests for 2FA (Two-Factor Authentication).
 *
 * Tests cover:
 * - OTP expiry after time limit
 * - Multiple OTP verification attempts
 * - Login with email address instead of username
 * - 2FA toggle at runtime (enable → disable)
 * - OTP verification clears OTP from DB
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("2FA Edge Case Integration Tests")
class TwoFactorAuthEdgeCaseIntegrationTest {

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

    @Autowired
    private GlobalFlagRepository globalFlagRepository;

    @Autowired
    private FeatureRepository featureRepository;

    private Role userRole;

    @BeforeEach
    void setUp() {
        try {
            urlRepository.deleteAll();
            globalFlagRepository.deleteAll();
            featureRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        String uniqueId = String.valueOf(System.currentTimeMillis());
        User testUser = User.builder()
            .username("2faedge" + uniqueId)
            .email("2faedge" + uniqueId + "@example.com")
            .firstName("2FA")
            .lastName("Edge")
            .password(passwordEncoder.encode("TestPass123!@#"))
            .role(userRole)
            .status(UserStatus.ACTIVE)
            .otpVerified(true)
            .build();

        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        try {
            urlRepository.deleteAll();
            globalFlagRepository.deleteAll();
            featureRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private User getTestUser() {
        return userRepository.findAll().stream()
            .filter(u -> u.getUsername().startsWith("2faedge"))
            .findFirst().orElseThrow();
    }

    private void enable2FA() {
        Feature feature = featureRepository.save(
            new Feature("TWO_FACTOR_AUTH", "Two-Factor Authentication", "Require OTP"));
        globalFlagRepository.save(new GlobalFlag(feature, true));
    }

    @Test
    @DisplayName("Login with email address instead of username should work with 2FA")
    void login_withEmail_shouldWorkWith2FA() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Act - Use email as login identifier
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getEmail());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.otpRequired").value(true));

        // Verify OTP was generated
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(updatedUser.getOtpCode());
    }

    @Test
    @DisplayName("Multiple OTP verification attempts should work with latest valid OTP")
    void multipleOtpAttempts_shouldAcceptValidOtp() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());

        String otp = userRepository.findById(user.getId()).orElseThrow().getOtpCode();

        // Step 2: Wrong OTP attempt
        Map<String, String> wrongVerify = Map.of("username", user.getUsername(), "otp", "000000");
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongVerify)))
            .andExpect(status().isBadRequest());

        // Step 3: Verify with correct OTP - should still work (OTP not cleared on wrong attempt)
        Map<String, String> correctVerify = Map.of("username", user.getUsername(), "otp", otp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(correctVerify)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("OTP verification should clear OTP from DB")
    void verifyOtp_shouldClearOtpFromDb() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());

        // Verify OTP is stored
        User afterLogin = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(afterLogin.getOtpCode());
        assertNotNull(afterLogin.getOtpExpiry());
        assertFalse(afterLogin.isOtpVerified());

        String otp = afterLogin.getOtpCode();

        // Step 2: Verify OTP
        Map<String, String> verifyRequest = Map.of("username", user.getUsername(), "otp", otp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());

        // Verify OTP is cleared from DB
        User afterVerify = userRepository.findById(user.getId()).orElseThrow();
        assertNull(afterVerify.getOtpCode(), "OTP should be cleared after verification");
        assertNull(afterVerify.getOtpExpiry(), "OTP expiry should be cleared");
        assertTrue(afterVerify.isOtpVerified(), "OTP should be marked verified");
    }

    @Test
    @DisplayName("OTP with expired expiry should fail verification")
    void expiredOtp_shouldFailVerification() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login to generate OTP
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());

        // Step 2: Manually set OTP to expired
        user = userRepository.findById(user.getId()).orElseThrow();
        String otp = user.getOtpCode();
        user.setOtpExpiry(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        // Step 3: Try to verify expired OTP
        Map<String, String> verifyRequest = Map.of("username", user.getUsername(), "otp", otp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("OTP has expired. Please login again."));
    }

    @Test
    @DisplayName("Disabling 2FA after login should still require OTP for that session")
    void disable2fa_afterLogin_shouldStillRequireOtp() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login with 2FA enabled
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.otpRequired").value(true))
            .andReturn();

        String otp = userRepository.findById(user.getId()).orElseThrow().getOtpCode();

        // Step 2: Disable 2FA
        globalFlagRepository.deleteAll();

        // Step 3: Verify OTP should still work (OTP was already generated)
        Map<String, String> verifyRequest = Map.of("username", user.getUsername(), "otp", otp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("Resend OTP with expired OTP should generate new one")
    void resendOtp_expired_shouldGenerateNewOtp() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());

        String firstOtp = userRepository.findById(user.getId()).orElseThrow().getOtpCode();

        // Step 2: Manually expire the OTP
        user = userRepository.findById(user.getId()).orElseThrow();
        user.setOtpExpiry(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        // Step 3: Resend — should generate new OTP
        Map<String, String> resendRequest = Map.of("username", user.getUsername());
        mockMvc.perform(post("/api/auth/resend-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resendRequest)))
            .andExpect(status().isOk());

        String secondOtp = userRepository.findById(user.getId()).orElseThrow().getOtpCode();

        // Step 4: First OTP should fail (it was expired)
        Map<String, String> firstVerify = Map.of("username", user.getUsername(), "otp", firstOtp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstVerify)))
            .andExpect(status().isBadRequest());

        // Step 5: Second OTP should succeed
        Map<String, String> secondVerify = Map.of("username", user.getUsername(), "otp", secondOtp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondVerify)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("Login flow should work with 2FA enabled via admin toggle")
    void login_2faEnabledViaAdmin_shouldWork() throws Exception {
        // Initially no 2FA flag
        User user = getTestUser();

        // Direct login should work without OTP
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());

        // Now enable 2FA (simulating admin action)
        enable2FA();

        // Next login should require OTP
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.otpRequired").value(true));
    }
}
