package com.miniurl.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration classes.
 */
@DisplayName("Configuration Tests")
class ConfigTest {

    @Test
    @DisplayName("RateLimitConfig")
    void rateLimitConfig() {
        RateLimitConfig config = new RateLimitConfig();
        assertNotNull(config);
    }

    @Test
    @DisplayName("ResilienceConfig")
    void resilienceConfig() {
        ResilienceConfig config = new ResilienceConfig();
        assertNotNull(config);
    }

    @Test
    @DisplayName("LockoutPreventionFilter")
    void lockoutPreventionFilter() {
        LockoutPreventionFilter filter = new LockoutPreventionFilter();
        assertNotNull(filter);
    }

    @Test
    @DisplayName("GlobalExceptionHandler")
    void globalExceptionHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertNotNull(handler);
    }

    @Test
    @DisplayName("OpenApiConfig")
    void openApiConfig() {
        OpenApiConfig config = new OpenApiConfig();
        assertNotNull(config);
    }
}
