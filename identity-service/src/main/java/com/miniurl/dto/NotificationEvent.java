package com.miniurl.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Event sent to the Notification Service via Kafka to trigger an email.
 */
public class NotificationEvent implements Serializable {
    private String eventType; // e.g., "OTP", "EMAIL_VERIFICATION", "PASSWORD_RESET", "WELCOME", "INVITE", etc.
    private String toEmail;
    private String username;
    private Map<String, Object> payload;

    // Constructors
    public NotificationEvent() {}

    public NotificationEvent(String eventType, String toEmail, String username, Map<String, Object> payload) {
        this.eventType = eventType;
        this.toEmail = toEmail;
        this.username = username;
        this.payload = payload;
    }

    // Getters and Setters
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    // Builder pattern factory
    public static Builder builder() {
        return new Builder();
    }

    // Builder pattern
    public static class Builder {
        private String eventType;
        private String toEmail;
        private String username;
        private Map<String, Object> payload;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder toEmail(String toEmail) {
            this.toEmail = toEmail;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public NotificationEvent build() {
            return new NotificationEvent(eventType, toEmail, username, payload);
        }
    }
}
