package com.miniurl.service;

import com.miniurl.entity.Url;
import com.miniurl.entity.User;
import com.miniurl.exception.UrlLimitExceededException;
import com.miniurl.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UrlUsageLimitService URL creation limit enforcement.
 * 
 * Tests cover:
 * - Monthly limit enforcement (1000 URLs)
 * - Daily limit enforcement (100 URLs)
 * - Minute limit enforcement (10 URLs)
 * - Cascading order (monthly > daily > minute)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UrlUsageLimitService - Limit Enforcement Tests")
class UrlUsageLimitServiceLimitTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private UrlCreationTracker minuteTracker;

    private UrlUsageLimitService urlUsageLimitService;

    private final Long TEST_USER_ID = 1L;
    private final int CURRENT_YEAR = LocalDate.now().getYear();
    private final int CURRENT_MONTH = LocalDate.now().getMonthValue();

    @BeforeEach
    void setUp() {
        urlUsageLimitService = new UrlUsageLimitService(urlRepository, minuteTracker);
    }

    @Test
    @DisplayName("Should allow URL creation when under all limits")
    void checkAndIncrementUrlCreation_whenUnderAllLimits_shouldSucceed() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(5);

        // Act & Assert - Should not throw
        assertDoesNotThrow(() -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID));
        
        // Verify minute tracker was incremented
        verify(minuteTracker, times(1)).increment(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should throw exception when monthly limit reached - cascading order test")
    void checkAndIncrementUrlCreation_whenMonthlyLimitReached_shouldThrowMonthlyException() {
        // Arrange - Monthly limit exceeded (1001 > 1000), but daily and minute are under
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(1001);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(5);

        // Act & Assert - Should throw monthly limit exception (highest priority)
        UrlLimitExceededException exception = assertThrows(
            UrlLimitExceededException.class,
            () -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID)
        );

        assertEquals("per month", exception.getLimitType());
        assertEquals(1000, exception.getLimit());
        assertEquals(1001, exception.getCurrentCount());
        assertEquals("Your monthly limit quota reached, please try next month", exception.getUiMessage());
        
        // Verify minute tracker was NOT incremented
        verify(minuteTracker, never()).increment(anyLong());
    }

    @Test
    @DisplayName("Should throw daily limit exception when monthly OK but daily exceeded - cascading order test")
    void checkAndIncrementUrlCreation_whenDailyLimitReached_shouldThrowDailyException() {
        // Arrange - Monthly OK (500 < 1000), daily exceeded (101 > 100), minute OK
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(101);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(5);

        // Act & Assert - Should throw daily limit exception (second priority)
        UrlLimitExceededException exception = assertThrows(
            UrlLimitExceededException.class,
            () -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID)
        );

        assertEquals("per day", exception.getLimitType());
        assertEquals(100, exception.getLimit());
        assertEquals(101, exception.getCurrentCount());
        assertEquals("Your daily limit quota reached, please try tomorrow", exception.getUiMessage());
        
        // Verify minute tracker was NOT incremented
        verify(minuteTracker, never()).increment(anyLong());
    }

    @Test
    @DisplayName("Should throw minute limit exception when monthly and daily OK but minute exceeded - cascading order test")
    void checkAndIncrementUrlCreation_whenMinuteLimitReached_shouldThrowMinuteException() {
        // Arrange - Monthly OK (500 < 1000), daily OK (50 < 100), minute exceeded (11 > 10)
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(11);

        // Act & Assert - Should throw minute limit exception (lowest priority)
        UrlLimitExceededException exception = assertThrows(
            UrlLimitExceededException.class,
            () -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID)
        );

        assertEquals("per minute", exception.getLimitType());
        assertEquals(10, exception.getLimit());
        assertEquals(11, exception.getCurrentCount());
        assertEquals("You created 10 in last minute, please wait another minute", exception.getUiMessage());
        
        // Verify minute tracker was NOT incremented
        verify(minuteTracker, never()).increment(anyLong());
    }

    @Test
    @DisplayName("Should correctly count URLs from database for monthly limit")
    void checkAndIncrementUrlCreation_shouldCountMonthlyUrlsFromDatabase() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(999);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(5);

        // Act
        urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID);

        // Assert - Verify database was queried for monthly count
        verify(urlRepository, times(1)).countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH);
        verify(minuteTracker, times(1)).increment(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should correctly count URLs from database for daily limit")
    void checkAndIncrementUrlCreation_shouldCountDailyUrlsFromDatabase() {
        // Arrange
        LocalDate today = LocalDate.now();
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, today)).thenReturn(99);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(5);

        // Act
        urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID);

        // Assert - Verify database was queried for daily count
        verify(urlRepository, times(1)).countByUserIdAndDay(TEST_USER_ID, today);
        verify(minuteTracker, times(1)).increment(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should get minute count from tracker")
    void checkAndIncrementUrlCreation_shouldGetMinuteCountFromTracker() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(9);

        // Act
        urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID);

        // Assert - Verify tracker was queried
        verify(minuteTracker, times(1)).getCountForLastMinute(TEST_USER_ID);
        verify(minuteTracker, times(1)).increment(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should increment minute tracker after successful check")
    void checkAndIncrementUrlCreation_shouldIncrementMinuteTracker() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(5);

        // Act
        urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID);

        // Assert - Verify tracker was incremented
        verify(minuteTracker, times(1)).increment(TEST_USER_ID);
    }

    @Test
    @DisplayName("Should NOT increment tracker when monthly limit reached")
    void checkAndIncrementUrlCreation_shouldNotIncrementTracker_whenMonthlyLimitReached() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(1001);

        // Act & Assert
        assertThrows(UrlLimitExceededException.class, 
            () -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID));

        // Verify tracker was NOT incremented
        verify(minuteTracker, never()).increment(anyLong());
    }

    @Test
    @DisplayName("Should NOT increment tracker when daily limit reached")
    void checkAndIncrementUrlCreation_shouldNotIncrementTracker_whenDailyLimitReached() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(101);

        // Act & Assert
        assertThrows(UrlLimitExceededException.class, 
            () -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID));

        // Verify tracker was NOT incremented
        verify(minuteTracker, never()).increment(anyLong());
    }

    @Test
    @DisplayName("Should NOT increment tracker when minute limit reached")
    void checkAndIncrementUrlCreation_shouldNotIncrementTracker_whenMinuteLimitReached() {
        // Arrange
        when(urlRepository.countByUserIdAndMonth(TEST_USER_ID, CURRENT_YEAR, CURRENT_MONTH)).thenReturn(500);
        when(urlRepository.countByUserIdAndDay(TEST_USER_ID, LocalDate.now())).thenReturn(50);
        when(minuteTracker.getCountForLastMinute(TEST_USER_ID)).thenReturn(11);

        // Act & Assert
        assertThrows(UrlLimitExceededException.class, 
            () -> urlUsageLimitService.checkAndIncrementUrlCreation(TEST_USER_ID));

        // Verify tracker was NOT incremented
        verify(minuteTracker, never()).increment(anyLong());
    }
}
