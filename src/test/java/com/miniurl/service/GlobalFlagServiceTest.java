package com.miniurl.service;

import com.miniurl.dto.GlobalFlagDTO;
import com.miniurl.entity.Feature;
import com.miniurl.entity.GlobalFlag;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.repository.GlobalFlagRepository;
import com.miniurl.repository.FeatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalFlagService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalFlagService Tests")
class GlobalFlagServiceTest {

    @Mock
    private GlobalFlagRepository globalFlagRepository;

    @Mock
    private FeatureRepository featureRepository;

    @InjectMocks
    private GlobalFlagService globalFlagService;

    private GlobalFlag testGlobalFlag;
    private Feature testFeature;

    @BeforeEach
    void setUp() {
        testFeature = new Feature("USER_SIGNUP", "User Sign Up", "Allow new user registration");
        testFeature.setId(1L);
        testGlobalFlag = new GlobalFlag(testFeature, true);
        testGlobalFlag.setId(1L);
    }

    @Test
    @DisplayName("getAllGlobalFlags should return list of all global flags")
    void getAllGlobalFlags_ShouldReturnList() {
        // Arrange
        when(globalFlagRepository.findAll()).thenReturn(Arrays.asList(testGlobalFlag));

        // Act
        List<GlobalFlagDTO> result = globalFlagService.getAllGlobalFlags();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(globalFlagRepository).findAll();
    }

    @Test
    @DisplayName("getGlobalFlagById should return global flag when exists")
    void getGlobalFlagById_WhenExists_ReturnsGlobalFlag() {
        // Arrange
        when(globalFlagRepository.findById(1L)).thenReturn(Optional.of(testGlobalFlag));

        // Act
        GlobalFlagDTO result = globalFlagService.getGlobalFlagById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("USER_SIGNUP", result.getFeatureKey());
    }

    @Test
    @DisplayName("getGlobalFlagById should throw exception when not found")
    void getGlobalFlagById_WhenNotExists_ThrowsException() {
        // Arrange
        when(globalFlagRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            globalFlagService.getGlobalFlagById(99L);
        });
    }

    @Test
    @DisplayName("isGlobalFeatureEnabled should return true when enabled")
    void isGlobalFeatureEnabled_WhenEnabled_ReturnsTrue() {
        // Arrange
        when(globalFlagRepository.findByFeatureKey("USER_SIGNUP")).thenReturn(Optional.of(testGlobalFlag));

        // Act
        boolean result = globalFlagService.isGlobalFeatureEnabled("USER_SIGNUP");

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("isGlobalFeatureEnabled should return false when disabled")
    void isGlobalFeatureEnabled_WhenDisabled_ReturnsFalse() {
        // Arrange
        testGlobalFlag.setEnabled(false);
        when(globalFlagRepository.findByFeatureKey("USER_SIGNUP")).thenReturn(Optional.of(testGlobalFlag));

        // Act
        boolean result = globalFlagService.isGlobalFeatureEnabled("USER_SIGNUP");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("isGlobalFeatureEnabled should return false when not found")
    void isGlobalFeatureEnabled_WhenNotFound_ReturnsFalse() {
        // Arrange
        when(globalFlagRepository.findByFeatureKey("NON_EXISTENT")).thenReturn(Optional.empty());

        // Act
        boolean result = globalFlagService.isGlobalFeatureEnabled("NON_EXISTENT");

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("toggleGlobalFlag should toggle enabled state")
    void toggleGlobalFlag_ShouldToggleState() {
        // Arrange
        testGlobalFlag.setEnabled(true);
        when(globalFlagRepository.findById(1L)).thenReturn(Optional.of(testGlobalFlag));
        when(globalFlagRepository.save(any(GlobalFlag.class))).thenReturn(testGlobalFlag);

        // Act
        GlobalFlagDTO result = globalFlagService.toggleGlobalFlag(1L, false);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEnabled());
        verify(globalFlagRepository).save(testGlobalFlag);
    }

    @Test
    @DisplayName("createGlobalFlag should create new global flag")
    void createGlobalFlag_ShouldCreate() {
        // Arrange
        when(featureRepository.findById(1L)).thenReturn(Optional.of(testFeature));
        GlobalFlag newFlag = new GlobalFlag(testFeature, true);
        newFlag.setId(2L);
        when(globalFlagRepository.save(any(GlobalFlag.class))).thenReturn(newFlag);

        // Act
        GlobalFlagDTO result = globalFlagService.createGlobalFlag(1L, true);

        // Assert
        assertNotNull(result);
        verify(globalFlagRepository).save(any(GlobalFlag.class));
    }

    @Test
    @DisplayName("deleteGlobalFlag should delete global flag")
    void deleteGlobalFlag_ShouldDelete() {
        // Arrange
        when(globalFlagRepository.existsById(1L)).thenReturn(true);
        doNothing().when(globalFlagRepository).deleteById(1L);

        // Act
        globalFlagService.deleteGlobalFlag(1L);

        // Assert
        verify(globalFlagRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteGlobalFlag should throw exception when not found")
    void deleteGlobalFlag_WhenNotExists_ThrowsException() {
        // Arrange
        when(globalFlagRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            globalFlagService.deleteGlobalFlag(99L);
        });
    }
}
