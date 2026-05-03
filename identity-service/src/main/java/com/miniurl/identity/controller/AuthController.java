package com.miniurl.identity.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.JwtAuthRequest;
import com.miniurl.dto.JwtAuthResponse;
import com.miniurl.dto.LoginRequest;
import com.miniurl.dto.LoginOtpResponse;
import com.miniurl.dto.LoginResponse;
import com.miniurl.dto.OtpVerificationRequest;
import com.miniurl.dto.ResendOtpRequest;
import com.miniurl.dto.SignupRequest;
import com.miniurl.dto.DeleteAccountRequest;
import com.miniurl.dto.ForgotPasswordRequest;
import com.miniurl.dto.ResetPasswordRequest;
import com.miniurl.identity.entity.User;
import com.miniurl.identity.entity.UserPrincipal;
import com.miniurl.identity.exception.AccountLockedException;
import com.miniurl.identity.exception.ResourceNotFoundException;
import com.miniurl.identity.exception.UnauthorizedException;
import com.miniurl.identity.repository.UserRepository;
import com.miniurl.identity.service.AuthService;
import com.miniurl.identity.service.EmailInviteService;
import com.miniurl.identity.service.JwtService;
import com.miniurl.identity.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final TokenService tokenService;
    private final EmailInviteService emailInviteService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.registerUser(
            request.getFirstName(),
            request.getLastName(),
            request.getUsername(),
            request.getPassword(),
            request.getInvitationToken()
        );
        String jwt = jwtService.generateToken(new UserPrincipal(user));
        JwtAuthResponse response = new JwtAuthResponse(
            jwt,
            user.getUsername(),
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.isMustChangePassword()
        );
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", response));
    }

    @GetMapping("/verify-email-invite")
    public ResponseEntity<ApiResponse<Void>> verifyEmailInvite(@RequestParam String token) {
        authService.verifyEmailInviteToken(token);
        return ResponseEntity.ok(ApiResponse.success("Email invitation verified successfully"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyResetPasswordToken(token);
        return ResponseEntity.ok(ApiResponse.success("Reset token is valid"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Password reset link sent to email"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginOtpResponse>> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
            .or(() -> userRepository.findByEmail(request.getUsername()))
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check account lockout before password validation
        if (!user.isLoginAttemptAllowed()) {
            long remainingSeconds = 0;
            if (user.getLockoutTime() != null) {
                remainingSeconds = Duration.between(LocalDateTime.now(), user.getLockoutTime()).getSeconds();
            }
            log.warn("Login blocked: account locked for user: {}", user.getUsername());
            throw new AccountLockedException(
                "Account is temporarily locked. Please try again in " + remainingSeconds + " seconds.",
                remainingSeconds);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // Increment failed attempts on wrong password
            user.incrementFailedLoginAttempts();
            userRepository.save(user);
            log.warn("Failed login attempt {} for user: {}", user.getFailedLoginAttempts(), user.getUsername());
            throw new UnauthorizedException("Invalid credentials");
        }

        // Reset failed attempts on successful password validation
        user.resetFailedLoginAttempts();
        userRepository.save(user);

        authService.sendLoginOtp(user);
        LoginOtpResponse response = new LoginOtpResponse("OTP sent to your email", user.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        // Check lockout before OTP verification (prevents bypassing lockout)
        User user = userRepository.findByUsername(request.getUsername())
            .or(() -> userRepository.findByEmail(request.getUsername()))
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isLoginAttemptAllowed()) {
            long remainingSeconds = 0;
            if (user.getLockoutTime() != null) {
                remainingSeconds = Duration.between(LocalDateTime.now(), user.getLockoutTime()).getSeconds();
            }
            throw new AccountLockedException(
                "Account is temporarily locked. Please try again in " + remainingSeconds + " seconds.",
                remainingSeconds);
        }

        user = authService.verifyLoginOtp(request.getUsername(), request.getOtp());
        String jwt = jwtService.generateToken(new UserPrincipal(user));
        JwtAuthResponse response = new JwtAuthResponse(
            jwt,
            user.getUsername(),
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.isMustChangePassword()
        );
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        // Check lockout before resending OTP
        User user = userRepository.findByUsername(request.getUsername())
            .or(() -> userRepository.findByEmail(request.getUsername()))
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isLoginAttemptAllowed()) {
            long remainingSeconds = 0;
            if (user.getLockoutTime() != null) {
                remainingSeconds = Duration.between(LocalDateTime.now(), user.getLockoutTime()).getSeconds();
            }
            throw new AccountLockedException(
                "Account is temporarily locked. Please try again in " + remainingSeconds + " seconds.",
                remainingSeconds);
        }

        authService.resendLoginOtp(request.getUsername());
        return ResponseEntity.ok(ApiResponse.success("OTP resent successfully"));
    }

    @PostMapping("/delete-account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        authService.deleteAccount(user.getId(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
    }
}
