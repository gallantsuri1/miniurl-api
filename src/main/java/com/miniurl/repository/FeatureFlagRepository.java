package com.miniurl.repository;

import com.miniurl.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for FeatureFlag entity operations.
 */
@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {

    /**
     * Find all feature flags for a specific role ordered by feature name.
     *
     * @param roleId the role ID
     * @return list of feature flags for the specified role
     */
    List<FeatureFlag> findByRoleIdOrderByFeatureFeatureNameAsc(Long roleId);

    /**
     * Find a feature flag by feature key and role ID.
     *
     * @param featureKey the feature key
     * @param roleId the role ID
     * @return Optional containing the feature flag if found
     */
    @Query("SELECT ff FROM FeatureFlag ff JOIN ff.feature f WHERE f.featureKey = :featureKey AND ff.role.id = :roleId")
    Optional<FeatureFlag> findByFeatureKeyAndRoleId(@Param("featureKey") String featureKey, @Param("roleId") Long roleId);

    /**
     * Check if a feature flag exists by feature key and role ID.
     *
     * @param featureKey the feature key
     * @param roleId the role ID
     * @return true if the feature flag exists
     */
    @Query("SELECT COUNT(ff) > 0 FROM FeatureFlag ff JOIN ff.feature f WHERE f.featureKey = :featureKey AND ff.role.id = :roleId")
    boolean existsByFeatureKeyAndRoleId(@Param("featureKey") String featureKey, @Param("roleId") Long roleId);

    /**
     * Find all feature flags ordered by feature name.
     *
     * @return list of all feature flags
     */
    @Query("SELECT ff FROM FeatureFlag ff JOIN FETCH ff.feature ORDER BY ff.feature.featureName ASC")
    List<FeatureFlag> findAllWithFeatures();
}
