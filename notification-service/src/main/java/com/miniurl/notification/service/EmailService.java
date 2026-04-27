package com.miniurl.notification.service;

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
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.ui-base-url:http://localhost:3000}")
    private String uiBaseUrl;

    @Value("${app.name:MyURL}")
    private String appName;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    private boolean isEmailConfigured() {
        return mailUsername != null && !mailUsername.isEmpty() && !"null".equals(mailUsername);
    }

    private Context createBaseContext(Map<String, Object> payload) {
        Context context = new Context();
        context.setVariable("appName", appName);
        context.setVariable("year", Year.now().getValue());
        if (payload != null) {
            payload.forEach(context::setVariable);
        }
        return context;
    }

    public void sendEmail(String eventType, String toEmail, String username, Map<String, Object> payload) {
        if (!isEmailConfigured()) {
            logger.warn("SMTP not configured. Skipping email {} for {}. Payload: {}", eventType, toEmail, payload);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, appName);
            helper.setTo(toEmail);

            String subject;
            String template;
            Context context = createBaseContext(payload);
            context.setVariable("username", username);

            switch (eventType) {
                case "OTP":
                    subject = appName + " - Your Verification Code";
                    template = "email/otp-email";
                    break;
                case "EMAIL_VERIFICATION":
                    subject = appName + " - Verify Your Email";
                    template = "email/email-verification";
                    context.setVariable("verificationLink", uiBaseUrl + "/activate?token=" + payload.get("token"));
                    break;
                case "PASSWORD_RESET":
                    subject = appName + " - Password Reset Request";
                    template = "email/password-reset";
                    context.setVariable("resetLink", uiBaseUrl + "/reset-password?token=" + payload.get("token"));
                    break;
                case "WELCOME":
                    subject = "Welcome to " + appName + "!";
                    template = "email/welcome-email";
                    context.setVariable("dashboardLink", uiBaseUrl + "/dashboard");
                    break;
                case "WELCOME_BACK":
                    subject = "Welcome Back to " + appName + "!";
                    template = "email/welcome-back-email";
                    context.setVariable("dashboardLink", uiBaseUrl + "/dashboard");
                    break;
                case "ACCOUNT_DELETION":
                    subject = appName + " Account Deleted";
                    template = "email/account-deletion";
                    break;
                case "PASSWORD_RESET_CONFIRMATION":
                    subject = appName + " - Password Reset Successful";
                    template = "email/password-reset-confirmation";
                    context.setVariable("forgotPasswordLink", uiBaseUrl + "/forgot-password");
                    break;
                case "PASSWORD_CHANGE_NOTIFICATION":
                    subject = appName + " - Password Changed Successfully";
                    template = "email/password-change-notification";
                    context.setVariable("settingsLink", uiBaseUrl + "/settings");
                    break;
                case "INVITE":
                    subject = appName + " - You're Invited!";
                    template = "email/invite-email";
                    context.setVariable("inviteLink", uiBaseUrl + "/signup?invite=" + payload.get("token"));
                    break;
                case "CONGRATULATIONS":
                    subject = "Welcome to " + appName + " - You're All Set!";
                    template = "email/registration-congratulations";
                    context.setVariable("loginLink", uiBaseUrl + "/login");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported event type: " + eventType);
            }

            helper.setSubject(subject);
            helper.setText(templateEngine.process(template, context), true);

            mailSender.send(message);
            logger.info("Email {} sent successfully to: {}", eventType, toEmail);
        } catch (Exception e) {
            logger.error("Failed to send email {} to {}: {}", eventType, toEmail, e.getMessage());
        }
    }
}
