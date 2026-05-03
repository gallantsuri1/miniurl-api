package com.miniurl.config;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * GreenMail configuration for email testing.
 * 
 * GreenMail is a mock SMTP/POP3/IMAP server for testing email functionality.
 * It runs in-memory and captures all emails sent during tests.
 */
@Configuration
@Profile("test")
public class GreenMailConfig {

    /**
     * Creates and starts GreenMail server for testing.
     * Configured with SMTP on port 3025 without authentication for testing.
     */
    @Bean(destroyMethod = "stop")
    public GreenMail greenMail() {
        // Create SMTP server setup without authentication
        ServerSetup smtp = new ServerSetup(3025, null, ServerSetup.PROTOCOL_SMTP);
        smtp.setServerStartupTimeout(10000);
        
        GreenMail greenMail = new GreenMail(smtp);
        greenMail.start();
        return greenMail;
    }
}
