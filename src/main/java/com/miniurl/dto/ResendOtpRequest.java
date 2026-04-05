package com.miniurl.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to resend OTP for 2FA login.
 * Accepts either username or email — whichever the user logged in with.
 */
@Schema(description = "OTP resend request for 2FA login")
public class ResendOtpRequest {

    @NotBlank(message = "Username or email is required")
    @Schema(description = "Username or email — use the same identifier used during login", example = "johndoe")
    private String username;

    public ResendOtpRequest() {}

    public ResendOtpRequest(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
