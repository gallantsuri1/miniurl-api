package com.miniurl.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for exception classes.
 */
@DisplayName("Exception Tests")
class ExceptionTest {

    @Test
    @DisplayName("ResourceNotFoundException")
    void resourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");
        assertEquals("Resource not found", ex.getMessage());
    }

    @Test
    @DisplayName("UnauthorizedException")
    void unauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Unauthorized");
        assertEquals("Unauthorized", ex.getMessage());
    }

    @Test
    @DisplayName("UrlValidationException")
    void urlValidationException() {
        UrlValidationException ex = new UrlValidationException("Invalid URL");
        assertEquals("Invalid URL", ex.getMessage());
    }

    @Test
    @DisplayName("AliasNotAvailableException")
    void aliasNotAvailableException() {
        AliasNotAvailableException ex = new AliasNotAvailableException("Alias not available");
        assertEquals("Alias not available", ex.getMessage());
    }

    @Test
    @DisplayName("UrlLimitExceededException - per minute")
    void urlLimitExceededExceptionPerMinute() {
        UrlLimitExceededException ex = new UrlLimitExceededException("per minute", 10, 10);
        assertNotNull(ex.getMessage());
        assertEquals("per minute", ex.getLimitType());
        assertEquals(10, ex.getLimit());
        assertEquals(10, ex.getCurrentCount());
        assertNotNull(ex.getRetryMessage());
        assertNotNull(ex.getRetryAfter());
        assertNotNull(ex.getUiMessage());
        assertTrue(ex.getUiMessage().contains("minute"));
    }

    @Test
    @DisplayName("UrlLimitExceededException - per day")
    void urlLimitExceededExceptionPerDay() {
        UrlLimitExceededException ex = new UrlLimitExceededException("per day", 100, 100);
        assertEquals("per day", ex.getLimitType());
        assertTrue(ex.getUiMessage().toLowerCase().contains("tomorrow") || 
                   ex.getUiMessage().toLowerCase().contains("daily"));
    }

    @Test
    @DisplayName("UrlLimitExceededException - per month")
    void urlLimitExceededExceptionPerMonth() {
        UrlLimitExceededException ex = new UrlLimitExceededException("per month", 1000, 1000);
        assertEquals("per month", ex.getLimitType());
        assertTrue(ex.getUiMessage().toLowerCase().contains("month"));
    }

    @Test
    @DisplayName("UrlLimitExceededException - default")
    void urlLimitExceededExceptionDefault() {
        UrlLimitExceededException ex = new UrlLimitExceededException("unknown", 100, 100);
        assertEquals("unknown", ex.getLimitType());
    }
}
