package com.miniurl.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for EmailInvite entity.
 * Tests cover all entity methods, lifecycle callbacks, and edge cases.
 */
@DisplayName("EmailInvite Entity Tests")
class EmailInviteTest {

    private EmailInvite emailInvite;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_TOKEN = "testToken123456789012345678901234";
    private static final String INVITER = "admin";

    @BeforeEach
    void setUp() {
        emailInvite = new EmailInvite(TEST_EMAIL, TEST_TOKEN, INVITER);
    }

    // ==================== CONSTRUCTOR TESTS ====================

    @Test
    @DisplayName("Constructor should initialize with default values")
    void constructor_ShouldInitializeWithDefaults() {
        // Assert
        assertEquals(TEST_EMAIL, emailInvite.getEmail());
        assertEquals(TEST_TOKEN, emailInvite.getToken());
        assertEquals(INVITER, emailInvite.getInvitedBy());
        assertEquals(EmailInvite.InviteStatus.PENDING, emailInvite.getStatus());
        assertNull(emailInvite.getId());
        assertNull(emailInvite.getAcceptedAt());
    }

    @Test
    @DisplayName("Default constructor should create empty invite")
    void defaultConstructor_ShouldCreateEmptyInvite() {
        // Arrange
        EmailInvite invite = new EmailInvite();

        // Assert
        assertNull(invite.getId());
        assertNull(invite.getEmail());
        assertNull(invite.getToken());
        assertNull(invite.getStatus());
    }

    // ==================== LIFECYCLE CALLBACK TESTS ====================

    @Test
    @DisplayName("onCreate should set createdAt and expiresAt")
    void onCreate_ShouldSetTimestamps() {
        // Arrange
        LocalDateTime beforeCreate = LocalDateTime.now();

        // Act
        emailInvite.onCreate();

        // Assert
        assertNotNull(emailInvite.getCreatedAt());
        assertNotNull(emailInvite.getExpiresAt());
        assertTrue(emailInvite.getCreatedAt().isAfter(beforeCreate.minusSeconds(1)));
        assertTrue(emailInvite.getCreatedAt().isBefore(beforeCreate.plusSeconds(1)));
        // Expires in 7 days
        assertTrue(emailInvite.getExpiresAt().isAfter(emailInvite.getCreatedAt().plusDays(6)));
        assertTrue(emailInvite.getExpiresAt().isBefore(emailInvite.getCreatedAt().plusDays(8)));
    }

    // ==================== IS EXPIRED TESTS ====================

    @Test
    @DisplayName("isExpired should return true when expiresAt is in past")
    void isExpired_WhenPast_ShouldReturnTrue() {
        // Arrange
        emailInvite.setExpiresAt(LocalDateTime.now().minusDays(1));

        // Assert
        assertTrue(emailInvite.isExpired());
    }

    @Test
    @DisplayName("isExpired should return false when expiresAt is in future")
    void isExpired_WhenFuture_ShouldReturnFalse() {
        // Arrange
        emailInvite.setExpiresAt(LocalDateTime.now().plusDays(1));

        // Assert
        assertFalse(emailInvite.isExpired());
    }

    @Test
    @DisplayName("isExpired should return true when expiresAt is now or in past")
    void isExpired_WhenExactlyNow_ShouldReturnTrue() {
        // Arrange
        emailInvite.setExpiresAt(LocalDateTime.now());

        // Assert - expiresAt is "now", so it's considered expired
        assertTrue(emailInvite.isExpired());
    }

    @Test
    @DisplayName("getExpired should return same value as isExpired")
    void getExpired_ShouldReturnIsExpired() {
        // Arrange
        emailInvite.setExpiresAt(LocalDateTime.now().minusDays(1));

        // Assert
        assertTrue(emailInvite.getExpired());

        // Arrange
        emailInvite.setExpiresAt(LocalDateTime.now().plusDays(1));

        // Assert
        assertFalse(emailInvite.getExpired());
    }

    // ==================== ACCEPT TESTS ====================

    @Test
    @DisplayName("accept should set status to ACCEPTED when pending and not expired")
    void accept_WhenPendingAndNotExpired_ShouldAccept() {
        // Arrange
        emailInvite.setStatus(EmailInvite.InviteStatus.PENDING);
        emailInvite.setExpiresAt(LocalDateTime.now().plusDays(1));

        // Act
        emailInvite.accept();

        // Assert
        assertEquals(EmailInvite.InviteStatus.ACCEPTED, emailInvite.getStatus());
        assertNotNull(emailInvite.getAcceptedAt());
        assertTrue(emailInvite.getAcceptedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("accept should not change status when already accepted")
    void accept_WhenAlreadyAccepted_ShouldNotChange() {
        // Arrange
        emailInvite.setStatus(EmailInvite.InviteStatus.ACCEPTED);
        LocalDateTime originalAcceptedAt = LocalDateTime.now().minusDays(1);
        emailInvite.setAcceptedAt(originalAcceptedAt);

        // Act
        emailInvite.accept();

        // Assert
        assertEquals(EmailInvite.InviteStatus.ACCEPTED, emailInvite.getStatus());
        assertEquals(originalAcceptedAt, emailInvite.getAcceptedAt());
    }

    @Test
    @DisplayName("accept should not change status when expired")
    void accept_WhenExpired_ShouldNotAccept() {
        // Arrange
        emailInvite.setStatus(EmailInvite.InviteStatus.PENDING);
        emailInvite.setExpiresAt(LocalDateTime.now().minusDays(1));

        // Act
        emailInvite.accept();

        // Assert
        assertEquals(EmailInvite.InviteStatus.PENDING, emailInvite.getStatus());
        assertNull(emailInvite.getAcceptedAt());
    }

    @Test
    @DisplayName("accept should not change status when revoked")
    void accept_WhenRevoked_ShouldNotAccept() {
        // Arrange
        emailInvite.setStatus(EmailInvite.InviteStatus.REVOKED);
        emailInvite.setExpiresAt(LocalDateTime.now().plusDays(1));

        // Act
        emailInvite.accept();

        // Assert
        assertEquals(EmailInvite.InviteStatus.REVOKED, emailInvite.getStatus());
        assertNull(emailInvite.getAcceptedAt());
    }

    // ==================== REVOKE TESTS ====================

    @Test
    @DisplayName("revoke should set status to REVOKED")
    void revoke_ShouldSetStatusToRevoked() {
        // Arrange
        emailInvite.setStatus(EmailInvite.InviteStatus.PENDING);

        // Act
        emailInvite.revoke();

        // Assert
        assertEquals(EmailInvite.InviteStatus.REVOKED, emailInvite.getStatus());
    }

    @Test
    @DisplayName("revoke should work regardless of current status")
    void revoke_FromAnyStatus_ShouldRevoke() {
        // Test from PENDING
        emailInvite.setStatus(EmailInvite.InviteStatus.PENDING);
        emailInvite.revoke();
        assertEquals(EmailInvite.InviteStatus.REVOKED, emailInvite.getStatus());

        // Test from ACCEPTED
        emailInvite.setStatus(EmailInvite.InviteStatus.ACCEPTED);
        emailInvite.revoke();
        assertEquals(EmailInvite.InviteStatus.REVOKED, emailInvite.getStatus());

        // Test from EXPIRED
        emailInvite.setStatus(EmailInvite.InviteStatus.EXPIRED);
        emailInvite.revoke();
        assertEquals(EmailInvite.InviteStatus.REVOKED, emailInvite.getStatus());
    }

    // ==================== GETTER/SETTER TESTS ====================

    @Test
    @DisplayName("setId should set id")
    void setId_ShouldSetId() {
        // Act
        emailInvite.setId(100L);

        // Assert
        assertEquals(100L, emailInvite.getId());
    }

    @Test
    @DisplayName("setEmail should set email")
    void setEmail_ShouldSetEmail() {
        // Act
        emailInvite.setEmail("new@example.com");

        // Assert
        assertEquals("new@example.com", emailInvite.getEmail());
    }

    @Test
    @DisplayName("setToken should set token")
    void setToken_ShouldSetToken() {
        // Act
        emailInvite.setToken("newToken");

        // Assert
        assertEquals("newToken", emailInvite.getToken());
    }

    @Test
    @DisplayName("setStatus should set status")
    void setStatus_ShouldSetStatus() {
        // Act
        emailInvite.setStatus(EmailInvite.InviteStatus.ACCEPTED);

        // Assert
        assertEquals(EmailInvite.InviteStatus.ACCEPTED, emailInvite.getStatus());
    }

    @Test
    @DisplayName("setInvitedBy should set invitedBy")
    void setInvitedBy_ShouldSetInvitedBy() {
        // Act
        emailInvite.setInvitedBy("newAdmin");

        // Assert
        assertEquals("newAdmin", emailInvite.getInvitedBy());
    }

    @Test
    @DisplayName("setCreatedAt should set createdAt")
    void setCreatedAt_ShouldSetCreatedAt() {
        // Arrange
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        // Act
        emailInvite.setCreatedAt(testTime);

        // Assert
        assertEquals(testTime, emailInvite.getCreatedAt());
    }

    @Test
    @DisplayName("setExpiresAt should set expiresAt")
    void setExpiresAt_ShouldSetExpiresAt() {
        // Arrange
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 8, 12, 0);

        // Act
        emailInvite.setExpiresAt(testTime);

        // Assert
        assertEquals(testTime, emailInvite.getExpiresAt());
    }

    @Test
    @DisplayName("setAcceptedAt should set acceptedAt")
    void setAcceptedAt_ShouldSetAcceptedAt() {
        // Arrange
        LocalDateTime testTime = LocalDateTime.of(2024, 1, 1, 12, 0);

        // Act
        emailInvite.setAcceptedAt(testTime);

        // Assert
        assertEquals(testTime, emailInvite.getAcceptedAt());
    }

    // ==================== TOSTRING TESTS ====================

    @Test
    @DisplayName("toString should return formatted string")
    void toString_ShouldReturnFormattedString() {
        // Arrange
        emailInvite.setId(1L);
        emailInvite.setStatus(EmailInvite.InviteStatus.PENDING);

        // Act
        String result = emailInvite.toString();

        // Assert
        assertTrue(result.contains("EmailInvite"));
        assertTrue(result.contains(TEST_EMAIL));
        assertTrue(result.contains("PENDING"));
    }

    @Test
    @DisplayName("toString should include id and email")
    void toString_ShouldIncludeKeyFields() {
        // Arrange
        emailInvite.setId(1L);
        emailInvite.setEmail("test@example.com");

        // Act
        String result = emailInvite.toString();

        // Assert
        assertTrue(result.contains("1"));
        assertTrue(result.contains("test@example.com"));
    }

    // ==================== INVITE STATUS ENUM TESTS ====================

    @Test
    @DisplayName("InviteStatus enum should have all expected values")
    void inviteStatusEnum_ShouldHaveAllValues() {
        // Assert
        assertEquals(4, EmailInvite.InviteStatus.values().length);
        assertEquals(EmailInvite.InviteStatus.PENDING, EmailInvite.InviteStatus.valueOf("PENDING"));
        assertEquals(EmailInvite.InviteStatus.ACCEPTED, EmailInvite.InviteStatus.valueOf("ACCEPTED"));
        assertEquals(EmailInvite.InviteStatus.EXPIRED, EmailInvite.InviteStatus.valueOf("EXPIRED"));
        assertEquals(EmailInvite.InviteStatus.REVOKED, EmailInvite.InviteStatus.valueOf("REVOKED"));
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Invite with null email should still work")
    void inviteWithNullEmail_ShouldStillWork() {
        // Arrange
        EmailInvite invite = new EmailInvite(null, TEST_TOKEN, INVITER);

        // Assert
        assertNull(invite.getEmail());
        assertEquals(TEST_TOKEN, invite.getToken());
        assertEquals(INVITER, invite.getInvitedBy());
    }

    @Test
    @DisplayName("Invite with very long token should work")
    void inviteWithLongToken_ShouldWork() {
        // Arrange
        String longToken = "a".repeat(1000);
        EmailInvite invite = new EmailInvite(TEST_EMAIL, longToken, INVITER);

        // Assert
        assertEquals(longToken, invite.getToken());
    }

    @Test
    @DisplayName("Multiple accept calls should not change acceptedAt")
    void multipleAcceptCalls_ShouldNotChangeAcceptedAt() {
        // Arrange
        emailInvite.setStatus(EmailInvite.InviteStatus.PENDING);
        emailInvite.setExpiresAt(LocalDateTime.now().plusDays(1));

        // Act - First accept
        emailInvite.accept();
        LocalDateTime firstAcceptedAt = emailInvite.getAcceptedAt();

        // Wait a bit
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act - Second accept
        emailInvite.accept();

        // Assert
        assertEquals(firstAcceptedAt, emailInvite.getAcceptedAt());
    }
}
