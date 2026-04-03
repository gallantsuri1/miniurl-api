package com.miniurl.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entity tests for core classes.
 */
@DisplayName("Entity Tests")
class EntityTest {

    @Test
    @DisplayName("User - all methods")
    void userEntity() {
        Role role = new Role("USER", "Regular user");
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("password");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setOtpVerified(true);
        user.setFailedLoginAttempts(0);
        
        assertEquals(1L, user.getId());
        assertFalse(user.isAdmin());
        assertFalse(user.isAccountLocked());
    }

    @Test
    @DisplayName("User - builder")
    void userBuilder() {
        User user = User.builder()
            .id(2L).username("user2").email("user2@example.com")
            .firstName("User").lastName("Two").password("pass")
            .status(UserStatus.ACTIVE).otpVerified(true)
            .failedLoginAttempts(0).build();
        assertEquals(2L, user.getId());
    }

    @Test
    @DisplayName("User - constructor")
    void userConstructor() {
        User user = new User("John", "Doe", "john@example.com", "johndoe", "password");
        assertEquals("John", user.getFirstName());
    }

    @Test
    @DisplayName("User - isAdmin")
    void userIsAdmin() {
        Role adminRole = new Role("ADMIN", "Admin");
        User admin = User.builder().username("admin").role(adminRole).status(UserStatus.ACTIVE).otpVerified(true).build();
        assertTrue(admin.isAdmin());
        
        Role userRole = new Role("USER", "User");
        User user = User.builder().username("user").role(userRole).status(UserStatus.ACTIVE).otpVerified(true).build();
        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("User - isLockoutExpired")
    void userIsLockoutExpired() {
        User user = User.builder().username("testuser").status(UserStatus.ACTIVE).otpVerified(true).build();
        // New user with no lockoutTime returns false
        assertFalse(user.isLockoutExpired());
        
        // User with expired lockout returns true
        user.setLockoutTime(LocalDateTime.now().minusMinutes(10));
        assertTrue(user.isLockoutExpired());
    }

    @Test
    @DisplayName("User - failed login")
    void userFailedLogin() {
        User user = User.builder().username("testuser").status(UserStatus.ACTIVE).otpVerified(true)
            .failedLoginAttempts(0).build();
        
        user.incrementFailedLoginAttempts();
        assertEquals(1, user.getFailedLoginAttempts());
        
        user.setFailedLoginAttempts(4);
        user.incrementFailedLoginAttempts();
        assertEquals(5, user.getFailedLoginAttempts());
        
        user.resetFailedLoginAttempts();
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    @DisplayName("Role")
    void roleEntity() {
        Role role = new Role();
        role.setId(1L);
        role.setName("USER");
        role.setDescription("Regular user");
        
        assertEquals(1L, role.getId());
        
        role = new Role("ADMIN", "Admin user");
        assertEquals("ADMIN", role.getName());
    }

    @Test
    @DisplayName("Url")
    void urlEntity() {
        User user = User.builder().id(1L).username("testuser").status(UserStatus.ACTIVE).otpVerified(true).build();
        
        Url url = new Url();
        url.setId(1L);
        url.setOriginalUrl("https://example.com");
        url.setShortCode("abc123");
        url.setUser(user);
        url.setAccessCount(10L);
        
        assertEquals(1L, url.getId());
        assertEquals("https://example.com", url.getOriginalUrl());
    }

    @Test
    @DisplayName("VerificationToken")
    void verificationToken() {
        User user = User.builder().id(1L).username("testuser").status(UserStatus.ACTIVE).otpVerified(true).build();
        
        VerificationToken token = new VerificationToken();
        token.setId(1L);
        token.setToken("token123");
        token.setUser(user);
        token.setTokenType("EMAIL_VERIFICATION");
        token.setExpiryTime(LocalDateTime.now().plusHours(1));
        
        assertEquals(1L, token.getId());
        assertFalse(token.isExpired());
        
        token = new VerificationToken(user, "token456", "EMAIL_VERIFICATION", LocalDateTime.now().minusHours(1));
        assertTrue(token.isExpired());
    }

    @Test
    @DisplayName("OtpToken")
    void otpToken() {
        OtpToken token = new OtpToken();
        token.setId(1L);
        token.setEmail("test@example.com");
        token.setOtpCode("123456");
        token.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        
        assertEquals(1L, token.getId());
        assertFalse(token.isExpired());
        
        token = new OtpToken("test2@example.com", "654321", LocalDateTime.now().minusMinutes(10));
        assertTrue(token.isExpired());
    }

    @Test
    @DisplayName("AuditLog")
    void auditLog() {
        User user = User.builder().id(1L).username("testuser").status(UserStatus.ACTIVE).otpVerified(true).build();
        
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setUser(user);
        log.setAction("USER_LOGIN");
        log.setEntityType("USER");
        log.setEntityId(1L);
        log.setDetails("User logged in");
        log.setIpAddress("127.0.0.1");
        log.setUserAgent("Mozilla/5.0");
        
        assertEquals(1L, log.getId());
        assertEquals("USER_LOGIN", log.getAction());
        
        log = new AuditLog(user, "LOGIN", "USER", 1L, "details", "127.0.0.1", "Mozilla");
        assertEquals("LOGIN", log.getAction());
    }

    @Test
    @DisplayName("FeatureFlag")
    void featureFlag() {
        FeatureFlag flag = new FeatureFlag();
        flag.setId(1L);
        flag.setEnabled(true);

        assertEquals(1L, flag.getId());
        assertTrue(flag.isEnabled());

        // Test with Feature and Role entities
        Feature feature = new Feature("TEST_FEATURE", "Test Feature", "Description");
        feature.setId(1L);
        Role role = new Role("USER", "Regular user");
        role.setId(2L);
        
        flag = new FeatureFlag(feature, role, false);
        assertEquals(1L, flag.getFeature().getId());
        assertEquals("TEST_FEATURE", flag.getFeature().getFeatureKey());
        assertEquals("USER", flag.getRole().getName());
        assertFalse(flag.isEnabled());
        
        // Test toggle
        flag.toggle();
        assertTrue(flag.isEnabled());
    }

    @Test
    @DisplayName("Feature")
    void feature() {
        Feature feature = new Feature();
        feature.setId(1L);
        feature.setFeatureKey("TEST");
        feature.setFeatureName("Test Feature");
        feature.setDescription("Description");

        assertEquals(1L, feature.getId());
        assertEquals("TEST", feature.getFeatureKey());
        assertNotNull(feature.getDescription());

        feature = new Feature("KEY", "Name", "Desc");
        assertEquals("KEY", feature.getFeatureKey());
        assertEquals("Name", feature.getFeatureName());
    }

    @Test
    @DisplayName("GlobalFlag")
    void globalFlag() {
        GlobalFlag flag = new GlobalFlag();
        flag.setId(1L);
        flag.setEnabled(true);

        assertEquals(1L, flag.getId());
        assertTrue(flag.isEnabled());

        Feature feature = new Feature("USER_SIGNUP", "User Sign Up", "Description");
        flag = new GlobalFlag(feature, false);
        assertEquals("USER_SIGNUP", flag.getFeature().getFeatureKey());
        assertFalse(flag.isEnabled());
        
        // Test toggle
        flag.toggle();
        assertTrue(flag.isEnabled());
    }

    @Test
    @DisplayName("EmailInvite")
    void emailInvite() {
        EmailInvite invite = new EmailInvite();
        invite.setId(1L);
        invite.setEmail("invite@example.com");
        invite.setToken("token123");
        invite.setStatus(EmailInvite.InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        
        assertEquals(1L, invite.getId());
        assertFalse(invite.isExpired());
    }

    @Test
    @DisplayName("UrlUsageLimit")
    void urlUsageLimit() {
        UrlUsageLimit limit = new UrlUsageLimit();
        limit.setId(1L);
        limit.setUserId(1L);
        limit.setPeriodYear(2024);
        limit.setPeriodMonth(3);
        limit.setDailyCount(10);
        limit.setMonthlyCount(100);
        
        assertEquals(1L, limit.getId());
        assertEquals(1L, limit.getUserId());
        assertEquals(10, limit.getDailyCount());
    }

    @Test
    @DisplayName("UserStatus enum")
    void userStatusEnum() {
        assertEquals(UserStatus.ACTIVE, UserStatus.valueOf("ACTIVE"));
        assertEquals(UserStatus.SUSPENDED, UserStatus.valueOf("SUSPENDED"));
    }

    @Test
    @DisplayName("RoleName enum")
    void roleNameEnum() {
        assertEquals(RoleName.USER, RoleName.valueOf("USER"));
        assertEquals(RoleName.ADMIN, RoleName.valueOf("ADMIN"));
    }
}
