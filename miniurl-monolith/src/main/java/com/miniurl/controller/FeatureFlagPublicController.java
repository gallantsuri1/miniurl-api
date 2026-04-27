package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.FeatureFlagDTO;
import com.miniurl.dto.GlobalFlagDTO;
import com.miniurl.entity.User;
import com.miniurl.service.FeatureFlagService;
import com.miniurl.service.GlobalFlagService;
import com.miniurl.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public Feature Flag Controller - accessible by all authenticated users.
 * Returns feature flags for the authenticated user's role.
 */
@RestController
@RequestMapping("/api/features")
@Tag(name = "Feature Flags (Public)", description = "Get feature flags for your role (all authenticated users)")
@SecurityRequirement(name = "bearerAuth")
public class FeatureFlagPublicController {

    private final FeatureFlagService featureFlagService;
    private final GlobalFlagService globalFlagService;
    private final UserRepository userRepository;

    public FeatureFlagPublicController(FeatureFlagService featureFlagService, GlobalFlagService globalFlagService, UserRepository userRepository) {
        this.featureFlagService = featureFlagService;
        this.globalFlagService = globalFlagService;
        this.userRepository = userRepository;
    }

    /**
     * Get feature flags for the authenticated user's role.
     * USER users see USER role features.
     * ADMIN users see ADMIN role features.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Get feature flags for your role",
        description = """
            Retrieve feature flags for the authenticated user's role.
            
            **USER Role Features:**
            - PROFILE_PAGE, EXPORT_JSON, URL_SHORTENING
            - DASHBOARD, SETTINGS_PAGE, EMAIL_INVITE
            - USER_MANAGEMENT, FEATURE_MANAGEMENT
            
            **ADMIN Role Features:**
            - Same 8 features as USER role
            
            USER_SIGNUP is a global flag (see /api/features/global).
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Features retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "USER Role", value = """
                        {
                          "success": true,
                          "message": "Features for USER role retrieved successfully",
                          "data": {
                            "features": [...],
                            "count": 8,
                            "role": "USER"
                          }
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "ADMIN Role", value = """
                        {
                          "success": true,
                          "message": "Features for ADMIN role retrieved successfully",
                          "data": {
                            "features": [...],
                            "count": 8,
                            "role": "ADMIN"
                          }
                        }
                        """)
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse> getMyRoleFeatures(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        Long roleId = user.getRole().getId();
        String roleName = user.getRole().getName();
        
        List<FeatureFlagDTO> features = featureFlagService.getFeaturesByRole(roleId);
        
        return ResponseEntity.ok(ApiResponse.success("Features for " + roleName + " role retrieved successfully",
                Map.of("features", features, "count", features.size(), "role", roleName)));
    }

    /**
     * Get all global flags (no authentication required).
     */
    @GetMapping("/global")
    @PreAuthorize("permitAll")
    @Operation(
        summary = "Get all global flags",
        description = """
            Retrieve all global feature flags (not tied to specific roles).
            
            **Global Features:**
            - USER_SIGNUP - Allow new user registration
            
            This endpoint does NOT require authentication.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Global flags retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Global flags retrieved successfully",
                      "data": {
                        "flags": [...],
                        "count": 1
                      }
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK")
    })
    public ResponseEntity<ApiResponse> getAllGlobalFlags() {
        List<GlobalFlagDTO> flags = globalFlagService.getAllGlobalFlags();
        
        return ResponseEntity.ok(ApiResponse.success("Global flags retrieved successfully",
                Map.of("flags", flags, "count", flags.size())));
    }
}
