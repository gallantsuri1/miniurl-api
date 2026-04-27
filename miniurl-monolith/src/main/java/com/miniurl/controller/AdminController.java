package com.miniurl.controller;

import com.miniurl.dto.ApiResponse;
import com.miniurl.dto.PagedResponse;
import com.miniurl.dto.PageableRequest;
import com.miniurl.dto.UserResponse;
import com.miniurl.entity.Role;
import com.miniurl.entity.RoleName;
import com.miniurl.entity.User;
import com.miniurl.entity.UserStatus;
import com.miniurl.repository.RoleRepository;
import com.miniurl.repository.UserRepository;
import com.miniurl.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Management", description = "Admin-only endpoints for user management and statistics")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/users")
    @Operation(
        summary = "Get all users",
        description = "Retrieve all users with optional status filter, search, pagination, and sorting"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getAllUsers(
            @Parameter(description = "Filter by user status (ACTIVE, SUSPENDED, DELETED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search query (searches username, email, firstName, lastName)", example = "john")
            @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Number of users per page", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size,
            @Parameter(description = "Sort by field (id, firstName, lastName, email, username, createdAt, lastLogin, status)", example = "createdAt")
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc or desc)", example = "desc")
            @RequestParam(required = false, defaultValue = "desc") String sortDirection) {

        // Validate sort field
        String validSortBy = validateUserSortField(sortBy);

        // Create sort
        Sort sort = "asc".equalsIgnoreCase(sortDirection) ?
            Sort.by(validSortBy).ascending() :
            Sort.by(validSortBy).descending();

        PageRequest pageRequest = PageRequest.of(page, size, sort);

        Page<User> userPage;
        
        // Handle search query
        if (search != null && !search.isEmpty()) {
            List<User> allUsers = userRepository.findByFirstNameContainingOrLastNameContainingOrEmailContaining(
                search, search, search);
            // Filter by status if provided
            if (status != null && !status.isEmpty()) {
                try {
                    UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
                    allUsers = allUsers.stream()
                        .filter(u -> u.getStatus() == userStatus)
                        .collect(Collectors.toList());
                } catch (IllegalArgumentException e) {
                    // Invalid status, ignore filter
                }
            }
            // Apply manual pagination for search results
            int totalElements = allUsers.size();
            int start = Math.min(page * size, totalElements);
            int end = Math.min(start + size, totalElements);
            List<User> paginatedUsers = start < end ? allUsers.subList(start, end) : List.of();
            userPage = new org.springframework.data.domain.PageImpl<>(paginatedUsers, pageRequest, totalElements);
        } else if (status == null || status.isEmpty()) {
            userPage = userRepository.findAll(pageRequest);
        } else {
            try {
                UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
                userPage = userRepository.findByStatus(userStatus, pageRequest);
            } catch (IllegalArgumentException e) {
                userPage = userRepository.findAll(pageRequest);
            }
        }

        List<UserResponse> userResponses = userPage.getContent().stream()
            .map(user -> UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .username(user.getUsername())
                .roleName(user.getRole() != null ? user.getRole().getName() : "USER")
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .status(user.getStatus().name())
                .build())
            .collect(Collectors.toList());

        // Calculate status counts
        long activeCount = userRepository.countByStatus(UserStatus.ACTIVE);
        long suspendedCount = userRepository.countByStatus(UserStatus.SUSPENDED);
        long deletedCount = userRepository.countByStatus(UserStatus.DELETED);

        PagedResponse<UserResponse> pagedResponse = PagedResponse.<UserResponse>builder()
            .content(userResponses)
            .page(page)
            .size(size)
            .totalElements(userPage.getTotalElements())
            .sortBy(validSortBy)
            .sortDirection(sortDirection)
            .build();

        Map<String, Object> response = new HashMap<>();
        response.put("pagination", pagedResponse);
        response.put("summary", Map.of(
            "totalUsers", userPage.getTotalElements(),
            "activeUsers", activeCount,
            "suspendedUsers", suspendedCount,
            "deletedUsers", deletedCount
        ));

        return ResponseEntity.ok(ApiResponse.success("Users retrieved", response));
    }

    @GetMapping("/users/{id}")
    @Operation(
        summary = "Get user by ID",
        description = "Retrieve a specific user by their ID"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getUserById(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

        UserResponse response = UserResponse.builder()
            .id(user.getId())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(user.getEmail())
            .username(user.getUsername())
            .roleName(user.getRole() != null ? user.getRole().getName() : "USER")
            .createdAt(user.getCreatedAt())
            .lastLogin(user.getLastLogin())
            .status(user.getStatus().name())
            .build();

        return ResponseEntity.ok(ApiResponse.success("User retrieved", response));
    }

    @GetMapping("/users/search")
    @Operation(
        summary = "Search users",
        description = "Search users by first name, last name, or email"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> searchUsers(
            @Parameter(description = "Search query", required = true, example = "john")
            @RequestParam String query) {
        List<User> users = userRepository.findByFirstNameContainingOrLastNameContainingOrEmailContaining(
            query, query, query);

        List<UserResponse> userResponses = users.stream()
            .map(user -> UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .username(user.getUsername())
                .roleName(user.getRole() != null ? user.getRole().getName() : "USER")
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .status(user.getStatus().name())
                .build())
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Users found", userResponses));
    }

    @PostMapping("/users/{id}/deactivate")
    @Operation(
        summary = "Deactivate user",
        description = "Soft delete a user account (set status to DELETED)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deactivated successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> deactivateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);

        auditLogService.logAction(user, "USER_DEACTIVATED", "USER", id,
            "User account deactivated by admin", httpRequest);

        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully"));
    }

    @PostMapping("/users/{id}/activate")
    @Operation(
        summary = "Activate user",
        description = "Activate a previously deactivated user account"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User activated successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> activateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        auditLogService.logAction(user, "USER_ACTIVATED", "USER", id,
            "User account activated by admin", httpRequest);

        return ResponseEntity.ok(ApiResponse.success("User activated successfully"));
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Get user statistics",
        description = "Get counts of users by status (total, active, suspended, deleted)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Statistics retrieved successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> getStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long suspendedUsers = userRepository.countByStatus(UserStatus.SUSPENDED);
        long deletedUsers = userRepository.countByStatus(UserStatus.DELETED);

        Map<String, Object> response = new HashMap<>();
        response.put("totalUsers", totalUsers);
        response.put("activeUsers", activeUsers);
        response.put("suspendedUsers", suspendedUsers);
        response.put("deletedUsers", deletedUsers);

        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved", response));
    }

    @PostMapping("/users/{id}/suspend")
    @Operation(
        summary = "Suspend user",
        description = "Temporarily suspend a user account (cannot be used for admin users)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User suspended successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Cannot suspend admin users"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> suspendUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isAdmin()) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Cannot suspend admin users"));
        }

        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        auditLogService.logAction(user, "USER_SUSPENDED", "USER", id,
            "User account suspended by admin", httpRequest);

        return ResponseEntity.ok(ApiResponse.success("User suspended successfully"));
    }

    @PostMapping("/users/{id}/role")
    @Operation(
        summary = "Update user role",
        description = "Change a user's role (ADMIN or USER)"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User role updated successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid role name"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User or role not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
    })
    public ResponseEntity<ApiResponse> updateUserRole(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "New role name (ADMIN or USER)", required = true, example = "ADMIN")
            @RequestParam String roleName,
            HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            RoleName newRoleName = RoleName.valueOf(roleName.toUpperCase());
            Role newRole = roleRepository.findByName(newRoleName.name())
                .orElseThrow(() -> new RuntimeException("Role not found"));

            String oldRoleName = user.getRole().getName();
            user.setRole(newRole);
            userRepository.save(user);

            auditLogService.logAction(user, "USER_ROLE_CHANGED", "USER", id,
                "User role changed from " + oldRoleName + " to " + newRoleName, httpRequest);

            return ResponseEntity.ok(ApiResponse.success("User role updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid role name: " + roleName));
        }
    }

    private String validateUserSortField(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return "createdAt";
        }
        
        Set<String> allowedFields = Set.of("id", "firstName", "lastName", "email", "username", "createdAt", "lastLogin", "status");
        String field = sortBy.trim();
        
        if (!allowedFields.contains(field)) {
            return "createdAt";
        }
        
        return field;
    }
}
