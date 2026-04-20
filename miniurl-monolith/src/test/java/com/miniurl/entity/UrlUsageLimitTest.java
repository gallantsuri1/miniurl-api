package com.miniurl.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UrlUsageLimit entity.
 *
 * Tests cover:
 * - Entity creation and builder pattern
 * - Counter increment/reset logic
 * - Automatic timestamp updates
 */
@DisplayName("UrlUsageLimit Entity Tests")
class UrlUsageLimitTest {

    private Long testUserId;
    private int testYear;
    private int testMonth;

    @BeforeEach
    void setUp() {
        testUserId = 1L;
        testYear = 2024;
        testMonth = 3;
    }

    @Test
    @DisplayName("Should create entity with constructor")
    void constructor_shouldCreateEntityWithCorrectValues() {
        // Act
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);

        // Assert
        assertEquals(testUserId, limit.getUserId());
        assertEquals(testYear, limit.getPeriodYear());
        assertEquals(testMonth, limit.getPeriodMonth());
        assertEquals(0, limit.getDailyCount());
        assertEquals(0, limit.getMonthlyCount());
        assertNotNull(limit.getLastResetDate());
        assertNotNull(limit.getUpdatedAt());
    }

    @Test
    @DisplayName("Should create entity with builder")
    void builder_shouldCreateEntityWithCorrectValues() {
        // Arrange
        LocalDateTime customDate = LocalDateTime.of(2024, 1, 1, 12, 0);

        // Act
        UrlUsageLimit limit = UrlUsageLimit.builder()
            .userId(testUserId)
            .periodYear(testYear)
            .periodMonth(testMonth)
            .dailyCount(50)
            .monthlyCount(500)
            .lastResetDate(customDate)
            .updatedAt(customDate)
            .build();

        // Assert
        assertEquals(testUserId, limit.getUserId());
        assertEquals(testYear, limit.getPeriodYear());
        assertEquals(testMonth, limit.getPeriodMonth());
        assertEquals(50, limit.getDailyCount());
        assertEquals(500, limit.getMonthlyCount());
        assertEquals(customDate, limit.getLastResetDate());
        assertEquals(customDate, limit.getUpdatedAt());
    }

    @Test
    @DisplayName("Should increment both daily and monthly counts")
    void increment_shouldIncreaseBothCounters() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);
        limit.setDailyCount(10);
        limit.getMonthlyCount();
        limit.setMonthlyCount(100);

        // Act
        limit.increment();

        // Assert
        assertEquals(11, limit.getDailyCount());
        assertEquals(101, limit.getMonthlyCount());
        assertNotNull(limit.getUpdatedAt());
    }

    @Test
    @DisplayName("Should reset daily count only")
    void resetDailyCount_shouldResetOnlyDailyCounter() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);
        limit.setDailyCount(95);
        limit.setMonthlyCount(500);

        // Act
        limit.resetDailyCount();

        // Assert
        assertEquals(0, limit.getDailyCount());
        assertEquals(500, limit.getMonthlyCount());
        assertNotNull(limit.getUpdatedAt());
    }

    @Test
    @DisplayName("Should reset both daily and monthly counts")
    void resetMonthlyCount_shouldResetBothCounters() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);
        limit.setDailyCount(95);
        limit.setMonthlyCount(999);
        limit.setLastResetDate(LocalDateTime.now().minusMonths(1));

        // Act
        limit.resetMonthlyCount();

        // Assert
        assertEquals(0, limit.getDailyCount());
        assertEquals(0, limit.getMonthlyCount());
        assertNotNull(limit.getLastResetDate());
        assertNotNull(limit.getUpdatedAt());
    }

    @Test
    @DisplayName("Should update timestamp on increment")
    void increment_shouldUpdateTimestamp() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);
        LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(1);
        limit.setUpdatedAt(oldTimestamp);

        // Act
        limit.increment();

        // Assert
        assertTrue(limit.getUpdatedAt().isAfter(oldTimestamp));
    }

    @Test
    @DisplayName("Should update timestamp on daily reset")
    void resetDailyCount_shouldUpdateTimestamp() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);
        LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(1);
        limit.setUpdatedAt(oldTimestamp);

        // Act
        limit.resetDailyCount();

        // Assert
        assertTrue(limit.getUpdatedAt().isAfter(oldTimestamp));
    }

    @Test
    @DisplayName("Should update timestamp on monthly reset")
    void resetMonthlyCount_shouldUpdateTimestamp() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);
        LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(1);
        limit.setUpdatedAt(oldTimestamp);

        // Act
        limit.resetMonthlyCount();

        // Assert
        assertTrue(limit.getUpdatedAt().isAfter(oldTimestamp));
    }

    @Test
    @DisplayName("Should set and get ID correctly")
    void setIdAndGetId_shouldWorkCorrectly() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit();

        // Act
        limit.setId(123L);

        // Assert
        assertEquals(123L, limit.getId());
    }

    @Test
    @DisplayName("Should set and get all properties correctly")
    void settersAndGetters_shouldWorkCorrectly() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit();
        LocalDateTime testDate = LocalDateTime.of(2024, 6, 15, 10, 30);

        // Act & Assert
        limit.setUserId(testUserId);
        assertEquals(testUserId, limit.getUserId());

        limit.setPeriodYear(2025);
        assertEquals(2025, limit.getPeriodYear());

        limit.setPeriodMonth(12);
        assertEquals(12, limit.getPeriodMonth());

        limit.setDailyCount(75);
        assertEquals(75, limit.getDailyCount());

        limit.setMonthlyCount(750);
        assertEquals(750, limit.getMonthlyCount());

        limit.setLastResetDate(testDate);
        assertEquals(testDate, limit.getLastResetDate());

        limit.setUpdatedAt(testDate);
        assertEquals(testDate, limit.getUpdatedAt());
    }

    @Test
    @DisplayName("Should handle multiple increments")
    void multipleIncrements_shouldAccumulateCorrectly() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);

        // Act
        for (int i = 0; i < 50; i++) {
            limit.increment();
        }

        // Assert
        assertEquals(50, limit.getDailyCount());
        assertEquals(50, limit.getMonthlyCount());
    }

    @Test
    @DisplayName("Should handle reset after increments")
    void resetAfterIncrements_shouldResetCorrectly() {
        // Arrange
        UrlUsageLimit limit = new UrlUsageLimit(testUserId, testYear, testMonth);

        // Act - increment then reset
        for (int i = 0; i < 25; i++) {
            limit.increment();
        }
        limit.resetDailyCount();

        // Assert
        assertEquals(0, limit.getDailyCount());
        assertEquals(25, limit.getMonthlyCount()); // Monthly should not be affected
    }

    @Test
    @DisplayName("Builder should handle default values correctly")
    void builder_withDefaultValues_shouldHandleCorrectly() {
        // Act
        UrlUsageLimit limit = UrlUsageLimit.builder()
            .userId(testUserId)
            .periodYear(testYear)
            .periodMonth(testMonth)
            .build();

        // Assert - defaults should be applied
        assertEquals(0, limit.getDailyCount());
        assertEquals(0, limit.getMonthlyCount());
        // lastResetDate is set in constructor via @PrePersist, not in builder
    }

    @Test
    @DisplayName("Should create entity with default constructor")
    void defaultConstructor_shouldCreateEmptyEntity() {
        // Act
        UrlUsageLimit limit = new UrlUsageLimit();

        // Assert - should not throw, fields will be null/zero
        assertNotNull(limit);
    }
}
