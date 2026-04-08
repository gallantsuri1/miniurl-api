package com.miniurl.controller;

import com.miniurl.entity.Url;
import com.miniurl.service.UrlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

@RestController
public class RedirectController {

    private static final Logger logger = LoggerFactory.getLogger(RedirectController.class);
    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    /**
     * Validate URL before redirect to prevent open redirect attacks
     */
    private boolean isValidRedirectUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol();
            
            // Only allow http and https
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                return false;
            }
            
            // Block dangerous protocols that could have bypassed creation validation
            String lowerUrl = url.toLowerCase().trim();
            if (lowerUrl.startsWith("javascript:") ||
                lowerUrl.startsWith("data:") ||
                lowerUrl.startsWith("vbscript:") ||
                lowerUrl.startsWith("file:")) {
                return false;
            }
            
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        Optional<Url> url = urlService.findByShortCode(shortCode);

        if (url.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String originalUrl = url.get().getOriginalUrl();
        
        // Validate redirect URL before redirecting
        if (!isValidRedirectUrl(originalUrl)) {
            logger.warn("Blocked redirect to potentially malicious URL: {}", originalUrl);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", originalUrl);

        return ResponseEntity.status(HttpStatus.FOUND)
            .headers(headers)
            .build();
    }
}
