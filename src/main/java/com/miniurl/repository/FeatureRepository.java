package com.miniurl.repository;

import com.miniurl.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for Feature entity operations.
 */
@Repository
public interface FeatureRepository extends JpaRepository<Feature, Long> {

    /**
     * Find a feature by its unique key.
     *
     * @param featureKey the unique feature key
     * @return Optional containing the feature if found
     */
    Optional<Feature> findByFeatureKey(String featureKey);

    /**
     * Check if a feature exists by its key.
     *
     * @param featureKey the unique feature key
     * @return true if the feature exists
     */
    boolean existsByFeatureKey(String featureKey);
}
