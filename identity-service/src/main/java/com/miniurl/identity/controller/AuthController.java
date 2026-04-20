package com.miniurl.identity.controller;

import com.miniurl.dto.*;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.identity.service.AuthService;
import com.miniurl.identity.service.EmailInviteService;
import com.miniurl.identity.service.JwtService;
import com.miniurl.identity.service.TokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final EmailInviteService emailInviteService;
    private final JwtService jwtService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        JwtAuthResponse response = authService.signup(request);
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", response));
    }

    @GetMapping("/verify-email-invite")
    public ResponseEntity<ApiResponse<Void>> verifyEmailInvite(@RequestParam String token) {
        authService.verifyEmailInvite(token);
        return ResponseEntity.ok(ApiResponse.success("Email invitation verified successfully"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset link sent to email"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        JwtAuthResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP resent successfully"));
    }

    @PostMapping("/delete-account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(request.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
    }
}
