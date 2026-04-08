package com.miniurl.service;

import com.miniurl.dto.FeatureFlagDTO;
import com.miniurl.entity.Feature;
import com.miniurl.entity.FeatureFlag;
import com.miniurl.entity.Role;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.repository.FeatureFlagRepository;
import com.miniurl.repository.FeatureRepository;
import com.miniurl.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing feature flags.
 * Provides methods to enable/disable features and check feature status.
 */
@Service
public class FeatureFlagService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureFlagService.class);

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureRepository featureRepository;
    private final RoleRepository roleRepository;

    public FeatureFlagService(FeatureFlagRepository featureFlagRepository,
                              FeatureRepository featureRepository,
                              RoleRepository roleRepository) {
        this.featureFlagRepository = featureFlagRepository;
        this.featureRepository = featureRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Check if a feature is enabled for a specific role.
     *
     * @param featureKey the feature key to check
     * @param roleId the role ID
     * @return true if the feature is enabled for the role, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String featureKey, Long roleId) {
        return featureFlagRepository.findByFeatureKeyAndRoleId(featureKey, roleId)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    /**
     * Get all feature flags with their status.
     *
     * @return list of all feature flags as DTOs
     */
    @Transactional(readOnly = true)
    public List<FeatureFlagDTO> getAllFeatures() {
        return featureFlagRepository.findAllWithFeatures().stream()
                .map(FeatureFlagDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Get feature flags for a specific role.
     *
     * @param roleId the role ID to filter by
     * @return list of feature flags for the specified role
     */
    @Transactional(readOnly = true)
    public List<FeatureFlagDTO> getFeaturesByRole(Long roleId) {
        return featureFlagRepository.findByRoleIdOrderByFeatureFeatureNameAsc(roleId).stream()
                .map(FeatureFlagDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Get a single feature flag by ID.
     *
     * @param id the feature flag ID
     * @return the feature flag DTO
     * @throws ResourceNotFoundException if the feature flag is not found
     */
    @Transactional(readOnly = true)
    public FeatureFlagDTO getFeatureFlagById(Long id) {
        FeatureFlag featureFlag = featureFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found with id: " + id));
        return new FeatureFlagDTO(featureFlag);
    }

    /**
     * Toggle a feature flag on or off.
     *
     * @param id the feature flag ID to toggle
     * @param enabled the new enabled state
     * @return the updated feature flag DTO
     * @throws ResourceNotFoundException if the feature flag is not found
     */
    @Transactional
    public FeatureFlagDTO toggleFeature(Long id, boolean enabled) {
        FeatureFlag featureFlag = featureFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found with id: " + id));

        boolean previousState = featureFlag.isEnabled();
        featureFlag.setEnabled(enabled);
        featureFlagRepository.save(featureFlag);

        logger.info("Feature '{}' (role: {}) toggled from {} to {}",
                featureFlag.getFeature().getFeatureKey(), 
                featureFlag.getRole().getName(), 
                previousState, enabled);

        return new FeatureFlagDTO(featureFlag);
    }

    /**
     * Create a new feature flag.
     *
     * @param featureId the feature ID
     * @param roleId the role ID
     * @param enabled the enabled state
     * @return the created feature flag DTO
     */
    @Transactional
    public FeatureFlagDTO createFeatureFlag(Long featureId, Long roleId, boolean enabled) {
        Feature feature = featureFlagRepository.findById(featureId)
            .map(FeatureFlag::getFeature)
            .orElseThrow(() -> new ResourceNotFoundException("Feature not found with id: " + featureId));

        Role role = new Role();
        role.setId(roleId);

        FeatureFlag featureFlag = new FeatureFlag(feature, role, enabled);
        featureFlagRepository.save(featureFlag);

        logger.info("Created feature flag for '{}' (role ID: {})", feature.getFeatureKey(), roleId);

        return new FeatureFlagDTO(featureFlag);
    }

    /**
     * Create a new feature with role-based flags for ADMIN and USER roles.
     * Creates the feature definition and feature flags for both roles.
     *
     * @param featureKey the feature key (e.g., "USER_SIGNUP")
     * @param featureName the display name of the feature
     * @param description the feature description
     * @param adminEnabled whether the feature is enabled for ADMIN role
     * @param userEnabled whether the feature is enabled for USER role
     * @return the created feature flag DTO for the USER role
     */
    @Transactional
    public FeatureFlagDTO createFeatureFlag(String featureKey, String featureName, String description,
                                            boolean adminEnabled, boolean userEnabled) {
        // Find or create the feature
        Feature feature = featureRepository.findByFeatureKey(featureKey)
            .orElseGet(() -> featureRepository.save(new Feature(featureKey, featureName, description)));

        // Look up roles
        Role adminRole = roleRepository.findByName("ADMIN")
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: ADMIN"));
        Role userRole = roleRepository.findByName("USER")
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: USER"));

        // Create or update ADMIN flag
        FeatureFlag adminFlag = featureFlagRepository.findByFeatureKeyAndRoleId(featureKey, adminRole.getId())
            .orElse(new FeatureFlag(feature, adminRole, adminEnabled));
        adminFlag.setEnabled(adminEnabled);
        featureFlagRepository.save(adminFlag);

        // Create or update USER flag
        FeatureFlag userFlag = featureFlagRepository.findByFeatureKeyAndRoleId(featureKey, userRole.getId())
            .orElse(new FeatureFlag(feature, userRole, userEnabled));
        userFlag.setEnabled(userEnabled);
        FeatureFlag savedUserFlag = featureFlagRepository.save(userFlag);

        logger.info("Created feature '{}' with ADMIN={}, USER={}", featureKey, adminEnabled, userEnabled);

        return new FeatureFlagDTO(savedUserFlag);
    }

    /**
     * Delete a feature flag by ID.
     *
     * @param id the feature flag ID
     * @throws ResourceNotFoundException if the feature flag is not found
     */
    @Transactional
    public void deleteFeatureFlag(Long id) {
        if (!featureFlagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Feature flag not found with id: " + id);
        }
        featureFlagRepository.deleteById(id);
        logger.info("Deleted feature flag with id: {}", id);
    }

    /**
     * Initialize default feature flags.
     * Note: Features are now initialized via database script (init-db.sql).
     * This method is kept for backward compatibility but does nothing.
     */
    @Transactional
    public void initializeDefaultFeatures() {
        // Feature flags are now initialized via database script (init-db.sql)
        // This method is kept for backward compatibility
        logger.info("Feature flags are managed via database initialization script");
    }
}
