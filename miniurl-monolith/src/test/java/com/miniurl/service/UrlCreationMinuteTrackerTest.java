package com.miniurl.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UrlCreationMinuteTracker.
 *
 * Tests cover:
 * - Incrementing URL creation counts
 * - Retrieving counts for the current minute
 * - Cleanup of old entries
 */
@DisplayName("UrlCreationMinuteTracker Unit Tests")
class UrlCreationMinuteTrackerTest {

    private UrlCreationMinuteTracker minuteTracker;

    @BeforeEach
    void setUp() {
        minuteTracker = new UrlCreationMinuteTracker();
    }

    @Test
    @DisplayName("Should increment count for user")
    void increment_shouldIncreaseCountForUser() {
        // Arrange
        Long userId = 1L;

        // Act
        minuteTracker.increment(userId);
        minuteTracker.increment(userId);
        minuteTracker.increment(userId);

        // Assert
        assertEquals(3, minuteTracker.getCountForLastMinute(userId));
    }

    @Test
    @DisplayName("Should track counts separately for different users")
    void increment_shouldTrackCountsSeparatelyForDifferentUsers() {
        // Arrange
        Long user1 = 1L;
        Long user2 = 2L;

        // Act
        minuteTracker.increment(user1);
        minuteTracker.increment(user1);
        minuteTracker.increment(user2);

        // Assert
        assertEquals(2, minuteTracker.getCountForLastMinute(user1));
        assertEquals(1, minuteTracker.getCountForLastMinute(user2));
    }

    @Test
    @DisplayName("Should return 0 for user with no counts")
    void getCountForLastMinute_whenNoCounts_shouldReturnZero() {
        // Arrange
        Long userId = 999L;

        // Act & Assert
        assertEquals(0, minuteTracker.getCountForLastMinute(userId));
    }

    @Test
    @DisplayName("Should handle multiple increments for same user")
    void increment_multipleTimes_shouldAccumulateCorrectly() {
        // Arrange
        Long userId = 1L;
        int increments = 100;

        // Act
        for (int i = 0; i < increments; i++) {
            minuteTracker.increment(userId);
        }

        // Assert
        assertEquals(increments, minuteTracker.getCountForLastMinute(userId));
    }

    @Test
    @DisplayName("Should cleanup old entries")
    void cleanupOldEntries_shouldRemoveOldEntries() throws Exception {
        // Arrange
        Long userId = 1L;
        
        // Access the internal map to simulate old entries
        Field field = UrlCreationMinuteTracker.class.getDeclaredField("userMinuteCounts");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Map<Long, AtomicInteger>> userMinuteCounts = 
            (Map<Long, Map<Long, AtomicInteger>>) field.get(minuteTracker);
        
        // Add an old entry (5 minutes ago)
        long oldMinute = (System.currentTimeMillis() / 60000) - 5;
        Map<Long, AtomicInteger> minuteMap = userMinuteCounts.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        minuteMap.put(oldMinute, new AtomicInteger(5));

        // Act
        minuteTracker.cleanupOldEntries();

        // Assert - old entry should be removed (and user map may be removed if empty)
        Map<Long, AtomicInteger> userMap = userMinuteCounts.get(userId);
        if (userMap != null) {
            assertFalse(userMap.containsKey(oldMinute));
        }
        // If userMap is null, that's also correct because the entire user entry was cleaned up
    }

    @Test
    @DisplayName("Should keep recent entries after cleanup")
    void cleanupOldEntries_shouldKeepRecentEntries() throws Exception {
        // Arrange
        Long userId = 1L;
        
        // Add current minute count
        minuteTracker.increment(userId);
        minuteTracker.increment(userId);
        
        // Access the internal map
        Field field = UrlCreationMinuteTracker.class.getDeclaredField("userMinuteCounts");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Map<Long, AtomicInteger>> userMinuteCounts = 
            (Map<Long, Map<Long, AtomicInteger>>) field.get(minuteTracker);

        // Act
        minuteTracker.cleanupOldEntries();

        // Assert - current minute entry should still exist
        Map<Long, AtomicInteger> userMap = userMinuteCounts.get(userId);
        assertNotNull(userMap);
        assertEquals(2, minuteTracker.getCountForLastMinute(userId));
    }

    @Test
    @DisplayName("Should remove empty user maps after cleanup")
    void cleanupOldEntries_shouldRemoveEmptyUserMaps() throws Exception {
        // Arrange
        Long userId = 1L;
        
        // Access the internal map
        Field field = UrlCreationMinuteTracker.class.getDeclaredField("userMinuteCounts");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, Map<Long, AtomicInteger>> userMinuteCounts = 
            (Map<Long, Map<Long, AtomicInteger>>) field.get(minuteTracker);
        
        // Add only old entry
        long oldMinute = (System.currentTimeMillis() / 60000) - 5;
        Map<Long, AtomicInteger> minuteMap = new ConcurrentHashMap<>();
        minuteMap.put(oldMinute, new AtomicInteger(5));
        userMinuteCounts.put(userId, minuteMap);

        // Act
        minuteTracker.cleanupOldEntries();

        // Assert - user map should be removed since it's now empty
        assertFalse(userMinuteCounts.containsKey(userId));
    }

    @Test
    @DisplayName("Should handle concurrent increments safely")
    void increment_concurrentAccess_shouldBeThreadSafe() throws Exception {
        // Arrange
        Long userId = 1L;
        int threadCount = 10;
        int incrementsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // Act - create multiple threads that increment concurrently
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    minuteTracker.increment(userId);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert
        assertEquals(threadCount * incrementsPerThread, minuteTracker.getCountForLastMinute(userId));
    }

    @Test
    @DisplayName("Should track minute-level granularity")
    void increment_shouldTrackByMinute() {
        // Arrange
        Long userId = 1L;
        
        // Act - increment multiple times within the same minute
        for (int i = 0; i < 5; i++) {
            minuteTracker.increment(userId);
        }

        // Assert - all increments should be counted in the same minute
        assertEquals(5, minuteTracker.getCountForLastMinute(userId));
    }
}
