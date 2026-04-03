package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.FeatureFlagDTO;
import com.miniurl.dto.GlobalFlagDTO;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.service.FeatureFlagService;
import com.miniurl.service.GlobalFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for feature flag management (ADMIN only).
 * For managing feature flags for both USER and ADMIN roles.
 */
@RestController
@RequestMapping("/api/admin/features")
@Tag(name = "Feature Flags (Admin)", description = "Admin-only feature flag management endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final GlobalFlagService globalFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService, GlobalFlagService globalFlagService) {
        this.featureFlagService = featureFlagService;
        this.globalFlagService = globalFlagService;
    }

    /**
     * Get all feature flags (ADMIN only).
     */
    @GetMapping
    @Operation(
        summary = "Get all feature flags",
        description = "Retrieve all feature flags for all roles. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Features retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getAllFeatures() {
        var features = featureFlagService.getAllFeatures();
        return ResponseEntity.ok(ApiResponse.success("All features retrieved successfully",
                Map.of("features", features, "count", features.size())));
    }

    /**
     * Get a single feature flag by ID (ADMIN only).
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get feature flag by ID",
        description = "Retrieve a specific feature flag by its ID. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Feature retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getFeatureFlag(
            @Parameter(description = "Feature flag ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        FeatureFlagDTO featureFlag = featureFlagService.getFeatureFlagById(id);
        return ResponseEntity.ok(ApiResponse.success("Feature retrieved", featureFlag));
    }

    /**
     * Toggle a feature flag (ADMIN only).
     */
    @PutMapping("/{id}/toggle")
    @Operation(
        summary = "Toggle feature flag",
        description = "Enable or disable a specific feature flag by ID. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Feature toggled successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> toggleFeatureFlag(
            @Parameter(description = "Feature flag ID", required = true, example = "1")
            @PathVariable("id") Long id,
            @Parameter(description = "Toggle status", required = true,
                schema = @Schema(example = "{\"enabled\": true}"))
            @RequestBody Object request,
            HttpServletRequest httpRequest) {
        Boolean enabled = null;
        
        // Handle both {"enabled": true} and simple true/false
        if (request instanceof Boolean) {
            enabled = (Boolean) request;
        } else if (request instanceof Map) {
            enabled = (Boolean) ((Map<?, ?>) request).get("enabled");
        }
        
        if (enabled == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing 'enabled' field in request body. Send {\"enabled\": true} or just true/false"));
        }

        FeatureFlagDTO updated = featureFlagService.toggleFeature(id, enabled);

        return ResponseEntity.ok(ApiResponse.success(
                "Feature '" + updated.getFeatureName() + "' (role: " + updated.getRoleName() + ") has been " + (enabled ? "enabled" : "disabled") + " successfully.",
                updated));
    }

    /**
     * Create a new feature flag (ADMIN only).
     */
    @PostMapping
    @Operation(
        summary = "Create feature flag",
        description = "Create a new feature with role-based flags for ADMIN and USER roles. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Feature flag created successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature or role not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> createFeatureFlag(
            @Parameter(description = "Create feature flag request", required = true,
                schema = @Schema(example = "{\"featureKey\":\"FEATURE_KEY\",\"featureName\":\"Feature Name\",\"description\":\"Feature Description\",\"adminEnabled\":true,\"userEnabled\":true}"))
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            String featureKey = (String) request.get("featureKey");
            String featureName = (String) request.get("featureName");
            String description = (String) request.get("description");
            Boolean adminEnabled = (Boolean) request.get("adminEnabled");
            Boolean userEnabled = (Boolean) request.get("userEnabled");

            if (featureKey == null || featureName == null || adminEnabled == null || userEnabled == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Missing required fields: featureKey, featureName, description, adminEnabled, userEnabled"));
            }

            FeatureFlagDTO created = featureFlagService.createFeatureFlag(featureKey, featureName, description, adminEnabled, userEnabled);

            return ResponseEntity.ok(ApiResponse.success(
                    "Feature flag created for '" + created.getFeatureName() + "' (role: " + created.getRoleName() + ")",
                    created));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid request: " + e.getMessage()));
        }
    }

    /**
     * Delete a feature flag (ADMIN only).
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete feature flag",
        description = "Delete a specific feature flag by ID. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Feature flag deleted successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> deleteFeatureFlag(
            @Parameter(description = "Feature flag ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        try {
            featureFlagService.deleteFeatureFlag(id);
            return ResponseEntity.ok(ApiResponse.success("Feature flag deleted successfully"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Get all global flags (ADMIN only).
     */
    @GetMapping("/global")
    @Operation(
        summary = "Get all global flags",
        description = "Retrieve all global feature flags. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Global flags retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getAllGlobalFlags() {
        var flags = globalFlagService.getAllGlobalFlags();
        return ResponseEntity.ok(ApiResponse.success("Global flags retrieved successfully",
                Map.of("flags", flags, "count", flags.size())));
    }

    /**
     * Get a single global flag by ID (ADMIN only).
     */
    @GetMapping("/global/{id}")
    @Operation(
        summary = "Get global flag by ID",
        description = "Retrieve a specific global flag by its ID. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Global flag retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Global flag not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getGlobalFlag(
            @Parameter(description = "Global flag ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        GlobalFlagDTO globalFlag = globalFlagService.getGlobalFlagById(id);
        return ResponseEntity.ok(ApiResponse.success("Global flag retrieved", globalFlag));
    }

    /**
     * Toggle a global flag (ADMIN only).
     */
    @PutMapping("/global/{id}/toggle")
    @Operation(
        summary = "Toggle global flag",
        description = "Enable or disable a specific global flag by ID. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Global flag toggled successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Global flag not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> toggleGlobalFlag(
            @Parameter(description = "Global flag ID", required = true, example = "1")
            @PathVariable("id") Long id,
            @Parameter(description = "Toggle status", required = true,
                schema = @Schema(example = "{\"enabled\": true}"))
            @RequestBody Object request,
            HttpServletRequest httpRequest) {
        Boolean enabled = null;
        
        // Handle both {"enabled": true} and simple true/false
        if (request instanceof Boolean) {
            enabled = (Boolean) request;
        } else if (request instanceof Map) {
            enabled = (Boolean) ((Map<?, ?>) request).get("enabled");
        }
        
        if (enabled == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Missing 'enabled' field in request body. Send {\"enabled\": true} or just true/false"));
        }

        GlobalFlagDTO updated = globalFlagService.toggleGlobalFlag(id, enabled);

        return ResponseEntity.ok(ApiResponse.success(
                "Global feature '" + updated.getFeatureName() + "' has been " + (enabled ? "enabled" : "disabled") + " successfully.",
                updated));
    }

    /**
     * Create a new global flag (ADMIN only).
     */
    @PostMapping("/global")
    @Operation(
        summary = "Create global flag",
        description = "Create a new global flag. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Global flag created successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Feature not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> createGlobalFlag(
            @Parameter(description = "Create global flag request", required = true,
                schema = @Schema(example = "{\"featureKey\":\"FEATURE_KEY\",\"featureName\":\"Feature Name\",\"description\":\"Feature Description\",\"enabled\":true}"))
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        try {
            String featureKey = (String) request.get("featureKey");
            String featureName = (String) request.get("featureName");
            String description = (String) request.get("description");
            Boolean enabled = (Boolean) request.get("enabled");

            if (featureKey == null || featureName == null || enabled == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Missing required fields: featureKey, featureName, enabled"));
            }

            GlobalFlagDTO created = globalFlagService.createGlobalFlag(featureKey, featureName, description, enabled);

            return ResponseEntity.ok(ApiResponse.success(
                    "Global flag created for '" + created.getFeatureName() + "'",
                    created));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid request: " + e.getMessage()));
        }
    }

    /**
     * Delete a global flag (ADMIN only).
     */
    @DeleteMapping("/global/{id}")
    @Operation(
        summary = "Delete global flag",
        description = "Delete a specific global flag by ID. ADMIN only endpoint."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Global flag deleted successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Global flag not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> deleteGlobalFlag(
            @Parameter(description = "Global flag ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        try {
            globalFlagService.deleteGlobalFlag(id);
            return ResponseEntity.ok(ApiResponse.success("Global flag deleted successfully"));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
