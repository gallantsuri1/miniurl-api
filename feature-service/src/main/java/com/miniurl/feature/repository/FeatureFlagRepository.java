package com.miniurl.feature.repository;

import com.miniurl.feature.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, Long> {
    Optional<FeatureFlag> findByFeatureIdAndRoleId(Long featureId, Long roleId);
}
