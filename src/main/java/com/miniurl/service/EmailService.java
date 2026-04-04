package com.miniurl.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final GlobalFlagService globalFlagService;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.ui-base-url:http://localhost:3000}")
    private String uiBaseUrl;

    @Value("${app.name:MiniURL}")
    private String appName;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
                        GlobalFlagService globalFlagService) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.globalFlagService = globalFlagService;
    }

    /**
     * Check if email is configured
     */
    private boolean isEmailConfigured() {
        return mailUsername != null && !mailUsername.isEmpty() && !"null".equals(mailUsername);
    }

    /**
     * Resolve the application display name.
     * Fallback chain: GLOBAL_APP_NAME global flag (if enabled) → application property app.name
     */
    private String resolveAppName() {
        String globalAppName = globalFlagService.getGlobalAppName();
        return globalAppName != null ? globalAppName : appName;
    }

    /**
     * Build a Thymeleaf context with common variables
     */
    private Context createBaseContext() {
        Context context = new Context();
        context.setVariable("appName", resolveAppName());
        context.setVariable("year", Year.now().getValue());
        return context;
    }

    /**
     * Send OTP email for user registration/login
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendOtpEmailFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendOtpEmail(String toEmail, String otp, String username) {
        if (!isEmailConfigured()) {
            logger.warn("SMTP not configured. OTP for {}: {}", toEmail, otp);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " - Your Verification Code");

            Context context = createBaseContext();
            context.setVariable("otp", otp);
            context.setVariable("username", username);
            String emailContent = templateEngine.process("email/otp-email", context);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            logger.warn("OTP for {}: {}", toEmail, otp);
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(resolveAppName() + " - Your Verification Code");
                Context context = createBaseContext();
                context.setVariable("otp", otp);
                context.setVariable("username", username);
                helper.setText(templateEngine.process("email/otp-email", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback OTP email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendOtpEmail when circuit breaker opens or retry exhausted
     */
    public void sendOtpEmailFallback(String toEmail, String otp, String username, Throwable t) {
        logger.error("Email service unavailable for {}. Logging OTP to console. Error: {}",
            toEmail, t != null ? t.getMessage() : "Circuit breaker open");
        logger.warn("OTP for {}: {}", toEmail, otp);
    }

    /**
     * Send email verification link
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendEmailVerificationFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendEmailVerificationEmail(String toEmail, String username, String token) {
        if (!isEmailConfigured()) {
            logger.warn("SMTP not configured. Verification link for {}: {}", toEmail, token);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " - Verify Your Email");

            String verificationLink = uiBaseUrl + "/activate?token=" + token;
            Context context = createBaseContext();
            context.setVariable("username", username);
            context.setVariable("verificationLink", verificationLink);
            helper.setText(templateEngine.process("email/email-verification", context), true);

            mailSender.send(message);
            logger.info("Verification email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(resolveAppName() + " - Verify Your Email");
                Context context = createBaseContext();
                context.setVariable("username", username);
                context.setVariable("verificationLink", uiBaseUrl + "/activate?token=" + token);
                helper.setText(templateEngine.process("email/email-verification", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback verification email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendEmailVerificationEmail
     */
    public void sendEmailVerificationFallback(String toEmail, String username, String token, Throwable t) {
        logger.error("Email service unavailable for verification. Logging token to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.warn("Verification token for {}: {}", toEmail, token);
    }

    /**
     * Send password reset link
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendPasswordResetFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        if (!isEmailConfigured()) {
            logger.warn("SMTP not configured. Reset link for {}: {}", toEmail, token);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " - Password Reset Request");

            String resetLink = uiBaseUrl + "/reset-password?token=" + token;
            Context context = createBaseContext();
            context.setVariable("username", username);
            context.setVariable("resetLink", resetLink);
            helper.setText(templateEngine.process("email/password-reset", context), true);

            mailSender.send(message);
            logger.info("Password reset email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(resolveAppName() + " - Password Reset Request");
                Context context = createBaseContext();
                context.setVariable("username", username);
                context.setVariable("resetLink", uiBaseUrl + "/reset-password?token=" + token);
                helper.setText(templateEngine.process("email/password-reset", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback password reset email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendPasswordResetEmail
     */
    public void sendPasswordResetFallback(String toEmail, String username, String token, Throwable t) {
        logger.error("Email service unavailable for password reset. Logging token to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.warn("Password reset token for {}: {}", toEmail, token);
    }

    /**
     * Send welcome email after successful registration
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendWelcomeFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendWelcomeEmail(String toEmail, String username) {
        if (!isEmailConfigured()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + resolveAppName() + "!");

            Context context = createBaseContext();
            context.setVariable("username", username);
            context.setVariable("dashboardLink", uiBaseUrl + "/dashboard");
            helper.setText(templateEngine.process("email/welcome-email", context), true);

            mailSender.send(message);
            logger.info("Welcome email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject("Welcome to " + resolveAppName() + "!");
                Context context = createBaseContext();
                context.setVariable("username", username);
                context.setVariable("dashboardLink", uiBaseUrl + "/dashboard");
                helper.setText(templateEngine.process("email/welcome-email", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback welcome email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendWelcomeEmail
     */
    public void sendWelcomeFallback(String toEmail, String username, Throwable t) {
        logger.error("Email service unavailable for welcome email. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Welcome email for {}: would have been sent", toEmail);
    }

    /**
     * Send welcome back email for returning users
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendWelcomeBackFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendWelcomeBackEmail(String toEmail, String username) {
        if (!isEmailConfigured()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject("Welcome Back to " + resolveAppName() + "!");

            Context context = createBaseContext();
            context.setVariable("username", username);
            context.setVariable("dashboardLink", uiBaseUrl + "/dashboard");
            helper.setText(templateEngine.process("email/welcome-back-email", context), true);

            mailSender.send(message);
            logger.info("Welcome back email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send welcome back email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject("Welcome Back to " + resolveAppName() + "!");
                Context context = createBaseContext();
                context.setVariable("username", username);
                context.setVariable("dashboardLink", uiBaseUrl + "/dashboard");
                helper.setText(templateEngine.process("email/welcome-back-email", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback welcome back email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendWelcomeBackEmail
     */
    public void sendWelcomeBackFallback(String toEmail, String username, Throwable t) {
        logger.error("Email service unavailable for welcome back email. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Welcome back email for {}: would have been sent", toEmail);
    }

    /**
     * Send account deletion confirmation email
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendAccountDeletionFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendAccountDeletionEmail(String toEmail, String username) {
        if (!isEmailConfigured()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " Account Deleted");

            Context context = createBaseContext();
            context.setVariable("username", username);
            helper.setText(templateEngine.process("email/account-deletion", context), true);

            mailSender.send(message);
            logger.info("Account deletion email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send deletion email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(resolveAppName() + " Account Deleted");
                Context context = createBaseContext();
                context.setVariable("username", username);
                helper.setText(templateEngine.process("email/account-deletion", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback deletion email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendAccountDeletionEmail
     */
    public void sendAccountDeletionFallback(String toEmail, String username, Throwable t) {
        logger.error("Email service unavailable for deletion email. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Deletion email for {}: would have been sent", toEmail);
    }

    /**
     * Send password reset confirmation email
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendPasswordResetConfirmationFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendPasswordResetConfirmationEmail(String toEmail, String username) {
        if (!isEmailConfigured()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " - Password Reset Successful");

            Context context = createBaseContext();
            context.setVariable("username", username);
            context.setVariable("forgotPasswordLink", uiBaseUrl + "/forgot-password");
            helper.setText(templateEngine.process("email/password-reset-confirmation", context), true);

            mailSender.send(message);
            logger.info("Password reset confirmation email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send password reset confirmation email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(resolveAppName() + " - Password Reset Successful");
                Context context = createBaseContext();
                context.setVariable("username", username);
                context.setVariable("forgotPasswordLink", uiBaseUrl + "/forgot-password");
                helper.setText(templateEngine.process("email/password-reset-confirmation", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback password reset confirmation email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendPasswordResetConfirmationEmail
     */
    public void sendPasswordResetConfirmationFallback(String toEmail, String username, Throwable t) {
        logger.error("Email service unavailable for reset confirmation. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Password reset confirmation for {}: would have been sent", toEmail);
    }

    /**
     * Send password change notification email
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendPasswordChangeNotificationFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendPasswordChangeNotificationEmail(String toEmail, String username) {
        if (!isEmailConfigured()) {
            logger.info("SMTP not configured. Password change notification for {}: skipped", toEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " - Password Changed Successfully");

            Context context = createBaseContext();
            context.setVariable("username", username);
            context.setVariable("settingsLink", uiBaseUrl + "/settings");
            helper.setText(templateEngine.process("email/password-change-notification", context), true);

            mailSender.send(message);
            logger.info("Password change notification email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send password change notification email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(resolveAppName() + " - Password Changed Successfully");
                Context context = createBaseContext();
                context.setVariable("username", username);
                context.setVariable("settingsLink", uiBaseUrl + "/settings");
                helper.setText(templateEngine.process("email/password-change-notification", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback password change notification email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendPasswordChangeNotificationEmail
     */
    public void sendPasswordChangeNotificationFallback(String toEmail, String username, Throwable t) {
        logger.error("Email service unavailable for password change notification. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Password change notification for {}: would have been sent", toEmail);
    }

    /**
     * Send email invitation for user signup
     */
    public void sendInviteEmail(String toEmail, String token) {
        if (!isEmailConfigured()) {
            logger.info("SMTP not configured. Invite email for {}: token={}", toEmail, token);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject(resolveAppName() + " - You're Invited!");

            String inviteLink = uiBaseUrl + "/signup?invite=" + token;
            Context context = createBaseContext();
            context.setVariable("inviteLink", inviteLink);
            helper.setText(templateEngine.process("email/invite-email", context), true);

            mailSender.send(message);
            logger.info("Invite email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            logger.error("Failed to send invite email to {}: {} - {}", toEmail, e.getClass().getName(), e.getMessage(), e);
        }
    }

    /**
     * Fallback method for sendInviteEmail (not used since we removed circuit breaker)
     */
    @SuppressWarnings("unused")
    public void sendInviteFallback(String toEmail, String token, String uiBaseUrl, Throwable t) {
        logger.error("Email service unavailable for invite email. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Invite email for {}: would have been sent", toEmail);
    }

    /**
     * Send registration congratulations email
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "sendCongratulationsFallback")
    @Bulkhead(name = "email")
    @Retry(name = "emailService")
    public void sendRegistrationCongratulationsEmail(String toEmail, String firstName) {
        if (!isEmailConfigured()) {
            logger.info("SMTP not configured. Congratulations email for {}: skipped", toEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, resolveAppName());
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + resolveAppName() + " - You're All Set!");

            Context context = createBaseContext();
            context.setVariable("firstName", firstName);
            context.setVariable("loginLink", uiBaseUrl + "/login");
            helper.setText(templateEngine.process("email/registration-congratulations", context), true);

            mailSender.send(message);
            logger.info("Congratulations email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send congratulations email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject("Welcome to " + resolveAppName() + "!");
                Context context = createBaseContext();
                context.setVariable("firstName", firstName);
                context.setVariable("loginLink", uiBaseUrl + "/login");
                helper.setText(templateEngine.process("email/registration-congratulations", context), true);
                mailSender.send(message);
            } catch (Exception ex) {
                logger.error("Failed to send fallback congratulations email: {}", ex.getMessage());
            }
        }
    }

    /**
     * Fallback method for sendRegistrationCongratulationsEmail
     */
    public void sendCongratulationsFallback(String toEmail, String firstName, Throwable t) {
        logger.error("Email service unavailable for congratulations email. Logging to console. Error: {}",
            t != null ? t.getMessage() : "Circuit breaker open");
        logger.info("Congratulations email for {}: would have been sent", toEmail);
    }
}
