package com.miniurl.service;

import com.miniurl.dto.GlobalFlagDTO;
import com.miniurl.entity.Feature;
import com.miniurl.entity.GlobalFlag;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.repository.GlobalFlagRepository;
import com.miniurl.repository.FeatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing global feature flags.
 * Provides methods to get/toggle global features (not tied to roles).
 */
@Service
public class GlobalFlagService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalFlagService.class);

    private final GlobalFlagRepository globalFlagRepository;
    private final FeatureRepository featureRepository;

    public GlobalFlagService(GlobalFlagRepository globalFlagRepository, FeatureRepository featureRepository) {
        this.globalFlagRepository = globalFlagRepository;
        this.featureRepository = featureRepository;
    }

    /**
     * Get all global flags.
     *
     * @return list of all global flags as DTOs
     */
    @Transactional(readOnly = true)
    public List<GlobalFlagDTO> getAllGlobalFlags() {
        return globalFlagRepository.findAll().stream()
                .map(GlobalFlagDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * Get a single global flag by ID.
     *
     * @param id the global flag ID
     * @return the global flag DTO
     * @throws ResourceNotFoundException if the global flag is not found
     */
    @Transactional(readOnly = true)
    public GlobalFlagDTO getGlobalFlagById(Long id) {
        GlobalFlag globalFlag = globalFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Global flag not found with id: " + id));
        return new GlobalFlagDTO(globalFlag);
    }

    /**
     * Check if a global feature is enabled by feature key.
     *
     * @param featureKey the feature key (e.g., "USER_SIGNUP")
     * @return true if the feature is enabled, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isGlobalFeatureEnabled(String featureKey) {
        return globalFlagRepository.findByFeatureKey(featureKey)
                .map(GlobalFlag::isEnabled)
                .orElse(false);
    }

    /**
     * Toggle a global flag on or off.
     *
     * @param id the global flag ID to toggle
     * @param enabled the new enabled state
     * @return the updated global flag DTO
     * @throws ResourceNotFoundException if the global flag is not found
     */
    @Transactional
    public GlobalFlagDTO toggleGlobalFlag(Long id, boolean enabled) {
        GlobalFlag globalFlag = globalFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Global flag not found with id: " + id));

        boolean previousState = globalFlag.isEnabled();
        globalFlag.setEnabled(enabled);
        globalFlagRepository.save(globalFlag);

        logger.info("Global feature '{}' toggled from {} to {}",
                globalFlag.getFeature().getFeatureKey(), previousState, enabled);

        return new GlobalFlagDTO(globalFlag);
    }

    /**
     * Create a new global flag.
     *
     * @param featureId the feature ID
     * @param enabled the enabled state
     * @return the created global flag DTO
     */
    @Transactional
    public GlobalFlagDTO createGlobalFlag(Long featureId, boolean enabled) {
        Feature feature = featureRepository.findById(featureId)
            .orElseThrow(() -> new ResourceNotFoundException("Feature not found with id: " + featureId));
        
        GlobalFlag globalFlag = new GlobalFlag(feature, enabled);
        globalFlagRepository.save(globalFlag);
        
        logger.info("Created global flag for '{}'", feature.getFeatureKey());
        
        return new GlobalFlagDTO(globalFlag);
    }

    /**
     * Delete a global flag by ID.
     *
     * @param id the global flag ID
     * @throws ResourceNotFoundException if the global flag is not found
     */
    @Transactional
    public void deleteGlobalFlag(Long id) {
        if (!globalFlagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Global flag not found with id: " + id);
        }
        globalFlagRepository.deleteById(id);
        logger.info("Deleted global flag with id: {}", id);
    }
}
