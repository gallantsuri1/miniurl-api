package com.miniurl.controller;

import com.miniurl.dto.*;
import com.miniurl.entity.User;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.repository.UserRepository;
import com.miniurl.service.AuthService;
import com.miniurl.service.CustomUserDetailsService;
import com.miniurl.service.GlobalFlagService;
import com.miniurl.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user authentication and account management")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final AuthService authService;
    private final GlobalFlagService globalFlagService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            UserRepository userRepository,
            CustomUserDetailsService userDetailsService,
            AuthService authService,
            GlobalFlagService globalFlagService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.authService = authService;
        this.globalFlagService = globalFlagService;
    }

    @PostMapping("/signup")
    @Operation(
        summary = "User signup",
        description = """
            Register a new user account using an invitation token.

            **Invitation Flow:**
            1. Admin sends invite to user via email using /api/admin/email-invites/send
            2. User receives email with invitation link containing invitationToken
            3. User clicks link: http://localhost:3000/signup?invite=<invitationToken>
            4. UI validates token using /api/auth/verify-email-invite?token=<invitationToken>
            5. UI shows signup form with First Name, Last Name, Username, Password fields
            6. User submits form with invitationToken to this endpoint

            **Validation Rules (NIST SP 800-63B compliant):**
            - **firstName/lastName**: 1-100 chars, letters + spaces + hyphens + apostrophes only
            - **username**: 3-50 chars, must start with letter, alphanumeric + underscore only
            - **password**: Min 8 chars, no complexity requirements, must not be a common password
            - Reserved usernames (admin, root, system, etc.) are rejected

            **Process:**
            1. Validates invitation token (required, extracted from request)
            2. Extracts email from the invitation token
            3. Validates input (field formats, password strength, reserved usernames)
            4. Checks if email/username already exists (blocks if exists)
            5. Creates user with provided password
            6. Marks email as verified (no verification needed - admin already sent invite)
            7. Marks invitation as accepted
            8. Sends congratulations email

            **Note:**
            - Email is NOT provided in the request - it is extracted from the invitation token.
            - Email verification is NOT required - admin invite proves email ownership.
            - User can login immediately after registration.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registration successful, congratulations email sent",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Successfully registered!"
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or validation failure",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid First Name", value = """
                        {
                          "success": false,
                          "message": "First name may only contain letters, spaces, hyphens, and apostrophes"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Username", value = """
                        {
                          "success": false,
                          "message": "Username must start with a letter and contain only letters, numbers, and underscores"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Weak Password", value = """
                        {
                          "success": false,
                          "message": "Password must be at least 8 characters long"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Common Password", value = """
                        {
                          "success": false,
                          "message": "Password is too common. Please choose a stronger password."
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Reserved Username", value = """
                        {
                          "success": false,
                          "message": "Username 'admin' is reserved. Please choose another."
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Token", value = """
                        {
                          "success": false,
                          "message": "Invalid or expired invitation token: This invite has expired"
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> signup(@Valid @RequestBody SignupRequest request) {
        try {
            authService.registerUser(
                request.getFirstName(),
                request.getLastName(),
                request.getUsername(),
                request.getPassword(),
                request.getInvitationToken()
            );

            return ResponseEntity.ok(ApiResponse.success("Successfully registered!"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/verify-email-invite")
    @Operation(
        summary = "Verify email invitation token before signup",
        description = """
            Verify email invitation token before user registration.

            **Invitation Flow:**
            1. Admin sends invite to user via email
            2. User clicks invite link containing invitation token
            3. This endpoint validates the invitation token BEFORE showing signup form

            **Process:**
            1. Validates invitation token against email_invites table
            2. Checks if token exists, not expired, not revoked, not used
            3. NO user is created at this stage
            4. User creation happens only when signup form is submitted

            **Note:** This endpoint does NOT create users or return authentication tokens.
            It only validates that the invitation is valid for signup.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation token is valid",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Invitation token is valid"
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired invitation token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Token", value = """
                        {
                          "success": false,
                          "message": "Invalid or expired invitation token: This invite has expired"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Revoked Token", value = """
                        {
                          "success": false,
                          "message": "Invalid or expired invitation token: This invite has been revoked"
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> verifyEmailInvite(
            @Parameter(description = "Invitation token from the email invite link",
                       required = true,
                       example = "0OjjT8du7Smtxx1VbOc9s2e6B9FSGv78")
            @RequestParam String token) {
        try {
            authService.verifyEmailInviteToken(token);
            return ResponseEntity.ok(ApiResponse.success("Invitation token is valid"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    @Operation(
        summary = "Verify password reset token",
        description = """
            Verify password reset token before allowing password reset.

            **Password Reset Flow:**
            1. User requests password reset via /api/auth/forgot-password
            2. System creates reset token and sends email
            3. User clicks reset link containing token
            4. This endpoint validates the reset token BEFORE showing reset form
            5. Returns success if token is valid

            **Process:**
            1. Validates reset token against verification_tokens table (PASSWORD_RESET type)
            2. Checks if token exists, not expired, not used
            3. User can then submit new password via /api/auth/reset-password

            **Note:** This endpoint does NOT reset passwords.
            It only validates that the reset token is valid.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reset token is valid",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Reset token is valid"
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired reset token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Token", value = """
                    {
                      "success": false,
                      "message": "Invalid or expired reset token"
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse> verifyEmail(
            @Parameter(description = "Password reset token from the reset email link",
                       required = true,
                       example = "aB3dE5fG7hI9jK1lM3nO5pQ7rS9tU1vW3xY5zA7bC9dE1fG3hI5jK7lM9nO1pQ")
            @RequestParam String token) {
        try {
            authService.verifyResetPasswordToken(token);
            return ResponseEntity.ok(ApiResponse.success("Reset token is valid"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    @Operation(
        summary = "Request password reset",
        description = """
            Send password reset link to user's email.

            **Security:**
            - Email bombing protection (max 3 requests per hour per email)
            - IP-based rate limiting (max 60 requests per hour)
            - Doesn't reveal if email exists (prevents enumeration)
            - Reset token expires in 15 minutes

            **Process:**
            1. User enters email address
            2. System generates secure reset token
            3. Reset link sent via email
            4. User clicks link to reset password

            **Rate Limits:**
            - Per-email: 3 requests per hour
            - Per-IP: 60 requests per hour
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset link sent (if email exists)",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Email Exists", value = """
                        {
                          "success": true,
                          "message": "Password reset link sent to your email"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Email Not Found", value = """
                        {
                          "success": true,
                          "message": "If the email exists, a reset link has been sent"
                        }
                        """)
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email format",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            authService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(ApiResponse.success("Password reset link sent to your email"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.ok(ApiResponse.success("If the email exists, a reset link has been sent"));
        }
    }

    @PostMapping("/reset-password")
    @Operation(
        summary = "Reset password",
        description = """
            Reset password using the token from email.
            
            **Requirements:**
            - Valid reset token from email
            - New password must meet strength requirements:
              * Minimum 12 characters
              * At least one uppercase letter
              * At least one lowercase letter
              * At least one number
              * At least one special character
            
            **After Reset:**
            - Password is immediately updated
            - All existing sessions are invalidated
            - User can login with new password
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Password reset successfully"
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Token", value = """
                        {
                          "success": false,
                          "message": "Invalid or expired reset token"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Weak Password", value = """
                        {
                          "success": false,
                          "message": "Password must be at least 12 characters long"
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = """
            Authenticate user with credentials.

            **Two-Factor Authentication (2FA):**
            - If 2FA is enabled (TWO_FACTOR_AUTH global flag), an OTP is sent to the user's email
            - The response will include `otpRequired: true` and the user's masked email
            - The user must then call POST /api/auth/verify-otp with the OTP to complete login
            - If 2FA is disabled, the JWT token is returned immediately

            **Account Lockout Protection:**
            - Account locks after 5 failed login attempts
            - Lockout duration: 5 minutes
            - Automatic reset after lockout expires

            **Authentication:**
            - Accepts username or email
            - Returns JWT token (if 2FA disabled) or OTP pending response (if 2FA enabled)
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful or OTP sent",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "2FA Disabled - Direct Login", value = """
                        {
                          "success": true,
                          "message": "Login successful",
                          "data": {
                            "token": "eyJhbGciOiJIUzUxMiJ9...",
                            "username": "johndoe",
                            "userId": 123,
                            "mustChangePassword": false,
                            "firstName": "John",
                            "lastName": "Doe"
                          }
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "2FA Enabled - OTP Required", value = """
                        {
                          "success": true,
                          "message": "OTP sent to your email",
                          "data": {
                            "otpRequired": true,
                            "email": "j***e@example.com"
                          }
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "2FA Cooldown (repeat login within 30s)", value = """
                        {
                          "success": true,
                          "message": "OTP already sent. Please wait 30 seconds before trying again.",
                          "data": {
                            "otpRequired": true,
                            "email": "j***e@example.com"
                          }
                        }
                        """)
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid credentials or account locked",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Credentials", value = """
                        {
                          "success": false,
                          "message": "Invalid username or password"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Account Locked", value = """
                        {
                          "success": false,
                          "message": "Account temporarily locked due to too many failed attempts. Please try again in 5 minutes."
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

            if (user.isAccountLocked()) {
                logger.warn("Login attempt on locked account: {}", request.getUsername());
                return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Account temporarily locked due to too many failed attempts. Please try again in 5 minutes."));
            }

            if (user.isLockoutExpired()) {
                user.resetFailedLoginAttempts();
                userRepository.save(user);
            }

            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            user.resetFailedLoginAttempts();
            userRepository.save(user);
            authService.updateLastLogin(user.getId());

            // Check if 2FA is enabled
            boolean twoFactorEnabled = globalFlagService.isTwoFactorAuthEnabled();

            if (twoFactorEnabled) {
                // Reuse existing valid OTP or generate/resend via service
                try {
                    authService.sendLoginOtp(user);
                } catch (com.miniurl.exception.RateLimitCooldownException e) {
                    String maskedEmail = maskEmail(user.getEmail());
                    LoginOtpResponse otpResponse = new LoginOtpResponse("Please wait before requesting a new OTP.", maskedEmail);
                    return ResponseEntity.ok(ApiResponse.success("OTP already sent. Please wait 30 seconds before trying again.", otpResponse));
                }

                String maskedEmail = maskEmail(user.getEmail());
                LoginOtpResponse otpResponse = new LoginOtpResponse("OTP sent to your email", maskedEmail);
                return ResponseEntity.ok(ApiResponse.success("OTP sent to your email", otpResponse));
            }

            // 2FA disabled - return JWT directly
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtUtil.generateToken(userDetails, user.getTokenVersion());

            LoginResponse response = LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId())
                .mustChangePassword(user.isMustChangePassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();

            return ResponseEntity.ok(ApiResponse.success("Login successful", response));

        } catch (LockedException e) {
            logger.warn("Login attempt on locked account: {}", request.getUsername());
            return ResponseEntity.badRequest().body(ApiResponse.error(
                "Account temporarily locked due to too many failed attempts. Please try again in 5 minutes."));
        } catch (BadCredentialsException e) {
            try {
                User user = userRepository.findByUsername(request.getUsername())
                    .or(() -> userRepository.findByEmail(request.getUsername()))
                    .orElse(null);
                if (user != null && !user.isAccountLocked()) {
                    user.incrementFailedLoginAttempts();
                    userRepository.save(user);
                    if (user.isAccountLocked()) {
                        logger.warn("Account locked after {} failed attempts: {}",
                            user.getFailedLoginAttempts(), request.getUsername());
                        return ResponseEntity.badRequest().body(ApiResponse.error(
                            "Your account is locked due to too many failed login attempts. Please try again after 5 minutes."));
                    }
                }
            } catch (Exception ex) {
                logger.error("Failed to update failed login attempts", ex);
            }
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid username or password"));
        }
    }

    @PostMapping("/verify-otp")
    @Operation(
        summary = "Verify OTP for 2FA login",
        description = """
            Verify the OTP sent during login to complete authentication.

            **Flow:**
            1. User calls POST /api/auth/login with credentials
            2. If 2FA is enabled, OTP is sent to user's email
            3. User calls this endpoint with their username (or email) and OTP
            4. If OTP is valid, JWT token is returned
            5. If OTP is invalid or expired, error is returned

            **Note:** The `username` field accepts either the username or email —
            use the same identifier you used during login.

            **OTP Expiry:** 10 minutes (configurable via app.otp.expiry-minutes)

            **Rate Limits:**
            - Per-email/username: 5 requests per 5 minutes
            - Per-IP: 30 requests per 15 minutes
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP verified, login complete",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Login successful",
                      "data": {
                        "token": "eyJhbGciOiJIUzUxMiJ9...",
                        "username": "johndoe",
                        "userId": 123,
                        "mustChangePassword": false,
                        "firstName": "John",
                        "lastName": "Doe"
                      }
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid OTP", value = """
                        {
                          "success": false,
                          "message": "Invalid OTP. Please try again."
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Expired OTP", value = """
                        {
                          "success": false,
                          "message": "OTP has expired. Please login again."
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "No OTP", value = """
                        {
                          "success": false,
                          "message": "No OTP generated. Please login again."
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        try {
            User user = authService.verifyLoginOtp(request.getUsername(), request.getOtp());

            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
            String token = jwtUtil.generateToken(userDetails, user.getTokenVersion());

            LoginResponse response = LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .userId(user.getId())
                .mustChangePassword(user.isMustChangePassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();

            return ResponseEntity.ok(ApiResponse.success("Login successful", response));

        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    @Operation(
        summary = "Resend OTP for 2FA login",
        description = """
            Resend the OTP if the user didn't receive it or it expired.

            **Behavior:**
            - If the existing OTP is still valid (not expired): **same OTP is resent**
            - If the existing OTP is expired: **new OTP is generated**
            - 30-second cooldown between consecutive OTP sends

            **Rate Limits:**
            - Per-email/username: 5 requests per 5 minutes
            - Per-IP: 30 requests per 15 minutes

            **Requirements:**
            - User must have initiated login (POST /api/auth/login) first
            - Username or email must match the one used during login
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP resent",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "OTP Resent", value = """
                        {
                          "success": true,
                          "message": "OTP resent to your email"
                        }
                        """)
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "User Not Found", value = """
                        {
                          "success": false,
                          "message": "User not found: nonexistent"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "No OTP Generated", value = """
                        {
                          "success": false,
                          "message": "No OTP generated. Please login again."
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Resend Cooldown", value = """
                        {
                          "success": false,
                          "message": "Please wait before requesting a new OTP."
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        try {
            authService.resendLoginOtp(request.getUsername());
            return ResponseEntity.ok(ApiResponse.success("OTP resent to your email"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (com.miniurl.exception.RateLimitCooldownException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Mask email address for privacy in OTP response.
     * Example: "johndoe@example.com" -> "j***e@example.com"
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 5) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***";
        return email.charAt(0) + "***" + email.charAt(atIndex - 1) + email.substring(atIndex);
    }

    @PostMapping("/change-password")
    @Operation(
        summary = "Change password",
        description = "Change user password (requires authentication)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid old password"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid authorization header"));
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            authService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete-account")
    @Operation(
        summary = "Delete account",
        description = "Permanently delete user account (requires authentication)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid password"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid authorization header"));
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            authService.deleteAccount(user.getId(), request.getPassword());
            return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/check-password-change")
    @Operation(
        summary = "Check password change required",
        description = "Check if user must change password (requires authentication)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password change status retrieved",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse> checkPasswordChange(
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid authorization header"));
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("mustChangePassword", user.isMustChangePassword());

            return ResponseEntity.ok(ApiResponse.success("Password change status retrieved", response));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}
