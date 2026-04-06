package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.DeleteAccountRequest;
import com.miniurl.entity.Url;
import com.miniurl.entity.User;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.repository.UrlRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings Management", description = "Endpoints for user settings and account management")
@SecurityRequirement(name = "bearerAuth")
public class SettingsController {

    private final UserRepository userRepository;
    private final UrlRepository urlRepository;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;
    private final FeatureFlagService featureFlagService;

    public SettingsController(UserRepository userRepository, UrlRepository urlRepository,
                             AuthService authService, JwtUtil jwtUtil, AuditLogService auditLogService,
                             FeatureFlagService featureFlagService) {
        this.userRepository = userRepository;
        this.urlRepository = urlRepository;
        this.authService = authService;
        this.jwtUtil = jwtUtil;
        this.auditLogService = auditLogService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping("/export")
    @Operation(
        summary = "Export user data",
        description = "Export all user data (profile and URLs) as JSON"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Data exported successfully",
            content = @Content(schema = @Schema(implementation = Map.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<?> exportData(
            @Parameter(description = "JWT Bearer token", required = true)
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            List<Url> urls = urlRepository.findByUserId(user.getId());

            Map<String, Object> exportData = new HashMap<>();
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("firstName", user.getFirstName());
            userData.put("lastName", user.getLastName());
            userData.put("email", user.getEmail());
            userData.put("username", user.getUsername());
            userData.put("role", user.getRole() != null ? user.getRole().getName() : "USER");
            userData.put("createdAt", user.getCreatedAt());
            userData.put("lastLogin", user.getLastLogin());
            exportData.put("user", userData);
            exportData.put("urls", urls.stream().map(url -> {
                Map<String, Object> urlData = new HashMap<>();
                urlData.put("id", url.getId());
                urlData.put("originalUrl", url.getOriginalUrl());
                urlData.put("shortCode", url.getShortCode());
                urlData.put("accessCount", url.getAccessCount());
                urlData.put("createdAt", url.getCreatedAt());
                return urlData;
            }).collect(Collectors.toList()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", "miniurl-export-" + username + ".json");

            return new ResponseEntity<>(exportData, headers, HttpStatus.OK);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete-account")
    @Operation(
        summary = "Delete account",
        description = "Permanently delete the current user's account"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid password"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Settings feature disabled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<ApiResponse> deleteAccount(
            @Parameter(description = "Delete account request", required = true)
            @Valid @RequestBody DeleteAccountRequest request,
            @Parameter(description = "JWT Bearer token", required = true)
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {
        try {
            // Authenticated users can access settings (no feature flag check needed)

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);

            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            authService.deleteAccount(user.getId(), request.getPassword());

            auditLogService.logAction(user, "ACCOUNT_DELETION", "USER", user.getId(),
                "Account deleted", httpRequest);

            return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
        } catch (UnauthorizedException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        }
    }
}
