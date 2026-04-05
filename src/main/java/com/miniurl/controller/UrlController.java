package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.CreateUrlRequest;
import com.miniurl.dto.PageableRequest;
import com.miniurl.dto.PagedResponse;
import com.miniurl.dto.UrlResponse;
import com.miniurl.entity.User;
import com.miniurl.repository.UserRepository;
import com.miniurl.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urls")
@Tag(name = "URL Management", description = "Endpoints for managing shortened URLs")
@SecurityRequirement(name = "bearerAuth")
public class UrlController {

    private final UrlService urlService;
    private final UserRepository userRepository;

    public UrlController(UrlService urlService, UserRepository userRepository) {
        this.urlService = urlService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @Operation(
        summary = "Create shortened URL",
        description = """
            Create a new shortened URL with optional custom alias.

            **Validation Rules:**
            - **url**: Required, max 2000 characters, no spaces allowed
            - **alias**: Optional, 6-20 characters, alphanumeric only (letters + numbers)
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "URL created successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid URL or alias",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "URL with spaces", value = """
                        {
                          "success": false,
                          "message": "URL must not contain spaces"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Short alias", value = """
                        {
                          "success": false,
                          "message": "Alias must be at least 6 characters"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Special chars in alias", value = """
                        {
                          "success": false,
                          "message": "Alias must contain only alphanumeric characters"
                        }
                        """)
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse> createUrl(
            @Parameter(description = "URL creation request", required = true)
            @Valid @RequestBody CreateUrlRequest request,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        UrlResponse response = urlService.createUrl(request, userId);
        return ResponseEntity.ok(ApiResponse.success("URL shortened successfully", response));
    }

    @GetMapping
    @Operation(
        summary = "Get user's URLs",
        description = "Retrieve all shortened URLs for the authenticated user with pagination support"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "URLs retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse> getUserUrls(
            @Parameter(description = "Pagination parameters")
            @ModelAttribute PageableRequest pageableRequest,
            Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        PagedResponse<UrlResponse> urls = urlService.getUserUrls(userId, pageableRequest);
        return ResponseEntity.ok(ApiResponse.success("URLs retrieved successfully", urls));
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete URL",
        description = "Delete a shortened URL by ID (only owner can delete)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "URL deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "URL not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorized to delete this URL")
    })
    public ResponseEntity<ApiResponse> deleteUrl(
            @Parameter(description = "URL ID", required = true)
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = getCurrentUserId(authentication);
        urlService.deleteUrl(id, userId);
        return ResponseEntity.ok(ApiResponse.success("URL deleted successfully"));
    }

    @GetMapping("/usage-stats")
    @Operation(
        summary = "Get URL creation usage stats",
        description = "Get current URL creation usage statistics (per minute, per day, per month)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Usage stats retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse> getUsageStats(Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        var stats = urlService.getUsageStats(userId);
        return ResponseEntity.ok(ApiResponse.success("Usage stats retrieved successfully", stats));
    }

    private Long getCurrentUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return user.getId();
    }
}
