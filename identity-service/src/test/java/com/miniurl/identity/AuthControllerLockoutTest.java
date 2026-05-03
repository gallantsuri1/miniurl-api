package com.miniurl.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniurl.dto.LoginRequest;
import com.miniurl.dto.OtpVerificationRequest;
import com.miniurl.dto.ResendOtpRequest;
import com.miniurl.identity.config.GlobalExceptionHandler;
import com.miniurl.identity.controller.AuthController;
import com.miniurl.identity.entity.User;
import com.miniurl.identity.entity.UserStatus;
import com.miniurl.identity.repository.UserRepository;
import com.miniurl.identity.service.AuthService;
import com.miniurl.identity.service.EmailInviteService;
import com.miniurl.identity.service.JwtService;
import com.miniurl.identity.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("AuthController Lockout Tests")
class AuthControllerLockoutTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private EmailInviteService emailInviteService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    private User activeUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .username("testuser")
                .password("encodedPassword")
                .status(UserStatus.ACTIVE)
                .build();

        lockedUser = User.builder()
                .id(2L)
                .firstName("Locked")
                .lastName("User")
                .email("locked@example.com")
                .username("lockeduser")
                .password("encodedPassword")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(5)
                .lockoutTime(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    @Nested
    @DisplayName("Login endpoint lockout")
    class LoginLockout {

        @Test
        @DisplayName("should return 423 when account is locked")
        void shouldReturnLockedWhenAccountLocked() throws Exception {
            when(userRepository.findByUsername("lockeduser"))
                    .thenReturn(Optional.of(lockedUser));
            when(userRepository.findByEmail("lockeduser"))
                    .thenReturn(Optional.empty());

            LoginRequest request = new LoginRequest("lockeduser", "password");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Account is temporarily locked")));

            // Verify no OTP was sent
            verify(authService, never()).sendLoginOtp(any());
        }

        @Test
        @DisplayName("should increment failed attempts on wrong password")
        void shouldIncrementFailedAttemptsOnWrongPassword() throws Exception {
            when(userRepository.findByUsername("testuser"))
                    .thenReturn(Optional.of(activeUser));
            when(userRepository.findByEmail("testuser"))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.matches("wrongpassword", "encodedPassword"))
                    .thenReturn(false);

            LoginRequest request = new LoginRequest("testuser", "wrongpassword");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            // Verify failed attempts were incremented and saved
            verify(userRepository).save(activeUser);
            assertEquals(1, activeUser.getFailedLoginAttempts());
        }

        @Test
        @DisplayName("should reset failed attempts on successful password")
        void shouldResetFailedAttemptsOnSuccess() throws Exception {
            // Pre-set some failed attempts
            activeUser.incrementFailedLoginAttempts();
            activeUser.incrementFailedLoginAttempts();
            assertEquals(2, activeUser.getFailedLoginAttempts());

            when(userRepository.findByUsername("testuser"))
                    .thenReturn(Optional.of(activeUser));
            when(userRepository.findByEmail("testuser"))
                    .thenReturn(Optional.empty());
            when(passwordEncoder.matches("correctpassword", "encodedPassword"))
                    .thenReturn(true);

            LoginRequest request = new LoginRequest("testuser", "correctpassword");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Verify failed attempts were reset
            verify(userRepository).save(activeUser);
            assertEquals(0, activeUser.getFailedLoginAttempts());
        }
    }

    @Nested
    @DisplayName("OTP verification lockout")
    class OtpVerificationLockout {

        @Test
        @DisplayName("should return 423 when verifying OTP on locked account")
        void shouldReturnLockedWhenVerifyingOtpOnLockedAccount() throws Exception {
            when(userRepository.findByUsername("lockeduser"))
                    .thenReturn(Optional.of(lockedUser));
            when(userRepository.findByEmail("lockeduser"))
                    .thenReturn(Optional.empty());

            OtpVerificationRequest request = new OtpVerificationRequest("lockeduser", "123456");

            mockMvc.perform(post("/api/auth/verify-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Account is temporarily locked")));

            // Verify OTP was not verified
            verify(authService, never()).verifyLoginOtp(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Resend OTP lockout")
    class ResendOtpLockout {

        @Test
        @DisplayName("should return 423 when resending OTP on locked account")
        void shouldReturnLockedWhenResendingOtpOnLockedAccount() throws Exception {
            when(userRepository.findByUsername("lockeduser"))
                    .thenReturn(Optional.of(lockedUser));
            when(userRepository.findByEmail("lockeduser"))
                    .thenReturn(Optional.empty());

            ResendOtpRequest request = new ResendOtpRequest("lockeduser");

            mockMvc.perform(post("/api/auth/resend-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Account is temporarily locked")));

            // Verify OTP was not resent
            verify(authService, never()).resendLoginOtp(anyString());
        }
    }
}
