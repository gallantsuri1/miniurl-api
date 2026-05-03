package com.miniurl.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all DTO classes.
 */
@DisplayName("DTO Tests")
class DtoTest {

    @Test
    @DisplayName("LoginRequest - all constructors and methods")
    void loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");
        request.setPassword("pass");
        assertEquals("user", request.getUsername());
        assertEquals("pass", request.getPassword());
        
        request = new LoginRequest("user2", "pass2");
        assertEquals("user2", request.getUsername());
        assertEquals("pass2", request.getPassword());
    }

    @Test
    @DisplayName("JwtAuthRequest - all constructors and methods")
    void jwtAuthRequest() {
        JwtAuthRequest request = new JwtAuthRequest();
        request.setUsername("user");
        request.setPassword("pass");
        assertEquals("user", request.getUsername());
        assertEquals("pass", request.getPassword());
        
        request = new JwtAuthRequest("user2", "pass2");
        assertEquals("user2", request.getUsername());
        assertEquals("pass2", request.getPassword());
    }

    @Test
    @DisplayName("JwtAuthResponse - all constructors and methods")
    void jwtAuthResponse() {
        JwtAuthResponse response = new JwtAuthResponse();
        response.setToken("token");
        response.setTokenType("Bearer");
        response.setUserId(1L);
        response.setMustChangePassword(true);
        response.setFirstName("John");
        response.setLastName("Doe");
        
        assertEquals("token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(1L, response.getUserId());
        assertTrue(response.isMustChangePassword());
        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        
        // Test builder
        response = JwtAuthResponse.builder()
            .token("t").tokenType("tt").userId(2L).mustChangePassword(false)
            .firstName("Jane").lastName("Smith").build();
        assertEquals("t", response.getToken());
        assertEquals(2L, response.getUserId());
        assertEquals("Jane", response.getFirstName());
    }

    @Test
    @DisplayName("LoginResponse - all constructors and methods")
    void loginResponse() {
        LoginResponse response = new LoginResponse();
        response.setToken("token");
        response.setUsername("user");
        response.setUserId(1L);
        response.setMustChangePassword(true);
        response.setFirstName("John");
        response.setLastName("Doe");
        
        assertEquals("token", response.getToken());
        assertEquals("user", response.getUsername());
        assertTrue(response.isMustChangePassword());
        
        // Test builder
        response = LoginResponse.builder()
            .token("t").username("u").userId(2L).mustChangePassword(false)
            .firstName("Jane").lastName("Smith").build();
        assertEquals("t", response.getToken());
        assertEquals("u", response.getUsername());
    }

    @Test
    @DisplayName("CreateUrlRequest - all constructors and methods")
    void createUrlRequest() {
        CreateUrlRequest request = new CreateUrlRequest();
        request.setUrl("https://example.com");
        request.setAlias("mylink");
        assertEquals("https://example.com", request.getUrl());
        assertEquals("mylink", request.getAlias());

        request = new CreateUrlRequest("https://test.com", "customcode");
        assertEquals("https://test.com", request.getUrl());
        assertEquals("customcode", request.getAlias());
    }

    @Test
    @DisplayName("UrlResponse - all constructors and methods")
    void urlResponse() {
        UrlResponse response = new UrlResponse();
        response.setId(1L);
        response.setOriginalUrl("https://example.com");
        response.setShortCode("abc");
        response.setShortUrl("http://localhost/r/abc");
        response.setAccessCount(10L);
        response.setCreatedAt(java.time.LocalDateTime.now());
        
        assertEquals(1L, response.getId());
        assertEquals("abc", response.getShortCode());
        assertEquals(10L, response.getAccessCount());
        
        // Test builder
        response = UrlResponse.builder()
            .id(2L).originalUrl("https://test.com").shortCode("xyz")
            .shortUrl("http://localhost/r/xyz").accessCount(5L)
            .createdAt(java.time.LocalDateTime.now()).build();
        assertEquals(2L, response.getId());
        assertEquals("xyz", response.getShortCode());
    }

    @Test
    @DisplayName("SignupRequest - all constructors and methods")
    void signupRequest() {
        SignupRequest request = new SignupRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setUsername("johndoe");
        request.setPassword("Pass123");
        request.setInvitationToken("abc123token");

        assertEquals("John", request.getFirstName());
        assertEquals("johndoe", request.getUsername());
        assertEquals("Pass123", request.getPassword());
        assertEquals("abc123token", request.getInvitationToken());

        request = new SignupRequest("Jane", "Smith", "janesmith", "Pass456!", "xyz789token");
        assertEquals("Jane", request.getFirstName());
        assertEquals("janesmith", request.getUsername());
        assertEquals("Pass456!", request.getPassword());
        assertEquals("xyz789token", request.getInvitationToken());
    }

    @Test
    @DisplayName("UserResponse - all constructors and methods")
    void userResponse() {
        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setEmail("john@example.com");
        response.setUsername("johndoe");
        response.setRoleName("USER");
        response.setStatus("ACTIVE");
        response.setCreatedAt(java.time.LocalDateTime.now());
        response.setLastLogin(java.time.LocalDateTime.now());
        
        assertEquals(1L, response.getId());
        assertEquals("USER", response.getRoleName());
        assertEquals("ACTIVE", response.getStatus());
        
        // Test builder
        response = UserResponse.builder()
            .id(2L).firstName("Jane").lastName("Smith").email("jane@example.com")
            .username("janesmith").roleName("ADMIN").status("SUSPENDED")
            .createdAt(java.time.LocalDateTime.now()).lastLogin(java.time.LocalDateTime.now()).build();
        assertEquals(2L, response.getId());
        assertEquals("ADMIN", response.getRoleName());
    }

    @Test
    @DisplayName("ProfileUpdateRequest - all constructors and methods")
    void profileUpdateRequest() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("john@example.com");
        
        assertEquals("John", request.getFirstName());
        assertEquals("john@example.com", request.getEmail());
        
        request = new ProfileUpdateRequest("Jane", "Smith", "jane@example.com");
        assertEquals("Jane", request.getFirstName());
    }

    @Test
    @DisplayName("DeleteAccountRequest - all constructors and methods")
    void deleteAccountRequest() {
        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setPassword("pass");
        assertEquals("pass", request.getPassword());

        request = new DeleteAccountRequest(1L, "pass2");
        assertEquals("pass2", request.getPassword());
        assertEquals(1L, request.getUserId());
    }

    @Test
    @DisplayName("ForgotPasswordRequest - all constructors and methods")
    void forgotPasswordRequest() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("email@example.com");
        assertEquals("email@example.com", request.getEmail());
        
        request = new ForgotPasswordRequest("email2@example.com");
        assertEquals("email2@example.com", request.getEmail());
    }

    @Test
    @DisplayName("ResetPasswordRequest - all constructors and methods")
    void resetPasswordRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("token");
        request.setNewPassword("newpass");
        
        assertEquals("token", request.getToken());
        assertEquals("newpass", request.getNewPassword());
        
        request = new ResetPasswordRequest("token2", "newpass2");
        assertEquals("token2", request.getToken());
    }

    @Test
    @DisplayName("OtpVerificationRequest - all constructors and methods")
    void otpVerificationRequest() {
        OtpVerificationRequest request = new OtpVerificationRequest();
        request.setUsername("testuser");
        request.setOtp("123456");

        assertEquals("testuser", request.getUsername());
        assertEquals("123456", request.getOtp());

        request = new OtpVerificationRequest("user2", "654321");
        assertEquals("user2", request.getUsername());
        assertEquals("654321", request.getOtp());
    }

    @Test
    @DisplayName("ResendOtpRequest - all constructors and methods")
    void resendOtpRequest() {
        ResendOtpRequest request = new ResendOtpRequest();
        request.setUsername("testuser");
        assertEquals("testuser", request.getUsername());

        request = new ResendOtpRequest("user2");
        assertEquals("user2", request.getUsername());
    }

    @Test
    @DisplayName("ApiResponse - all constructors and methods")
    void apiResponse() {
        // Test success
        ApiResponse response = ApiResponse.success("Success");
        assertTrue(response.isSuccess());
        assertEquals("Success", response.getMessage());
        assertNull(response.getData());
        
        response = ApiResponse.success("Success", "data");
        assertEquals("data", response.getData());
        
        // Test error
        response = ApiResponse.error("Error");
        assertFalse(response.isSuccess());
        assertEquals("Error", response.getMessage());
        
        // Test builder
        response = ApiResponse.builder()
            .success(true).message("Test").data("TestData").build();
        assertTrue(response.isSuccess());
        assertEquals("Test", response.getMessage());
        assertEquals("TestData", response.getData());
    }

    @Test
    @DisplayName("FeatureFlagDTO - all constructors and methods")
    void featureFlagDto() {
        FeatureFlagDTO dto = new FeatureFlagDTO();
        dto.setId(1L);
        dto.setFeatureKey("KEY");
        dto.setFeatureName("Name");
        dto.setDescription("Description");
        dto.setEnabled(true);
        dto.setRoleId(2L);
        dto.setRoleName("USER");
        dto.setCreatedAt(java.time.LocalDateTime.now());
        dto.setUpdatedAt(java.time.LocalDateTime.now());

        assertEquals(1L, dto.getId());
        assertEquals("KEY", dto.getFeatureKey());
        assertTrue(dto.isEnabled());
        assertNotNull(dto.getDescription());
        assertEquals(2L, dto.getRoleId());
        assertEquals("USER", dto.getRoleName());

        dto = new FeatureFlagDTO(2L, 5L, "KEY2", "Name2", "Desc2", false,
            1L, "ADMIN",
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        assertEquals(2L, dto.getId());
        assertEquals(5L, dto.getFeatureId());
        assertFalse(dto.isEnabled());
        assertEquals(1L, dto.getRoleId());
        assertEquals("ADMIN", dto.getRoleName());

        // Test toString
        assertNotNull(dto.toString());
    }

    @Test
    @DisplayName("GlobalFlagDTO - all constructors and methods")
    void globalFlagDto() {
        GlobalFlagDTO dto = new GlobalFlagDTO();
        dto.setId(1L);
        dto.setFeatureKey("USER_SIGNUP");
        dto.setFeatureName("User Sign Up");
        dto.setDescription("Allow new user registration");
        dto.setEnabled(true);
        dto.setCreatedAt(java.time.LocalDateTime.now());
        dto.setUpdatedAt(java.time.LocalDateTime.now());

        assertEquals(1L, dto.getId());
        assertEquals("USER_SIGNUP", dto.getFeatureKey());
        assertTrue(dto.isEnabled());

        dto = new GlobalFlagDTO(2L, 1L, "KEY2", "Name2", "Desc2", false,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        assertEquals(2L, dto.getId());
        assertEquals(1L, dto.getFeatureId());
        assertFalse(dto.isEnabled());

        // Test toString
        assertNotNull(dto.toString());
    }

    @Test
    @DisplayName("LoginOtpResponse - all constructors and methods")
    void loginOtpResponse() {
        // Test default constructor + setters
        LoginOtpResponse response = new LoginOtpResponse();
        response.setMessage("OTP sent to your email");
        response.setOtpRequired(true);
        response.setEmail("j***e@example.com");

        assertEquals("OTP sent to your email", response.getMessage());
        assertTrue(response.isOtpRequired());
        assertEquals("j***e@example.com", response.getEmail());

        // Test convenience constructor
        response = new LoginOtpResponse("Login with OTP", "user@test.com");
        assertEquals("Login with OTP", response.getMessage());
        assertTrue(response.isOtpRequired());
        assertEquals("user@test.com", response.getEmail());
    }
}
