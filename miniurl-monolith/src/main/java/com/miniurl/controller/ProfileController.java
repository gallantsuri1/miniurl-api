package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.ProfileUpdateRequest;
import com.miniurl.entity.Theme;
import com.miniurl.entity.User;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.repository.UserRepository;
import com.miniurl.service.AuditLogService;
import com.miniurl.service.AuthService;
import com.miniurl.service.FeatureFlagService;
import com.miniurl.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile Management", description = "Endpoints for managing user profile")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;
    private final FeatureFlagService featureFlagService;

    public ProfileController(UserRepository userRepository, AuthService authService,
                            JwtUtil jwtUtil, AuditLogService auditLogService,
                            FeatureFlagService featureFlagService) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.auditLogService = auditLogService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    @Operation(
        summary = "Get user profile",
        description = "Retrieve the current authenticated user's profile information"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid token"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Profile feature disabled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse> getProfile(
            @Parameter(description = "JWT Bearer token", required = true)
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {
        try {
            // Authenticated users can access profile (no feature flag check needed)

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            response.put("email", user.getEmail());
            response.put("username", user.getUsername());
            response.put("role", user.getRole() != null ? user.getRole().getName() : "USER");
            response.put("createdAt", user.getCreatedAt());
            response.put("lastLogin", user.getLastLogin());
            response.put("theme", user.getTheme() != null ? user.getTheme() : Theme.LIGHT);

            return ResponseEntity.ok(ApiResponse.success("Profile retrieved", response));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping
    @Operation(
        summary = "Update user profile",
        description = """
            Update the current authenticated user's profile information.
            All fields are optional — only provided fields will be updated.

            **Theme values:** `LIGHT`, `DARK`, `OCEAN`, `FOREST`

            **Examples:**
            - Update theme only: `{ "theme": "DARK" }`
            - Update name only: `{ "firstName": "John", "lastName": "Doe" }`
            - Update all: `{ "firstName": "John", "lastName": "Doe", "email": "john@example.com", "theme": "OCEAN" }`
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or theme value"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - invalid token"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Profile feature disabled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse> updateProfile(
            @Parameter(description = "Profile update request (all fields optional)", required = true)
            @Valid @RequestBody ProfileUpdateRequest request,
            @Parameter(description = "JWT Bearer token", required = true)
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {
        try {
            // Authenticated users can access profile (no feature flag check needed)

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            com.miniurl.enums.Theme requestTheme = request.getTheme();
            Theme theme = requestTheme != null ? Theme.valueOf(requestTheme.name()) : null;

            User updatedUser = authService.updateProfile(
                user.getId(),
                request.getFirstName(),
                request.getLastName(),
                request.getEmail(),
                theme
            );

            auditLogService.logAction(updatedUser, "PROFILE_UPDATE", "USER", updatedUser.getId(),
                "Updated profile information", httpRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("firstName", updatedUser.getFirstName());
            response.put("lastName", updatedUser.getLastName());
            response.put("email", updatedUser.getEmail());
            response.put("theme", updatedUser.getTheme());

            return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}
