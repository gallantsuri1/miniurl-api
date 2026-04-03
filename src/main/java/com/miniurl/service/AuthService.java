package com.miniurl.service;

import com.miniurl.entity.EmailInvite;
import com.miniurl.entity.Role;
import com.miniurl.entity.User;
import com.miniurl.entity.UserStatus;
import com.miniurl.entity.VerificationToken;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.repository.RoleRepository;
import com.miniurl.repository.UserRepository;
import com.miniurl.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final String ALPHANUMERIC = "0123456789";
    private static final int OTP_LENGTH = 6;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final EmailInviteService emailInviteService;

    @Value("${app.otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    // Email bombing protection - track password reset requests per email
    private final ConcurrentMap<String, LocalDateTime> passwordResetRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> signupRequests = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository,
                      RoleRepository roleRepository,
                      VerificationTokenRepository tokenRepository,
                      EmailService emailService,
                      TokenService tokenService,
                      PasswordEncoder passwordEncoder,
                      EmailInviteService emailInviteService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.emailInviteService = emailInviteService;
    }

    /**
     * Validate password strength
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            throw new UnauthorizedException("Password cannot be empty");
        }

        if (password.length() < 8) {
            throw new UnauthorizedException("Password must be at least 8 characters long");
        }
    }

    /**
     * Register a new user with email verification and signup rate limiting
     * @param firstName User's first name
     * @param lastName User's last name
     * @param username Desired username
     * @param password User's password (will be validated for strength)
     * @param invitationToken Invitation token for invited users (required)
     * @return Registered user
     */
    @Transactional
    public User registerUser(String firstName, String lastName, String username, String password, String invitationToken) {
        // Validate invitation token and extract email
        if (invitationToken == null || invitationToken.trim().isEmpty()) {
            throw new UnauthorizedException("Invitation token is required");
        }

        EmailInvite invite = emailInviteService.validateInvite(invitationToken);
        String email = invite.getEmail();
        logger.info("Valid invitation token for email: {}", email);

        // Signup rate limiting - max 5 requests per hour per email
        LocalDateTime lastSignup = signupRequests.get(email.toLowerCase());
        if (lastSignup != null && LocalDateTime.now().isBefore(lastSignup.plusMinutes(12))) {
            throw new UnauthorizedException("Too many signup attempts. Please try again later.");
        }

        // Validate password strength
        validatePasswordStrength(password);

        // Check if email already exists
        Optional<User> existingUser = userRepository.findByEmail(email);

        // If user exists, check status
        if (existingUser.isPresent()) {
            UserStatus status = existingUser.get().getStatus();

            // Block suspended users from re-registering
            if (status == UserStatus.SUSPENDED) {
                throw new UnauthorizedException("Account suspended!");
            }

            // Block active verified users
            if (existingUser.get().isOtpVerified() && status == UserStatus.ACTIVE) {
                throw new UnauthorizedException("Email already registered");
            }
        }

        // Check if username already exists
        Optional<User> existingByUsername = userRepository.findByUsername(username);
        if (existingByUsername.isPresent()) {
            UserStatus status = existingByUsername.get().getStatus();

            // Block suspended users
            if (status == UserStatus.SUSPENDED) {
                throw new UnauthorizedException("Account suspended!");
            }

            // Block active users
            if (status == UserStatus.ACTIVE) {
                throw new UnauthorizedException("Username already taken");
            }
        }

        // Get USER role
        Role userRole = roleRepository.findByName("USER")
            .orElseThrow(() -> new RuntimeException("USER role not found"));

        // Determine if this is a returning user (soft-deleted) or new user
        boolean isReturningUser = existingUser.isPresent() && existingUser.get().getStatus() == UserStatus.DELETED;
        boolean isInvitedUser = true;

        // Create or reactivate user
        User user;
        if (existingUser.isPresent()) {
            // Reactivate deleted user
            user = existingUser.get();
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setStatus(UserStatus.ACTIVE);
            user.setOtpVerified(true); // Email already verified via invite
            user.setMustChangePassword(false); // User set their own password

            // Invalidate ALL existing tokens for this user (used and unused)
            tokenService.invalidateAllUserTokens(user.getId(), VerificationToken.TYPE_EMAIL_VERIFICATION);
            tokenService.invalidateAllUserTokens(user.getId(), VerificationToken.TYPE_PASSWORD_RESET);
        } else {
            // Create new user - invited users don't need email verification
            user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(userRole)
                .otpVerified(true) // Email already verified via admin invite
                .mustChangePassword(false) // User set their own password
                .status(UserStatus.ACTIVE)
                .build();
        }

        userRepository.save(user);

        // Track signup for rate limiting
        signupRequests.put(email.toLowerCase(), LocalDateTime.now());

        logger.info("User registered: {} ({}) - Returning: {}, Invited: {}", username, email, isReturningUser, isInvitedUser);

        // If invited user, accept the invitation after successful registration
        if (isInvitedUser) {
            try {
                emailInviteService.acceptInvite(invitationToken);
                logger.info("Invitation accepted for invited user: {} ({})", username, email);
            } catch (UnauthorizedException e) {
                logger.warn("Failed to accept invitation for user {}: {}", username, e.getMessage());
                // Don't fail the registration, just log the error
            }
        }

        // Send congratulations email (NO verification email needed for invited users)
        try {
            emailService.sendRegistrationCongratulationsEmail(email, firstName);
            logger.info("Congratulations email sent to: {} ({})", email, username);
        } catch (Exception e) {
            logger.warn("Failed to send congratulations email to {}: {}", email, e.getMessage());
            // Don't fail the registration, just log the error
        }

        // Cleanup old entries
        cleanupOldRateLimitEntries();

        return user;
    }

    /**
     * Verify email invitation token before registration.
     * This endpoint only validates the token - it does NOT create or modify any user.
     * @param token Invitation token from the email invite link
     * @return Email from the invite if token is valid
     * @throws UnauthorizedException if token is invalid, expired, or revoked
     */
    @Transactional(readOnly = true)
    public String verifyEmailInviteToken(String token) {
        try {
            EmailInvite invite = emailInviteService.validateInvite(token);
            logger.info("Invitation token validated for email: {}", invite.getEmail());
            return invite.getEmail();
        } catch (UnauthorizedException e) {
            throw e;
        }
    }

    /**
     * Verify password reset token.
     * Validates token against verification_tokens table (PASSWORD_RESET type).
     * @param token Password reset token
     * @return User email if token is valid
     * @throws UnauthorizedException if token is invalid, expired, or used
     */
    @Transactional(readOnly = true)
    public String verifyResetPasswordToken(String token) {
        Optional<VerificationToken> tokenOpt = tokenService.validateToken(token);

        if (tokenOpt.isEmpty()) {
            throw new UnauthorizedException("Invalid or expired reset token");
        }

        VerificationToken verificationToken = tokenOpt.get();
        User user = verificationToken.getUser();

        logger.info("Reset token validated for user: {}", user.getEmail());
        return user.getEmail();
    }

    /**
     * Set password after email verification (by user ID, not token)
     */
    @Transactional
    public void setPassword(Long userId, String newPassword, boolean isNewUser) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Validate new password strength
        validatePasswordStrength(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.incrementTokenVersion();  // Invalidate any existing tokens
        userRepository.save(user);

        // Send appropriate welcome email
        if (isNewUser) {
            emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
            logger.info("Welcome email sent to new user: {}", user.getUsername());
        } else {
            emailService.sendWelcomeBackEmail(user.getEmail(), user.getUsername());
            logger.info("Welcome back email sent to returning user: {}", user.getUsername());
        }
    }

    /**
     * Request password reset with email bombing protection
     */
    @Transactional
    public void requestPasswordReset(String email) {
        // Email bombing protection - max 3 requests per hour
        LocalDateTime lastRequest = passwordResetRequests.get(email.toLowerCase());
        if (lastRequest != null && LocalDateTime.now().isBefore(lastRequest.plusMinutes(20))) {
            logger.warn("Password reset rate limit exceeded for: {}", email);
            // Return silently to avoid revealing if email exists or rate limit hit
            return;
        }
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        // Create password reset token
        VerificationToken resetToken = tokenService.createPasswordResetToken(user);

        // Send reset email
        emailService.sendPasswordResetEmail(email, user.getUsername(), resetToken.getToken());
        
        // Track request for rate limiting
        passwordResetRequests.put(email.toLowerCase(), LocalDateTime.now());

        logger.info("Password reset requested for: {}", email);
        
        // Cleanup old entries periodically
        cleanupOldRateLimitEntries();
    }

    /**
     * Reset password using token
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Optional<VerificationToken> tokenOpt = tokenService.validateToken(token);

        if (tokenOpt.isEmpty()) {
            throw new UnauthorizedException("Invalid or expired reset token");
        }

        VerificationToken resetToken = tokenOpt.get();
        User user = resetToken.getUser();

        // Validate new password strength
        validatePasswordStrength(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.incrementTokenVersion();  // Invalidate all existing tokens
        userRepository.save(user);

        // Mark reset token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Send password reset confirmation email
        emailService.sendPasswordResetConfirmationEmail(user.getEmail(), user.getUsername());

        logger.info("Password reset for user: {}", user.getUsername());
    }

    /**
     * Change user password
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Verify old password (if not first-time change)
        if (!user.isMustChangePassword() && !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        // Validate new password strength
        validatePasswordStrength(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.incrementTokenVersion();  // Invalidate all existing tokens
        userRepository.save(user);

        // Send password change notification email
        emailService.sendPasswordChangeNotificationEmail(user.getEmail(), user.getUsername());

        logger.info("Password changed successfully for user: {}", user.getUsername());
    }

    /**
     * Soft delete user account
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Password is incorrect");
        }

        // Send deletion confirmation email
        emailService.sendAccountDeletionEmail(user.getEmail(), user.getUsername());

        // Soft delete
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);

        logger.info("Account soft-deleted for user: {}", user.getUsername());
    }

    /**
     * Update user profile
     */
    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName, String email) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if new email is already taken by another user
        if (!user.getEmail().equals(email) && userRepository.existsByEmail(email)) {
            throw new UnauthorizedException("Email already in use");
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        
        userRepository.save(user);

        logger.info("Profile updated for user: {}", user.getUsername());
        return user;
    }

    /**
     * Update last login time
     */
    @Transactional
    public void updateLastLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    /**
     * Generate a random password
     */
    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a random OTP code
     */
    public String generateOtp() {
        StringBuilder sb = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Cleanup old rate limit entries to prevent memory leaks
     */
    private void cleanupOldRateLimitEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        
        passwordResetRequests.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        signupRequests.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
