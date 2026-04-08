package com.miniurl.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User account lockout functionality
 */
@DisplayName("User Account Lockout Tests")
class UserLockoutTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("John", "Doe", "john@example.com", "johndoe", "hashedPassword");
        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
    }

    @Test
    @DisplayName("New user should not be locked")
    void newUserShouldNotBeLocked() {
        assertFalse(user.isAccountLocked());
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("Account should lock after 5 failed attempts")
    void accountShouldLockAfterFiveFailedAttempts() {
        // Simulate 5 failed login attempts
        for (int i = 1; i <= 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertTrue(user.isAccountLocked());
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockoutTime());
    }

    @Test
    @DisplayName("Account should not lock after 4 failed attempts")
    void accountShouldNotLockAfterFourFailedAttempts() {
        // Simulate 4 failed login attempts
        for (int i = 1; i <= 4; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertFalse(user.isAccountLocked());
        assertEquals(4, user.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("Lockout time should be 5 minutes from now")
    void lockoutTimeShouldBeFiveMinutes() {
        LocalDateTime beforeLock = LocalDateTime.now();

        // Simulate 5 failed login attempts
        for (int i = 1; i <= 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        LocalDateTime afterLock = LocalDateTime.now().plusMinutes(5);

        assertNotNull(user.getLockoutTime());
        assertTrue(user.getLockoutTime().isAfter(beforeLock));
        assertTrue(user.getLockoutTime().isBefore(afterLock.plusSeconds(1)));
    }

    @Test
    @DisplayName("Reset failed attempts should clear lockout")
    void resetFailedAttemptsShouldClearLockout() {
        // Lock the account
        for (int i = 1; i <= 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertTrue(user.isAccountLocked());

        // Reset
        user.resetFailedLoginAttempts();

        assertFalse(user.isAccountLocked());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockoutTime());
    }

    @Test
    @DisplayName("Lockout expired should return true when lockout time is in past")
    void lockoutExpiredShouldReturnTrueWhenTimePassed() {
        // Set lockout time to 10 minutes ago
        user.setLockoutTime(LocalDateTime.now().minusMinutes(10));
        user.setFailedLoginAttempts(5);

        assertTrue(user.isLockoutExpired());
        assertFalse(user.isAccountLocked());
    }

    @Test
    @DisplayName("Lockout expired should return false when lockout time is in future")
    void lockoutExpiredShouldReturnFalseWhenTimeNotPassed() {
        // Set lockout time to 3 minutes from now
        user.setLockoutTime(LocalDateTime.now().plusMinutes(3));
        user.setFailedLoginAttempts(5);

        assertFalse(user.isLockoutExpired());
        assertTrue(user.isAccountLocked());
    }

    @Test
    @DisplayName("Failed attempts should increment correctly")
    void failedAttemptsShouldIncrementCorrectly() {
        assertEquals(0, user.getFailedLoginAttempts());

        user.incrementFailedLoginAttempts();
        assertEquals(1, user.getFailedLoginAttempts());

        user.incrementFailedLoginAttempts();
        assertEquals(2, user.getFailedLoginAttempts());

        user.incrementFailedLoginAttempts();
        assertEquals(3, user.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("Account should remain locked after additional failed attempts")
    void accountShouldRemainLockedAfterAdditionalFailedAttempts() {
        // Lock the account
        for (int i = 1; i <= 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertTrue(user.isAccountLocked());

        // Increment again (account is already locked)
        user.incrementFailedLoginAttempts();
        assertEquals(6, user.getFailedLoginAttempts());
        assertTrue(user.isAccountLocked());
        assertNotNull(user.getLockoutTime());
        // Lockout time should still be set (may be updated or remain same)
        assertTrue(user.getLockoutTime().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Successful login should reset all lockout state")
    void successfulLoginShouldResetAllLockoutState() {
        // Simulate failed attempts
        for (int i = 1; i <= 3; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertEquals(3, user.getFailedLoginAttempts());
        assertFalse(user.isAccountLocked());

        // Simulate successful login
        user.resetFailedLoginAttempts();

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockoutTime());
        assertFalse(user.isAccountLocked());
    }
}
