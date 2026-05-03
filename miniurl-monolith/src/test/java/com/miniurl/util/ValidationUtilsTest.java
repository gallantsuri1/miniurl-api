package com.miniurl.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationUtils.
 */
@DisplayName("ValidationUtils Tests")
class ValidationUtilsTest {

    // ==================== Username validation ====================

    @Test
    @DisplayName("Valid usernames should pass")
    void isValidUsername_validNames() {
        assertTrue(ValidationUtils.isValidUsername("abc"));
        assertTrue(ValidationUtils.isValidUsername("johndoe"));
        assertTrue(ValidationUtils.isValidUsername("user_123"));
        assertTrue(ValidationUtils.isValidUsername("a1_b"));
        assertTrue(ValidationUtils.isValidUsername("AbCdEf1234567890"));
    }

    @Test
    @DisplayName("Invalid usernames should fail")
    void isValidUsername_invalidNames() {
        assertFalse(ValidationUtils.isValidUsername(null));
        assertFalse(ValidationUtils.isValidUsername(""));
        assertFalse(ValidationUtils.isValidUsername("ab"));           // too short
        assertFalse(ValidationUtils.isValidUsername("1user"));        // starts with digit
        assertFalse(ValidationUtils.isValidUsername("_user"));        // starts with underscore
        assertFalse(ValidationUtils.isValidUsername("user-name"));    // hyphen not allowed
        assertFalse(ValidationUtils.isValidUsername("user name"));    // space not allowed
        assertFalse(ValidationUtils.isValidUsername("user@name"));    // special char not allowed
    }

    // ==================== Reserved username ====================

    @Test
    @DisplayName("Reserved usernames should be detected")
    void isReservedUsername_detectsReserved() {
        assertTrue(ValidationUtils.isReservedUsername("admin"));
        assertTrue(ValidationUtils.isReservedUsername("Admin"));
        assertTrue(ValidationUtils.isReservedUsername("ADMIN"));
        assertTrue(ValidationUtils.isReservedUsername("root"));
        assertTrue(ValidationUtils.isReservedUsername("system"));
        assertTrue(ValidationUtils.isReservedUsername("support"));
        assertTrue(ValidationUtils.isReservedUsername("www"));
        assertTrue(ValidationUtils.isReservedUsername("api"));
        assertTrue(ValidationUtils.isReservedUsername("login"));
        assertTrue(ValidationUtils.isReservedUsername("signup"));
        assertTrue(ValidationUtils.isReservedUsername("webmaster"));
    }

    @Test
    @DisplayName("Non-reserved usernames should pass")
    void isReservedUsername_nonReserved() {
        assertFalse(ValidationUtils.isReservedUsername("johndoe"));
        assertFalse(ValidationUtils.isReservedUsername("testuser"));
        assertFalse(ValidationUtils.isReservedUsername("developer123"));
        assertFalse(ValidationUtils.isReservedUsername(null));
    }

    // ==================== Common password ====================

    @Test
    @DisplayName("Common passwords should be detected")
    void isCommonPassword_detectsCommon() {
        assertTrue(ValidationUtils.isCommonPassword("password"));
        assertTrue(ValidationUtils.isCommonPassword("Password"));
        assertTrue(ValidationUtils.isCommonPassword("PASSWORD"));
        assertTrue(ValidationUtils.isCommonPassword("123456"));
        assertTrue(ValidationUtils.isCommonPassword("qwerty"));
        assertTrue(ValidationUtils.isCommonPassword("admin123"));
        assertTrue(ValidationUtils.isCommonPassword("letmein"));
        assertTrue(ValidationUtils.isCommonPassword("iloveyou"));
        assertTrue(ValidationUtils.isCommonPassword("p@ssw0rd"));
    }

    @Test
    @DisplayName("Uncommon passwords should pass")
    void isCommonPassword_notCommon() {
        assertFalse(ValidationUtils.isCommonPassword("MyStr0ng!Pass"));
        assertFalse(ValidationUtils.isCommonPassword("correct horse battery staple"));
        assertFalse(ValidationUtils.isCommonPassword(null));
    }

    // ==================== Password contains username ====================

    @Test
    @DisplayName("Password containing username should be detected")
    void passwordContainsUsername_detects() {
        assertTrue(ValidationUtils.passwordContainsUsername("johndoe123", "johndoe"));
        assertTrue(ValidationUtils.passwordContainsUsername("MyJohndoePass", "johndoe"));
        assertTrue(ValidationUtils.passwordContainsUsername("JOHNDOE!", "johndoe"));
        assertTrue(ValidationUtils.passwordContainsUsername("password", "pass"));
    }

    @Test
    @DisplayName("Password not containing username should pass")
    void passwordContainsUsername_notContained() {
        assertFalse(ValidationUtils.passwordContainsUsername("Str0ng!Pass", "johndoe"));
        assertFalse(ValidationUtils.passwordContainsUsername("MyS3cur3P@ss", "admin"));
        assertFalse(ValidationUtils.passwordContainsUsername("Str0ng!Pass", null));
        assertFalse(ValidationUtils.passwordContainsUsername(null, "admin"));
    }
}
