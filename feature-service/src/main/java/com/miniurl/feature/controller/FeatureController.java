package com.miniurl.feature.controller;

import com.miniurl.dto.FeatureFlagDTO;
import com.miniurl.dto.GlobalFlagDTO;
import com.miniurl.feature.service.FeatureFlagService;
import com.miniurl.feature.service.GlobalFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureFlagService featureFlagService;
    private final GlobalFlagService globalFlagService;

    @GetMapping
    public ResponseEntity<List<FeatureFlagDTO>> getAllFeatures() {
        return ResponseEntity.ok(featureFlagService.getAllFeatures());
    }

    @GetMapping("/role/{roleId}")
    public ResponseEntity<List<FeatureFlagDTO>> getFeaturesByRole(@PathVariable Long roleId) {
        return ResponseEntity.ok(featureFlagService.getFeaturesByRole(roleId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeatureFlagDTO> getFeatureFlag(@PathVariable Long id) {
        return ResponseEntity.ok(featureFlagService.getFeatureFlagById(id));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<FeatureFlagDTO> toggleFeature(
            @PathVariable Long id, 
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(featureFlagService.toggleFeature(id, enabled));
    }

    @PostMapping
    public ResponseEntity<FeatureFlagDTO> createFeatureFlag(
            @RequestParam Long featureId, 
            @RequestParam Long roleId, 
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(featureFlagService.createFeatureFlag(featureId, roleId, enabled));
    }

    @PostMapping("/bulk")
    public ResponseEntity<FeatureFlagDTO> createFeatureBulk(
            @RequestParam String featureKey, 
            @RequestParam String featureName, 
            @RequestParam String description,
            @RequestParam boolean adminEnabled, 
            @RequestParam boolean userEnabled) {
        return ResponseEntity.ok(featureFlagService.createFeatureFlag(featureKey, featureName, description, adminEnabled, userEnabled));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeatureFlag(@PathVariable Long id) {
        featureFlagService.deleteFeatureFlag(id);
        return ResponseEntity.noContent().build();
    }
}
