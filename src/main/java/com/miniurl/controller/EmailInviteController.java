package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.PagedResponse;
import com.miniurl.entity.EmailInvite;
import com.miniurl.entity.User;
import com.miniurl.repository.UserRepository;
import com.miniurl.service.AuditLogService;
import com.miniurl.service.EmailInviteService;
import com.miniurl.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for email invitation management.
 */
@RestController
@RequestMapping("/api/admin/email-invites")
@Tag(name = "Email Invitations", description = "Admin-only endpoints for managing email invitations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class EmailInviteController {

    private static final Logger logger = LoggerFactory.getLogger(EmailInviteController.class);

    private final EmailInviteService emailInviteService;
    private final FeatureFlagService featureFlagService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public EmailInviteController(EmailInviteService emailInviteService,
                                 FeatureFlagService featureFlagService,
                                 AuditLogService auditLogService,
                                 UserRepository userRepository) {
        this.emailInviteService = emailInviteService;
        this.featureFlagService = featureFlagService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(
        summary = "Get all email invites",
        description = "Retrieve all email invitations with pagination, sorting, search, and statistics"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invites retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getAllInvitesApi(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Number of invites per page", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size,
            @Parameter(description = "Sort by field (id, email, status, createdAt, expiresAt, invitedByUsername)", example = "createdAt")
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)", example = "desc")
            @RequestParam(required = false, defaultValue = "desc") String sortDirection,
            @Parameter(description = "Search by email (partial match)", example = "@gmail.com")
            @RequestParam(required = false) String search) {

        PagedResponse<EmailInvite> pagedInvites = emailInviteService.getAllInvites(page, size, sortBy, sortDirection, search);

        // Calculate status counts from all invites (not filtered)
        long totalCount = emailInviteService.getAllInvites().size();
        long pendingCount = emailInviteService.getPendingInvites().size();
        long acceptedCount = emailInviteService.getAcceptedInvites().size();
        long revokedCount = totalCount - pendingCount - acceptedCount;

        Map<String, Object> response = new HashMap<>();
        response.put("pagination", pagedInvites);
        response.put("summary", Map.of(
            "totalInvites", totalCount,
            "pendingInvites", pendingCount,
            "acceptedInvites", acceptedCount,
            "revokedInvites", revokedCount
        ));

        return ResponseEntity.ok(ApiResponse.success("Invites retrieved", response));
    }

    @PostMapping("/send")
    @Operation(
        summary = "Send email invitation",
        description = "Send an email invitation to a new user (ADMIN only)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation sent successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email or feature disabled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> sendInvite(
            @Parameter(description = "Email address to send invitation to", required = true, example = "user@example.com")
            @RequestParam("email") String email,
            Authentication authentication) {
        try {
            // ADMIN users can always access email invites (no feature flag check needed)

            String invitedByUsername = "Unknown";
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                invitedByUsername = ((User) authentication.getPrincipal()).getUsername();
            }

            emailInviteService.createInvite(email, invitedByUsername);
            logger.info("Invite sent to {} by {}", email, invitedByUsername);
            return ResponseEntity.ok(ApiResponse.success("Invitation sent to: " + email));

        } catch (Exception e) {
            logger.error("Failed to send invite to {}: {}", email, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send invite: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/resend")
    @Operation(
        summary = "Resend email invitation",
        description = "Resend an existing email invitation (for revoked/expired invites)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation resent successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> resendInvite(
            @Parameter(description = "Invitation ID", required = true)
            @PathVariable("id") Long id,
            Authentication authentication) {
        try {
            EmailInvite existingInvite = emailInviteService.getById(id);
            if (existingInvite == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invitation not found"));
            }

            String username = null;
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                username = ((User) authentication.getPrincipal()).getUsername();
            }

            emailInviteService.createInvite(existingInvite.getEmail(), username);
            logger.info("Invite resent to {} by {}", existingInvite.getEmail(), username);
            return ResponseEntity.ok(ApiResponse.success("Invitation resent to: " + existingInvite.getEmail()));

        } catch (Exception e) {
            logger.error("Failed to resend invite: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to resend invite: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/revoke")
    @Operation(
        summary = "Revoke email invitation",
        description = "Revoke an existing email invitation"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation revoked successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> revokeInvite(
            @Parameter(description = "Invitation ID", required = true)
            @PathVariable("id") Long id) {
        try {
            emailInviteService.revokeInvite(id);
            return ResponseEntity.ok(ApiResponse.success("Invitation revoked successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to revoke invite: " + e.getMessage()));
        }
    }
}
