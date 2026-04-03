package com.miniurl.service;

import com.miniurl.dto.FeatureFlagDTO;
import com.miniurl.entity.Feature;
import com.miniurl.entity.Role;
import com.miniurl.entity.FeatureFlag;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.repository.FeatureFlagRepository;
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
 * Unit tests for FeatureFlagService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureFlagService Tests")
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @InjectMocks
    private FeatureFlagService featureFlagService;

    private FeatureFlag testFeatureFlag;
    private Feature testFeature;
    private Role testRole;

    @BeforeEach
    void setUp() {
        testFeature = new Feature("DASHBOARD", "Dashboard", "User dashboard access");
        testFeature.setId(5L);
        testRole = new Role("USER", "Regular user");
        testRole.setId(2L);
        testFeatureFlag = new FeatureFlag(testFeature, testRole, true);
        testFeatureFlag.setId(1L);
    }

    @Test
    @DisplayName("isFeatureEnabled should return true when feature is enabled")
    void isFeatureEnabled_WhenEnabled_ReturnsTrue() {
        // Arrange
        when(featureFlagRepository.findByFeatureKeyAndRoleId("DASHBOARD", 2L)).thenReturn(Optional.of(testFeatureFlag));

        // Act
        boolean result = featureFlagService.isFeatureEnabled("DASHBOARD", 2L);

        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("isFeatureEnabled should return false when feature is disabled")
    void isFeatureEnabled_WhenDisabled_ReturnsFalse() {
        // Arrange
        testFeatureFlag.setEnabled(false);
        when(featureFlagRepository.findByFeatureKeyAndRoleId("DASHBOARD", 2L)).thenReturn(Optional.of(testFeatureFlag));

        // Act
        boolean result = featureFlagService.isFeatureEnabled("DASHBOARD", 2L);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("getAllFeatures should return list of all feature flags")
    void getAllFeatures_ShouldReturnList() {
        // Arrange
        when(featureFlagRepository.findAllWithFeatures()).thenReturn(Arrays.asList(testFeatureFlag));

        // Act
        List<FeatureFlagDTO> result = featureFlagService.getAllFeatures();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getFeaturesByRole should return features for specific role")
    void getFeaturesByRole_ShouldReturnRoleFeatures() {
        // Arrange
        when(featureFlagRepository.findByRoleIdOrderByFeatureFeatureNameAsc(2L)).thenReturn(Arrays.asList(testFeatureFlag));

        // Act
        List<FeatureFlagDTO> result = featureFlagService.getFeaturesByRole(2L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getFeatureFlagById should return feature flag when exists")
    void getFeatureFlagById_WhenExists_ReturnsFeatureFlag() {
        // Arrange
        when(featureFlagRepository.findById(1L)).thenReturn(Optional.of(testFeatureFlag));

        // Act
        FeatureFlagDTO result = featureFlagService.getFeatureFlagById(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    @DisplayName("getFeatureFlagById should throw exception when not found")
    void getFeatureFlagById_WhenNotExists_ThrowsException() {
        // Arrange
        when(featureFlagRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            featureFlagService.getFeatureFlagById(99L);
        });
    }

    @Test
    @DisplayName("toggleFeature should toggle enabled state")
    void toggleFeature_ShouldToggle() {
        // Arrange
        testFeatureFlag.setEnabled(true);
        when(featureFlagRepository.findById(1L)).thenReturn(Optional.of(testFeatureFlag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(testFeatureFlag);

        // Act
        FeatureFlagDTO result = featureFlagService.toggleFeature(1L, false);

        // Assert
        assertNotNull(result);
        verify(featureFlagRepository).save(testFeatureFlag);
    }

    @Test
    @DisplayName("createFeatureFlag should create new feature flag")
    void createFeatureFlag_ShouldCreate() {
        // Arrange
        when(featureFlagRepository.findById(5L)).thenReturn(Optional.of(testFeatureFlag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(testFeatureFlag);

        // Act
        FeatureFlagDTO result = featureFlagService.createFeatureFlag(5L, 2L, true);

        // Assert
        assertNotNull(result);
        verify(featureFlagRepository).save(any(FeatureFlag.class));
    }

    @Test
    @DisplayName("deleteFeatureFlag should delete feature flag")
    void deleteFeatureFlag_ShouldDelete() {
        // Arrange
        when(featureFlagRepository.existsById(1L)).thenReturn(true);
        doNothing().when(featureFlagRepository).deleteById(1L);

        // Act
        featureFlagService.deleteFeatureFlag(1L);

        // Assert
        verify(featureFlagRepository).deleteById(1L);
    }
}
