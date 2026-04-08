package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.repository.UserRepository;
import com.miniurl.service.EmailInviteService;
import com.miniurl.service.GlobalFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for self-invitation.
 * Allows users to invite themselves when USER_SIGNUP feature is enabled.
 * This endpoint is PUBLIC (no authentication required) but checks the USER_SIGNUP global feature.
 */
@RestController
@RequestMapping("/api/self-invite")
@Tag(name = "Self Invitation", description = "Endpoints for users to invite themselves")
public class SelfInviteController {

    private static final Logger logger = LoggerFactory.getLogger(SelfInviteController.class);

    private final EmailInviteService emailInviteService;
    private final GlobalFlagService globalFlagService;
    private final UserRepository userRepository;

    public SelfInviteController(EmailInviteService emailInviteService,
                                GlobalFlagService globalFlagService,
                                UserRepository userRepository) {
        this.emailInviteService = emailInviteService;
        this.globalFlagService = globalFlagService;
        this.userRepository = userRepository;
    }

    @PostMapping("/send")
    @Operation(
        summary = "Send self-invitation",
        description = """
            Send an invitation to yourself for user signup.
            
            **Requirements:**
            - USER_SIGNUP global feature must be enabled
            - Email must not already be registered
            
            **Process:**
            1. Check if USER_SIGNUP feature is enabled
            2. Verify email is not already registered
            3. Create invitation and send email
            4. User receives email with signup link
            
            **Note:** This endpoint is PUBLIC (no authentication required) but checks the USER_SIGNUP global feature.
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation sent successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Success", value = """
                    {
                      "success": true,
                      "message": "Invitation sent to: user@example.com"
                    }
                    """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid email or feature disabled",
            content = @Content(schema = @Schema(implementation = ApiResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Feature Disabled", value = """
                        {
                          "success": false,
                          "message": "Self-signup is currently disabled"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Email Exists", value = """
                        {
                          "success": false,
                          "message": "Email already registered: user@example.com"
                        }
                        """),
                    @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Invalid Email", value = """
                        {
                          "success": false,
                          "message": "Failed to send invite: Invalid email format"
                        }
                        """)
                }))
    })
    public ResponseEntity<ApiResponse> sendSelfInvite(
            @Parameter(description = "Email address to send invitation to", required = true, example = "user@example.com")
            @RequestParam("email") String email) {
        try {
            // Check if GLOBAL_USER_SIGNUP feature is enabled
            if (!globalFlagService.isGlobalFeatureEnabled("GLOBAL_USER_SIGNUP")) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Self-signup is currently disabled"));
            }

            // Check if email is already registered
            if (userRepository.existsByEmail(email)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Email already registered: " + email));
            }

            // Create invitation (no username tracking for self-invite)
            emailInviteService.createInvite(email, "self-invite");
            
            logger.info("Self-invite sent to {}", email);
            return ResponseEntity.ok(ApiResponse.success("Invitation sent to: " + email));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid email for self-invite: {}", email);
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid email format"));
        } catch (Exception e) {
            logger.error("Failed to send self-invite to {}: {}", email, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send invite: " + e.getMessage()));
        }
    }
}
