package com.miniurl.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "URL creation request")
public class CreateUrlRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2000, message = "URL must be 2000 characters or less")
    @Pattern(regexp = "^[^\\s]+$", message = "URL must not contain spaces")
    @Schema(description = "Original URL to shorten (max 2000 chars, no spaces)", example = "https://www.example.com/very/long/url/path")
    private String url;

    @Size(min = 6, max = 20, message = "Alias must be between 6 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Alias must contain only alphanumeric characters")
    @Schema(description = "Custom short code (6-20 chars, alphanumeric only)", example = "mylink")
    private String alias;
    
    public CreateUrlRequest() {}
    
    public CreateUrlRequest(String url, String alias) {
        this.url = url;
        this.alias = alias;
    }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
}
