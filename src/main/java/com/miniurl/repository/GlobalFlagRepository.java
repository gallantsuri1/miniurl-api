package com.miniurl.repository;

import com.miniurl.entity.GlobalFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for GlobalFlag entity operations.
 */
@Repository
public interface GlobalFlagRepository extends JpaRepository<GlobalFlag, Long> {

    /**
     * Find a global flag by feature key.
     *
     * @param featureKey the feature key
     * @return Optional containing the global flag if found
     */
    @Query("SELECT gf FROM GlobalFlag gf JOIN gf.feature f WHERE f.featureKey = :featureKey")
    Optional<GlobalFlag> findByFeatureKey(@Param("featureKey") String featureKey);

    /**
     * Check if a global flag exists by feature key.
     *
     * @param featureKey the feature key
     * @return true if the global flag exists
     */
    @Query("SELECT COUNT(gf) > 0 FROM GlobalFlag gf JOIN gf.feature f WHERE f.featureKey = :featureKey")
    boolean existsByFeatureKey(@Param("featureKey") String featureKey);
}
