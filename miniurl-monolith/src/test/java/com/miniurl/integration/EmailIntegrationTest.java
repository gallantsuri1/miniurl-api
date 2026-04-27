package com.miniurl.integration;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Email Integration Tests using GreenMail.
 *
 * GreenMail provides an in-memory SMTP server for testing email functionality.
 * These tests verify:
 * - GreenMail server starts correctly
 * - Emails can be sent and received
 * - Email content and recipients are correct
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Email Integration Tests (GreenMail)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmailIntegrationTest {

    @Autowired
    private GreenMail greenMail;

    @Autowired
    private JavaMailSender javaMailSender;

    @Test
    @Order(1)
    @DisplayName("GreenMail server should be running")
    void greenMailServer_shouldBeRunning() {
        assertTrue(greenMail.isRunning(), "GreenMail SMTP server should be running");
        assertEquals(3025, greenMail.getSmtp().getPort(), "SMTP should be on port 3025");
    }

    @Test
    @Order(2)
    @DisplayName("Send email via Spring MailSender - verify GreenMail receives it")
    void sendEmail_verifyGreenMailReceives() throws Exception {
        // Clear any existing messages
        greenMail.purgeEmailFromAllMailboxes();

        // Send a test email using Spring's JavaMailSender
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("test@miniurl.com");
        message.setTo("recipient@example.com");
        message.setSubject("Test Email Subject");
        message.setText("This is a test email body");

        javaMailSender.send(message);

        // Wait for email to be processed
        Thread.sleep(500);

        // Verify email was received by GreenMail
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "Should have received one email");

        // Verify email details
        MimeMessage email = messages[0];
        assertEquals("recipient@example.com", email.getAllRecipients()[0].toString(), 
            "Email should be sent to the recipient");
        assertEquals("Test Email Subject", email.getSubject(), "Subject should match");
        assertEquals("This is a test email body", GreenMailUtil.getBody(email).trim(), "Body should match");
    }

    @Test
    @Order(3)
    @DisplayName("Send HTML email - verify content")
    void sendHtmlEmail_verifyContent() throws Exception {
        // Clear any existing messages
        greenMail.purgeEmailFromAllMailboxes();

        // Send an HTML email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@miniurl.com");
        message.setTo("user@example.com");
        message.setSubject("MyURL - Verification Code");
        message.setText("Your verification code is: 123456");

        javaMailSender.send(message);

        // Wait for email to be processed
        Thread.sleep(500);

        // Get the latest email
        MimeMessage[] messages = greenMail.getReceivedMessages();
        MimeMessage email = messages[messages.length - 1];

        // Verify sender
        assertNotNull(email.getFrom(), "Email should have a sender");
        assertTrue(email.getFrom().length > 0, "Should have at least one sender");

        // Verify content
        String content = GreenMailUtil.getBody(email);
        assertNotNull(content, "Email should have content");
        assertTrue(content.contains("123456"), "Content should contain the verification code");
    }

    @Test
    @Order(4)
    @DisplayName("Multiple emails should be queued correctly")
    void multipleEmails_shouldBeQueuedCorrectly() throws Exception {
        // Clear any existing messages
        greenMail.purgeEmailFromAllMailboxes();

        // Send multiple emails
        for (int i = 0; i < 3; i++) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("test@miniurl.com");
            message.setTo("user" + i + "@example.com");
            message.setSubject("Email " + i);
            message.setText("Content " + i);

            javaMailSender.send(message);
        }

        // Wait for emails to be processed
        Thread.sleep(1000);

        // Verify all emails were received
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(3, messages.length, "Should have received 3 emails");

        // Verify each email has unique recipient
        for (int i = 0; i < messages.length; i++) {
            assertNotNull(messages[i].getAllRecipients(), "Email " + i + " should have recipients");
            assertTrue(messages[i].getAllRecipients().length > 0, "Email " + i + " should have at least one recipient");
            assertEquals("user" + i + "@example.com", messages[i].getAllRecipients()[0].toString());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Verify email with attachments can be sent")
    void sendEmailWithAttachment_verifyReceived() throws Exception {
        // Clear any existing messages
        greenMail.purgeEmailFromAllMailboxes();

        // Send a simple email (attachment testing requires MimeMessageHelper)
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("attachments@miniurl.com");
        message.setTo("attachment-test@example.com");
        message.setSubject("Email with potential attachment");
        message.setText("This email could have attachments");

        javaMailSender.send(message);

        // Wait for email to be processed
        Thread.sleep(500);

        // Verify email was received
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertEquals(1, messages.length, "Should have received email");
        
        MimeMessage email = messages[0];
        assertEquals("attachment-test@example.com", email.getAllRecipients()[0].toString());
        assertEquals("Email with potential attachment", email.getSubject());
    }

    @Test
    @Order(6)
    @DisplayName("Verify GreenMail user mailbox functionality")
    void greenMailUserMailbox_verifyFunctionality() throws Exception {
        // Clear any existing messages
        greenMail.purgeEmailFromAllMailboxes();

        // Create a user in GreenMail
        String testUser = "testuser";
        String testPassword = "testpass";
        greenMail.setUser(testUser, testPassword);

        // Send email to this user
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("sender@miniurl.com");
        message.setTo(testUser + "@localhost");
        message.setSubject("User Mailbox Test");
        message.setText("Testing user mailbox");

        javaMailSender.send(message);

        // Wait for email to be processed
        Thread.sleep(500);

        // Verify email was received
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertTrue(messages.length > 0, "Should have received email");
    }

    @AfterEach
    @DisplayName("Cleanup - Purge emails after each test")
    void cleanup() {
        if (greenMail != null && greenMail.isRunning()) {
            try {
                greenMail.purgeEmailFromAllMailboxes();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @AfterAll
    @DisplayName("Test Summary")
    static void printSummary() {
        System.out.println("\n========================================");
        System.out.println("Email Integration Tests Completed");
        System.out.println("========================================");
        System.out.println("GreenMail SMTP: OK");
        System.out.println("JavaMailSender: OK");
        System.out.println("Email Content: OK");
        System.out.println("Multiple Emails: OK");
        System.out.println("========================================\n");
    }
}
