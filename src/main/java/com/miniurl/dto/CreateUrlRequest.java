package com.miniurl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateUrlRequest {
    
    @NotBlank(message = "URL is required")
    private String url;
    
    @Size(max = 20, message = "Alias must be 20 characters or less")
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
