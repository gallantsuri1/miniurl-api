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

import java.time.Year;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.name:MiniURL}")
    private String appName;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Check if email is configured
     */
    private boolean isEmailConfigured() {
        return mailUsername != null && !mailUsername.isEmpty() && !"null".equals(mailUsername);
    }

    /**
     * Send OTP email for user registration/login
     * Uses circuit breaker, bulkhead, and retry for resilience
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Your Verification Code");

            String emailContent = buildOtpEmailContent(otp, username);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            logger.warn("OTP for {}: {}", toEmail, otp);
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            // Fallback to simple from address
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(mailUsername);
                helper.setTo(toEmail);
                helper.setSubject(appName + " - Your Verification Code");
                helper.setText(buildOtpEmailContent(otp, username), true);
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Verify Your Email");

            String verificationLink = baseUrl + "/activate?token=" + token;
            String emailContent = buildEmailVerificationContent(username, verificationLink);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Verification email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, appName + " - Verify Your Email",
                buildEmailVerificationContent(username, baseUrl + "/activate?token=" + token));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Password Reset Request");

            String resetLink = baseUrl + "/reset-password?token=" + token;
            String emailContent = buildPasswordResetContent(username, resetLink);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Password reset email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, appName + " - Password Reset Request",
                buildPasswordResetContent(username, baseUrl + "/reset-password?token=" + token));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to " + appName + "!");

            String emailContent = buildWelcomeEmailContent(username);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Welcome email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, "Welcome to " + appName + "!", buildWelcomeEmailContent(username));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject("Welcome Back to " + appName + "!");

            String emailContent = buildWelcomeBackEmailContent(username);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Welcome back email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send welcome back email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, "Welcome Back to " + appName + "!", buildWelcomeBackEmailContent(username));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " Account Deleted");

            String emailContent = buildDeletionEmailContent(username);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Account deletion email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send deletion email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, appName + " Account Deleted", buildDeletionEmailContent(username));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Password Reset Successful");

            String emailContent = buildPasswordResetConfirmationContent(username, baseUrl + "/forgot-password");
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Password reset confirmation email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send password reset confirmation email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, appName + " - Password Reset Successful",
                buildPasswordResetConfirmationContent(username, baseUrl + "/forgot-password"));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - Password Changed Successfully");

            String emailContent = buildPasswordChangeNotificationContent(username, baseUrl + "/settings");
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Password change notification email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send password change notification email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, appName + " - Password Changed Successfully",
                buildPasswordChangeNotificationContent(username, baseUrl + "/settings"));
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject(appName + " - You're Invited!");

            String inviteLink = baseUrl + "/signup?invite=" + token;
            String emailContent = buildInviteEmailContent(inviteLink);
            helper.setText(emailContent, true);

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
    public void sendInviteFallback(String toEmail, String token, String baseUrl, Throwable t) {
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

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);
            helper.setSubject("🎉 Welcome to " + appName + " - You're All Set!");

            String emailContent = buildCongratulationsEmailContent(firstName);
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Congratulations email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send congratulations email to {}: {}", toEmail, e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("Invalid from address format: {}", e.getMessage());
            sendSimpleEmail(toEmail, "🎉 Welcome to " + appName + "!", buildCongratulationsEmailContent(firstName));
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

    /**
     * Send simple email without name in from address (fallback)
     */
    private void sendSimpleEmail(String toEmail, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
        } catch (Exception e) {
            logger.error("Failed to send simple email: {}", e.getMessage());
        }
    }

    private String buildOtpEmailContent(String otp, String username) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #4a90d9; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .otp-code { font-size: 32px; font-weight: bold; color: #4a90d9;
                                text-align: center; padding: 20px; background: #fff;
                                border: 2px dashed #4a90d9; margin: 20px 0; letter-spacing: 5px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>%s</h1>
                        <p>URL Shortener Service</p>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>Your verification code for %s is:</p>
                        <div class="otp-code">%s</div>
                        <p>This code will expire in 10 minutes.</p>
                        <p>If you didn't request this code, please ignore this email.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(appName, username, appName, otp, currentYear, appName);
    }

    private String buildEmailVerificationContent(String username, String verificationLink) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #4a90d9; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background: #4a90d9;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Verify Your Email</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>Thank you for registering with %s. Please click the button below to verify your email address:</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Verify Email</a>
                        </p>
                        <p>This link will expire in 15 minutes.</p>
                        <p>If you didn't create an account, please ignore this email.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, appName, verificationLink, currentYear, appName);
    }

    private String buildPasswordResetContent(String username, String resetLink) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #dc3545; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background: #dc3545; 
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Password Reset Request</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>You requested to reset your password. Click the button below to proceed:</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Reset Password</a>
                        </p>
                        <p>This link will expire in 15 minutes.</p>
                        <p>If you didn't request a password reset, please ignore this email or contact support.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, resetLink, currentYear, appName);
    }

    private String buildWelcomeEmailContent(String username) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #4a90d9; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background: #4a90d9;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome to %s!</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>Thank you for registering with %s. Your account has been successfully created.</p>
                        <p>You can now start shortening your URLs and managing them from your dashboard.</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Go to Dashboard</a>
                        </p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(appName, username, appName, baseUrl + "/dashboard", currentYear, appName);
    }

    private String buildWelcomeBackEmailContent(String username) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #4a90d9; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background: #4a90d9;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Welcome Back to %s!</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p><strong>Welcome back!</strong> We're excited to have you again.</p>
                        <p>Your account has been successfully reactivated. You can now start shortening your URLs and managing them from your dashboard.</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Go to Dashboard</a>
                        </p>
                        <p>If you have any questions, feel free to reach out to our support team.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(appName, username, baseUrl + "/dashboard", currentYear, appName);
    }

    private String buildDeletionEmailContent(String username) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #dc3545; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Account Deleted</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p>Your %s account has been successfully deleted.</p>
                        <p>All your shortened URLs and associated data have been permanently removed.</p>
                        <p>If you have any questions, please contact our support team.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, appName, currentYear, appName);
    }

    private String buildPasswordResetConfirmationContent(String username, String forgotPasswordLink) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #28a745; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .warning-box { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }
                    .button { display: inline-block; padding: 12px 24px; background: #dc3545;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 10px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Password Reset Successful</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p><strong>Your password has been reset successfully.</strong></p>

                        <div class="warning-box">
                            <strong>⚠️ Note:</strong> If this password reset was not initiated by you,
                            please click the button below to reset your password immediately:
                            <br><br>
                            <a href="%s" class="button">Reset Password</a>
                        </div>

                        <p>If you have any concerns, please contact our support team.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, forgotPasswordLink, currentYear, appName);
    }

    private String buildPasswordChangeNotificationContent(String username, String settingsLink) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #28a745; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .warning-box { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }
                    .button { display: inline-block; padding: 12px 24px; background: #4a90d9;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 10px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Password Changed Successfully</h1>
                    </div>
                    <div class="content">
                        <p>Hello %s,</p>
                        <p><strong>Your password has been changed successfully.</strong></p>
                        <p>This is a confirmation that your %s account password was updated.</p>

                        <div class="warning-box">
                            <strong>⚠️ Did not make this change?</strong> If you didn't change your password,
                            please click the button below to secure your account immediately:
                            <br><br>
                            <a href="%s" class="button">Go to Settings</a>
                        </div>

                        <p>If you have any concerns, please contact our support team.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(username, appName, settingsLink, currentYear, appName);
    }

    private String buildInviteEmailContent(String inviteLink) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #4a90d9; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background: #f9f9f9; }
                    .button { display: inline-block; padding: 12px 24px; background: #4a90d9;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 10px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>You're Invited!</h1>
                    </div>
                    <div class="content">
                        <p>Hello,</p>
                        <p>You have been invited to join <strong>%s</strong> - our URL shortener service.</p>
                        <p>Click the button below to create your account and start shortening URLs:</p>
                        <p style="text-align: center;">
                            <a href="%s" class="button">Accept Invitation</a>
                        </p>
                        <p>This invitation link will expire in 7 days.</p>
                        <p>If you have any questions, please contact the person who sent you this invitation.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(appName, inviteLink, currentYear, appName);
    }

    private String buildCongratulationsEmailContent(String firstName) {
        int currentYear = Year.now().getValue();
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; }
                    .content { padding: 30px; background: #f9f9f9; }
                    .emoji { font-size: 48px; }
                    .button { display: inline-block; padding: 14px 28px; background: #667eea;
                              color: white; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="emoji">🎉🎊🥳</div>
                        <h1>Congratulations, %s!</h1>
                        <p>You're Successfully Registered!</p>
                    </div>
                    <div class="content">
                        <p>Dear %s,</p>
                        <p>Welcome to <strong>%s</strong>! We're thrilled to have you on board.</p>
                        <p>Your account has been successfully created. You're now part of our community!</p>
                        
                        <p style="text-align: center;">
                            <a href="%s/login" class="button">🚀 Get Started</a>
                        </p>
                        
                        <p><strong>What's next?</strong></p>
                        <ul>
                            <li>✅ Your account is ready to use</li>
                            <li>📧 Check your email for verification (if not already verified)</li>
                            <li>🔐 Log in and start exploring</li>
                        </ul>
                        
                        <p>If you have any questions or need assistance, feel free to reach out to our support team.</p>
                        
                        <p>Happy exploring! 🌟</p>
                    </div>
                    <div class="footer">
                        <p>&copy; %d %s. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(firstName, firstName, appName, baseUrl, currentYear, appName);
    }
}
