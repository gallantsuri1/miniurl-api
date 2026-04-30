package com.miniurl.identity.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.DeleteAccountRequest;
import com.miniurl.identity.entity.User;
import com.miniurl.identity.exception.ResourceNotFoundException;
import com.miniurl.identity.exception.UnauthorizedException;
import com.miniurl.identity.repository.UserRepository;
import com.miniurl.identity.service.AuthService;
import com.miniurl.identity.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.validation.Valid;
import java.util.*;

/**
 * Settings management endpoints (export data, delete account).
 * Ported from monolith's SettingsController.
 * URL data is fetched from url-service via RestTemplate (service discovery).
 * Audit logging via SLF4J (no dedicated AuditLogService in microservices).
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;

    public SettingsController(UserRepository userRepository, AuthService authService,
                              JwtService jwtService, RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.jwtService = jwtService;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportData(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Fetch URLs from url-service via internal endpoint
        List<Map<String, Object>> urls = fetchUserUrls(user.getId());

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
        exportData.put("urls", urls);

        log.info("DATA_EXPORT: User {} (id={}) exported data", username, user.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "miniurl-export-" + username + ".json");

        return new ResponseEntity<>(exportData, headers, HttpStatus.OK);
    }

    @PostMapping("/delete-account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid authorization header");
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        authService.deleteAccount(user.getId(), request.getPassword());

        log.warn("ACCOUNT_DELETION: User {} (id={}) deleted account via settings", username, user.getId());

        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
    }

    /**
     * Fetches user URLs from the url-service via its internal endpoint.
     * Uses service discovery (url-service) with RestTemplate.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchUserUrls(Long userId) {
        try {
            String url = "http://url-service/internal/urls/by-user/" + userId;
            ResponseEntity<ApiResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse>() {}
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                return (List<Map<String, Object>>) response.getBody().getData();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch URLs from url-service for user {}: {}", userId, e.getMessage());
        }
        return List.of();
    }
}
