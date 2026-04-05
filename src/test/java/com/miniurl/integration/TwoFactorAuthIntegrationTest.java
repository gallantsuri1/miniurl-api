package com.miniurl.integration;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for 2FA (Two-Factor Authentication) login flow.
 *
 * Tests cover:
 * - Login with 2FA enabled → OTP sent, JWT returned after verification
 * - Login with 2FA disabled → JWT returned immediately
 * - OTP verification (valid, invalid, no prior login)
 * - OTP resend
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("2FA Integration Tests")
class TwoFactorAuthIntegrationTest {

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
        // Clean up
        try {
            urlRepository.deleteAll();
            globalFlagRepository.deleteAll();
            featureRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        // Create USER role
        userRole = roleRepository.findByName("USER")
            .orElseGet(() -> roleRepository.save(new Role("USER", "Regular user")));

        // Create test user
        String uniqueId = String.valueOf(System.currentTimeMillis());
        User testUser = User.builder()
            .username("2fatestuser" + uniqueId)
            .email("2fa" + uniqueId + "@example.com")
            .firstName("2FA")
            .lastName("Test")
            .password(passwordEncoder.encode("TestPass123!@#"))
            .role(userRole)
            .status(UserStatus.ACTIVE)
            .otpVerified(true)
            .build();

        userRepository.save(testUser);
    }

    private User getTestUser() {
        return userRepository.findAll().stream()
            .filter(u -> u.getUsername().startsWith("2fatestuser"))
            .findFirst().orElseThrow();
    }

    private void enable2FA() {
        Feature feature = featureRepository.save(
            new Feature("TWO_FACTOR_AUTH", "Two-Factor Authentication", "Require OTP verification after login"));
        globalFlagRepository.save(new GlobalFlag(feature, true));
    }

    @Test
    @DisplayName("Login with 2FA disabled should return JWT directly")
    void login_2faDisabled_shouldReturnJwtDirectly() throws Exception {
        // Ensure 2FA is disabled (default - no global flag exists)
        assertFalse(globalFlagRepository.findByFeatureKey("TWO_FACTOR_AUTH")
            .map(GlobalFlag::isEnabled).orElse(false));

        User user = getTestUser();

        // Act & Assert
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Login successful"))
            .andExpect(jsonPath("$.data.token").exists())
            .andExpect(jsonPath("$.data.username").value(user.getUsername()))
            // Should NOT have otpRequired
            .andExpect(result -> {
                JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
                assertNull(data.get("otpRequired"), "otpRequired should not be present when 2FA is disabled");
            });
    }

    @Test
    @DisplayName("Login with 2FA enabled should return OTP pending response")
    void login_2faEnabled_shouldReturnOtpPending() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Act & Assert
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("OTP sent to your email"))
            .andExpect(jsonPath("$.data.otpRequired").value(true))
            .andExpect(jsonPath("$.data.email").exists())
            .andReturn();

        // Verify OTP was stored in DB
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(updatedUser.getOtpCode(), "OTP should be stored in DB");
        assertEquals(6, updatedUser.getOtpCode().length(), "OTP should be 6 digits");
        assertNotNull(updatedUser.getOtpExpiry(), "OTP expiry should be set");
        assertFalse(updatedUser.isOtpVerified(), "OTP should not be verified yet");
    }

    @Test
    @DisplayName("Login with 2FA enabled then verify OTP should return JWT")
    void login_2faEnabled_thenVerifyOtp_shouldReturnJwt() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login (triggers OTP)
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.otpRequired").value(true));

        // Step 2: Get OTP from DB
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        String otp = updatedUser.getOtpCode();
        assertNotNull(otp);

        // Step 3: Verify OTP
        Map<String, String> verifyRequest = new HashMap<>();
        verifyRequest.put("username", user.getUsername());
        verifyRequest.put("otp", otp);

        MvcResult verifyResult = mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Login successful"))
            .andExpect(jsonPath("$.data.token").exists())
            .andExpect(jsonPath("$.data.username").value(user.getUsername()))
            .andReturn();

        // Verify the token is valid
        String token = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
            .get("data").get("token").asText();
        assertNotNull(token);
        assertTrue(token.length() > 50);

        // Verify OTP was cleared from DB
        User finalUser = userRepository.findById(user.getId()).orElseThrow();
        assertNull(finalUser.getOtpCode(), "OTP should be cleared after verification");
        assertTrue(finalUser.isOtpVerified(), "OTP should be marked as verified");
    }

    @Test
    @DisplayName("Verify OTP with wrong code should return 400")
    void verifyOtp_wrongCode_shouldReturnBadRequest() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login (triggers OTP)
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());

        // Step 2: Verify with wrong OTP
        Map<String, String> verifyRequest = new HashMap<>();
        verifyRequest.put("username", user.getUsername());
        verifyRequest.put("otp", "999999");

        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid OTP. Please try again."));
    }

    @Test
    @DisplayName("Verify OTP without prior login should return 400")
    void verifyOtp_noPriorLogin_shouldReturnBadRequest() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Verify OTP without prior login
        Map<String, String> verifyRequest = new HashMap<>();
        verifyRequest.put("username", user.getUsername());
        verifyRequest.put("otp", "123456");

        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("No OTP generated. Please login again."));
    }

    @Test
    @DisplayName("Resend OTP should reuse same OTP if still valid")
    void resendOtp_shouldReuseSameOtpIfValid() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: Login (triggers OTP)
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk());

        User afterLogin = userRepository.findById(user.getId()).orElseThrow();
        String firstOtp = afterLogin.getOtpCode();

        // Step 2: Resend OTP immediately (should reuse same OTP)
        Map<String, String> resendRequest = new HashMap<>();
        resendRequest.put("username", user.getUsername());

        mockMvc.perform(post("/api/auth/resend-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resendRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("OTP resent to your email"));

        // Verify same OTP was reused
        User afterResend = userRepository.findById(user.getId()).orElseThrow();
        assertNotNull(afterResend.getOtpCode(), "OTP should still exist");
        assertEquals(firstOtp, afterResend.getOtpCode(), "Resend should reuse same OTP when still valid");

        // Step 3: Verify with the original OTP — should still work
        Map<String, String> verifyRequest = Map.of("username", user.getUsername(), "otp", firstOtp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("Repeated login calls should reuse same OTP (cooldown=0 in tests)")
    void login_repeatedCalls_shouldReuseOtp() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Step 1: First login — generates OTP
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "TestPass123!@#");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.otpRequired").value(true));

        String firstOtp = userRepository.findById(user.getId()).orElseThrow().getOtpCode();

        // Step 2: Second login — with cooldown=0 in tests, OTP is reused (same code sent again)
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.otpRequired").value(true));

        // Verify OTP was NOT regenerated (same code reused)
        String secondOtp = userRepository.findById(user.getId()).orElseThrow().getOtpCode();
        assertEquals(firstOtp, secondOtp, "OTP should remain the same when reused");

        // Step 3: Verify original OTP still works
        Map<String, String> verifyRequest = Map.of("username", user.getUsername(), "otp", firstOtp);
        mockMvc.perform(post("/api/auth/verify-otp")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    @DisplayName("Login with wrong password should not generate OTP even with 2FA enabled")
    void login_wrongPassword_shouldNotGenerateOtp() throws Exception {
        enable2FA();
        User user = getTestUser();

        // Act - Login with wrong password
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", user.getUsername());
        loginRequest.put("password", "WrongPassword123!");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isBadRequest());

        // Verify no OTP was generated
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertNull(updatedUser.getOtpCode(), "Wrong password should not generate OTP");
    }
}
