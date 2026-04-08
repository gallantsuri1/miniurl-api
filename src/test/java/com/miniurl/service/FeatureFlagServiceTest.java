package com.miniurl.service;

import com.miniurl.dto.FeatureFlagDTO;
import com.miniurl.entity.Feature;
import com.miniurl.entity.Role;
import com.miniurl.entity.FeatureFlag;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.repository.FeatureFlagRepository;
import com.miniurl.repository.FeatureRepository;
import com.miniurl.repository.RoleRepository;
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

    @Mock
    private FeatureRepository featureRepository;

    @Mock
    private RoleRepository roleRepository;

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
    @DisplayName("createFeatureFlag with featureKey should create feature and role flags")
    void createFeatureFlag_WithFeatureKey_ShouldCreateFeatureAndRoleFlags() {
        // Arrange
        Role adminRole = new Role("ADMIN", "Administrator");
        adminRole.setId(1L);
        Role userRole = new Role("USER", "Regular user");
        userRole.setId(2L);
        Feature newFeature = new Feature("TEST_FEATURE", "Test Feature", "Test description");
        newFeature.setId(10L);
        FeatureFlag adminFlag = new FeatureFlag(newFeature, adminRole, true);
        adminFlag.setId(20L);
        FeatureFlag userFlag = new FeatureFlag(newFeature, userRole, false);
        userFlag.setId(21L);

        when(featureRepository.findByFeatureKey("TEST_FEATURE")).thenReturn(Optional.empty());
        when(featureRepository.save(any(Feature.class))).thenReturn(newFeature);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(featureFlagRepository.findByFeatureKeyAndRoleId("TEST_FEATURE", 1L)).thenReturn(Optional.empty());
        when(featureFlagRepository.findByFeatureKeyAndRoleId("TEST_FEATURE", 2L)).thenReturn(Optional.empty());
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(userFlag);

        // Act
        FeatureFlagDTO result = featureFlagService.createFeatureFlag("TEST_FEATURE", "Test Feature", "Test description", true, false);

        // Assert
        assertNotNull(result);
        verify(featureRepository).save(any(Feature.class));
        verify(featureFlagRepository, times(2)).save(any(FeatureFlag.class));
    }

    @Test
    @DisplayName("createFeatureFlag with featureKey should update existing feature flags")
    void createFeatureFlag_WithFeatureKey_ShouldUpdateExistingFlags() {
        // Arrange
        Role adminRole = new Role("ADMIN", "Administrator");
        adminRole.setId(1L);
        Role userRole = new Role("USER", "Regular user");
        userRole.setId(2L);
        FeatureFlag existingAdminFlag = new FeatureFlag(testFeature, adminRole, false);
        existingAdminFlag.setId(30L);
        FeatureFlag existingUserFlag = new FeatureFlag(testFeature, userRole, false);
        existingUserFlag.setId(31L);

        when(featureRepository.findByFeatureKey("DASHBOARD")).thenReturn(Optional.of(testFeature));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(featureFlagRepository.findByFeatureKeyAndRoleId("DASHBOARD", 1L)).thenReturn(Optional.of(existingAdminFlag));
        when(featureFlagRepository.findByFeatureKeyAndRoleId("DASHBOARD", 2L)).thenReturn(Optional.of(existingUserFlag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(existingUserFlag);

        // Act
        FeatureFlagDTO result = featureFlagService.createFeatureFlag("DASHBOARD", "Dashboard", "Dashboard access", true, true);

        // Assert
        assertNotNull(result);
        assertTrue(existingAdminFlag.isEnabled());
        assertTrue(existingUserFlag.isEnabled());
        verify(featureRepository, never()).save(any(Feature.class));
        verify(featureFlagRepository, times(2)).save(any(FeatureFlag.class));
    }

    @Test
    @DisplayName("createFeatureFlag with featureKey should throw when role not found")
    void createFeatureFlag_WithFeatureKey_ShouldThrowWhenRoleNotFound() {
        // Arrange
        when(featureRepository.findByFeatureKey("TEST_FEATURE")).thenReturn(Optional.empty());
        when(featureRepository.save(any(Feature.class))).thenReturn(testFeature);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            featureFlagService.createFeatureFlag("TEST_FEATURE", "Test", "Test", true, true);
        });
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
