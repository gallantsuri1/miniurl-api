package com.miniurl.feature.controller;

import com.miniurl.dto.GlobalFlagDTO;
import com.miniurl.feature.service.GlobalFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/global-flags")
@RequiredArgsConstructor
public class GlobalFlagController {

    private final GlobalFlagService globalFlagService;

    @GetMapping
    public ResponseEntity<List<GlobalFlagDTO>> getAllGlobalFlags() {
        return ResponseEntity.ok(globalFlagService.getAllGlobalFlags());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GlobalFlagDTO> getGlobalFlag(@PathVariable Long id) {
        return ResponseEntity.ok(globalFlagService.getGlobalFlagById(id));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<GlobalFlagDTO> toggleGlobalFlag(
            @PathVariable Long id, 
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(globalFlagService.toggleGlobalFlag(id, enabled));
    }

    @PostMapping
    public ResponseEntity<GlobalFlagDTO> createGlobalFlag(
            @RequestParam Long featureId, 
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(globalFlagService.createGlobalFlag(featureId, enabled));
    }

    @PostMapping("/bulk")
    public ResponseEntity<GlobalFlagDTO> createGlobalFlagBulk(
            @RequestParam String featureKey, 
            @RequestParam String featureName, 
            @RequestParam String description, 
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(globalFlagService.createGlobalFlag(featureKey, featureName, description, enabled));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGlobalFlag(@PathVariable Long id) {
        globalFlagService.deleteGlobalFlag(id);
        return ResponseEntity.noContent().build();
    }
}
