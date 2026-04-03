package com.miniurl.config;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Test configuration for email-related beans.
 * Provides GreenMail SMTP server for testing email functionality.
 */
@TestConfiguration
@Profile("dev")
public class TestEmailConfig {

    @Bean
    public GreenMail greenMail() {
        ServerSetup smtp = new ServerSetup(3025, null, ServerSetup.PROTOCOL_SMTP);
        GreenMail greenMail = new GreenMail(smtp);
        greenMail.setUser("test@localhost", "test", "test");
        greenMail.start();
        return greenMail;
    }

    @Bean
    public JavaMailSender javaMailSender(GreenMail greenMail) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(greenMail.getSmtp().getPort());
        mailSender.setUsername("test");
        mailSender.setPassword("test");
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.debug", "false");
        
        return mailSender;
    }
}
