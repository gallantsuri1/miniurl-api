package com.miniurl.feature.service;

import com.miniurl.common.dto.GlobalFlagDTO;
import com.miniurl.common.exception.ResourceNotFoundException;
import com.miniurl.feature.entity.Feature;
import com.miniurl.feature.entity.GlobalFlag;
import com.miniurl.feature.repository.FeatureRepository;
import com.miniurl.feature.repository.GlobalFlagRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GlobalFlagService {

    private final GlobalFlagRepository globalFlagRepository;
    private final FeatureRepository featureRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String GLOBAL_FLAG_CACHE_PREFIX = "global_flag:";

    public GlobalFlagService(GlobalFlagRepository globalFlagRepository, 
                             FeatureRepository featureRepository,
                             RedisTemplate<String, Object> redisTemplate) {
        this.globalFlagRepository = globalFlagRepository;
        this.featureRepository = featureRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<GlobalFlagDTO> getAllGlobalFlags() {
        return globalFlagRepository.findAll().stream()
                .map(GlobalFlagDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GlobalFlagDTO getGlobalFlagById(Long id) {
        GlobalFlag globalFlag = globalFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Global flag not found with id: " + id));
        return new GlobalFlagDTO(globalFlag);
    }

    @Transactional(readOnly = true)
    public boolean isGlobalFeatureEnabled(String featureKey) {
        String cacheKey = GLOBAL_FLAG_CACHE_PREFIX + featureKey;
        
        // Try cache first
        Boolean cachedValue = (Boolean) redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            return cachedValue;
        }

        // Fallback to DB
        boolean enabled = globalFlagRepository.findByFeatureKey(featureKey)
                .map(GlobalFlag::isEnabled)
                .orElse(false);

        // Cache the result for 10 minutes
        redisTemplate.opsForValue().set(cacheKey, enabled, 10, TimeUnit.MINUTES);
        
        return enabled;
    }

    @Transactional
    public GlobalFlagDTO toggleGlobalFlag(Long id, boolean enabled) {
        GlobalFlag globalFlag = globalFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Global flag not found with id: " + id));

        boolean previousState = globalFlag.isEnabled();
        globalFlag.setEnabled(enabled);
        globalFlagRepository.save(globalFlag);

        // Invalidate cache
        redisTemplate.delete(GLOBAL_FLAG_CACHE_PREFIX + globalFlag.getFeature().getFeatureKey());

        log.info("Global feature '{}' toggled from {} to {}",
                globalFlag.getFeature().getFeatureKey(), previousState, enabled);

        return new GlobalFlagDTO(globalFlag);
    }

    @Transactional
    public GlobalFlagDTO createGlobalFlag(Long featureId, boolean enabled) {
        Feature feature = featureRepository.findById(featureId)
            .orElseThrow(() -> new ResourceNotFoundException("Feature not found with id: " + featureId));

        GlobalFlag globalFlag = new GlobalFlag(feature, enabled);
        globalFlagRepository.save(globalFlag);

        log.info("Created global flag for '{}'", feature.getFeatureKey());

        return new GlobalFlagDTO(globalFlag);
    }

    @Transactional
    public GlobalFlagDTO createGlobalFlag(String featureKey, String featureName, String description, boolean enabled) {
        Feature feature = featureRepository.findByFeatureKey(featureKey)
            .orElseGet(() -> featureRepository.save(new Feature(featureKey, featureName, description)));

        GlobalFlag globalFlag = globalFlagRepository.findByFeatureKey(featureKey)
            .orElse(new GlobalFlag(feature, enabled));
        globalFlag.setEnabled(enabled);
        globalFlagRepository.save(globalFlag);

        // Invalidate cache
        redisTemplate.delete(GLOBAL_FLAG_CACHE_PREFIX + featureKey);

        log.info("Created/updated global flag for '{}'", feature.getFeatureKey());

        return new GlobalFlagDTO(globalFlag);
    }

    @Transactional
    public void deleteGlobalFlag(Long id) {
        GlobalFlag globalFlag = globalFlagRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Global flag not found with id: " + id));
        
        String featureKey = globalFlag.getFeature().getFeatureKey();
        globalFlagRepository.deleteById(id);
        
        // Invalidate cache
        redisTemplate.delete(GLOBAL_FLAG_CACHE_PREFIX + featureKey);
        log.info("Deleted global flag with id: {}", id);
    }

    @Transactional(readOnly = true)
    public String getGlobalAppName() {
        // This is a specific high-frequency check, we could cache it specifically
        // but isGlobalFeatureEnabled already handles caching for the boolean part.
        // For the name, we'll just hit the DB or implement a similar cache.
        return globalFlagRepository.findByFeatureKey("GLOBAL_APP_NAME")
                .filter(GlobalFlag::isEnabled)
                .map(gf -> gf.getFeature().getFeatureName())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isTwoFactorAuthEnabled() {
        return isGlobalFeatureEnabled("TWO_FACTOR_AUTH");
    }
}
