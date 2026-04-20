package com.miniurl.feature.repository;

import com.miniurl.feature.entity.GlobalFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalFlagRepository extends JpaRepository<GlobalFlag, Long> {
    Optional<GlobalFlag> findByFeatureId(Long featureId);
}
