package com.miniurl.service;

import com.miniurl.dto.CreateUrlRequest;
import com.miniurl.dto.PagedResponse;
import com.miniurl.dto.PageableRequest;
import com.miniurl.dto.UrlResponse;
import com.miniurl.entity.Url;
import com.miniurl.entity.User;
import com.miniurl.exception.AliasNotAvailableException;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.exception.UrlLimitExceededException;
import com.miniurl.exception.UrlValidationException;
import com.miniurl.repository.UrlRepository;
import com.miniurl.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UrlService {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SHORT_CODE_LENGTH = 6;

    // Blocked domains for URL shortening
    private static final Set<String> BLOCKED_DOMAINS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0", "::1",
        "metadata.google.internal", "169.254.169.254",
        "instance-data", "metadata.azure.com", "metadata.digitalocean.com"
    );

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final UrlUsageLimitService urlUsageLimitService;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.ui-base-url:http://localhost:3000}")
    private String uiBaseUrl;

    public UrlService(UrlRepository urlRepository, UserRepository userRepository,
                      UrlUsageLimitService urlUsageLimitService) {
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.urlUsageLimitService = urlUsageLimitService;
    }

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateShortCode() {
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private String generateUniqueShortCode() {
        String shortCode;
        int attempts = 0;
        int maxAttempts = 100;
        long baseDelayMs = 10; // Start with 10ms delay
        
        do {
            shortCode = generateShortCode();
            attempts++;
            
            if (attempts > maxAttempts) {
                throw new RuntimeException("Failed to generate unique short code after " + maxAttempts + " attempts. Possible high collision rate.");
            }
            
            // Check if code exists
            if (urlRepository.existsByShortCode(shortCode)) {
                // Exponential backoff: delay = baseDelay * 2^(attempt-1)
                // Cap delay at 100ms to avoid excessive waiting
                long delayMs = Math.min(baseDelayMs * (1L << (attempts - 1)), 100);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while generating short code", e);
                }
            }
        } while (urlRepository.existsByShortCode(shortCode));
        
        return shortCode;
    }

    /**
     * Validate URL format and security
     */
    private void validateUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new UrlValidationException("URL cannot be empty");
        }

        // Block dangerous protocols
        String lowerUrl = urlString.toLowerCase().trim();
        if (lowerUrl.startsWith("javascript:") ||
            lowerUrl.startsWith("data:") ||
            lowerUrl.startsWith("vbscript:") ||
            lowerUrl.startsWith("file:") ||
            lowerUrl.startsWith("ftp:") ||
            lowerUrl.startsWith("about:")) {
            throw new UrlValidationException("URL protocol not allowed");
        }

        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol();
            
            // Only allow http and https
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                throw new UrlValidationException("URL must use http or https protocol");
            }

            // Validate host
            String host = url.getHost();
            if (host == null || host.isEmpty()) {
                throw new UrlValidationException("Invalid URL: missing host");
            }

            // Block self-referencing URLs (app's own domain) — check before generic blocked domains
            if (isSelfReferencingUrl(host)) {
                throw new UrlValidationException("Shortening URLs for this domain is not allowed");
            }

            // Check against blocked domains
            if (BLOCKED_DOMAINS.contains(host.toLowerCase())) {
                throw new UrlValidationException("URL host is not allowed");
            }

            // Block private/internal IP addresses (SSRF protection)
            if (isPrivateOrInternalIp(host)) {
                throw new UrlValidationException("Internal/private IP addresses are not allowed");
            }

            // Block cloud metadata endpoints
            if (host.endsWith(".internal") ||
                host.equals("metadata.google.internal") ||
                host.equals("169.254.169.254") ||
                host.contains("metadata")) {
                throw new UrlValidationException("Cloud metadata endpoints are not allowed");
            }

        } catch (MalformedURLException e) {
            throw new UrlValidationException("Invalid URL format");
        }
    }

    /**
     * Check if the host matches the app's own domain (self-referencing URL block).
     * Compares normalized hosts (lowercase, stripped of port and www prefix).
     */
    private boolean isSelfReferencingUrl(String host) {
        String normalizedHost = normalizeHost(host);
        String normalizedBaseUrl = normalizeHost(extractHost(baseUrl));
        String normalizedUiUrl = normalizeHost(extractHost(uiBaseUrl));

        return (!normalizedBaseUrl.isEmpty() && normalizedHost.equals(normalizedBaseUrl)) ||
               (!normalizedUiUrl.isEmpty() && normalizedHost.equals(normalizedUiUrl));
    }

    /**
     * Extract host from a full URL string (e.g., "https://url.suricloud.uk:443/path" → "url.suricloud.uk")
     */
    private String extractHost(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getHost();
        } catch (MalformedURLException e) {
            return "";
        }
    }

    /**
     * Normalize host: lowercase, strip www prefix.
     */
    private String normalizeHost(String host) {
        if (host == null || host.isEmpty()) return "";
        String h = host.toLowerCase().trim();
        if (h.startsWith("www.")) h = h.substring(4);
        return h;
    }

    /**
     * Check if host is a private or internal IP address
     */
    private boolean isPrivateOrInternalIp(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() ||
                    addr.isAnyLocalAddress() ||
                    addr.isLinkLocalAddress() ||
                    addr.isSiteLocalAddress()) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            // If we can't resolve it, allow it (will fail later if invalid)
            return false;
        }
    }

    private void validateAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return;
        }

        if (alias.length() < 3) {
            throw new UrlValidationException("Alias must be at least 3 characters");
        }

        if (alias.length() > 10) {
            throw new UrlValidationException("Alias must be 10 characters or less");
        }

        if (!alias.matches("^[a-zA-Z0-9]+$")) {
            throw new UrlValidationException("Alias must contain only alphanumeric characters (letters and numbers)");
        }
    }

    @Transactional
    public UrlResponse createUrl(CreateUrlRequest request, Long userId) {
        validateUrl(request.getUrl());
        validateAlias(request.getAlias());

        // Check and increment URL creation limits
        urlUsageLimitService.checkAndIncrementUrlCreation(userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String shortCode;
        if (request.getAlias() != null && !request.getAlias().trim().isEmpty()) {
            shortCode = request.getAlias().trim();
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new AliasNotAvailableException("Alias not available");
            }
        } else {
            shortCode = generateUniqueShortCode();
        }

        Url url = Url.builder()
            .originalUrl(request.getUrl())
            .shortCode(shortCode)
            .user(user)
            .build();

        Url savedUrl = urlRepository.save(url);

        return convertToResponse(savedUrl);
    }

    public List<UrlResponse> getUserUrls(Long userId) {
        List<Url> urls = urlRepository.findByUserId(userId);
        return urls.stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get user's URLs with pagination and sorting
     */
    public PagedResponse<UrlResponse> getUserUrls(Long userId, PageableRequest pageableRequest) {
        // Validate sort field
        String sortBy = validateSortField(pageableRequest.getSortBy());
        
        // Create PageRequest with sorting
        Sort sort = pageableRequest.isAscending() ? 
            Sort.by(sortBy).ascending() : 
            Sort.by(sortBy).descending();
        
        PageRequest pageRequest = PageRequest.of(
            pageableRequest.getPage(), 
            pageableRequest.getSize(), 
            sort
        );

        Page<Url> urlPage = urlRepository.findByUserId(userId, pageRequest);
        List<UrlResponse> content = urlPage.getContent().stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());

        return PagedResponse.<UrlResponse>builder()
            .content(content)
            .page(pageableRequest.getPage())
            .size(pageableRequest.getSize())
            .totalElements(urlPage.getTotalElements())
            .sortBy(sortBy)
            .sortDirection(pageableRequest.getSortDirection())
            .build();
    }

    private String validateSortField(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return "createdAt";
        }
        
        // Allow only specific fields to prevent SQL injection
        Set<String> allowedFields = Set.of("id", "originalUrl", "shortCode", "accessCount", "createdAt");
        String field = sortBy.trim();
        
        if (!allowedFields.contains(field)) {
            return "createdAt"; // Default to createdAt if invalid field
        }
        
        return field;
    }

    @Transactional
    public void deleteUrl(Long urlId, Long userId) {
        Url url = urlRepository.findByIdAndUserId(urlId, userId)
            .orElseThrow(() -> {
                if (urlRepository.existsById(urlId)) {
                    return new UnauthorizedException("You can only delete your own URLs");
                }
                return new ResourceNotFoundException("URL not found");
            });

        urlRepository.delete(url);
    }

    @Transactional
    public Optional<Url> findByShortCode(String shortCode) {
        Optional<Url> url = urlRepository.findByShortCode(shortCode);
        url.ifPresent(u -> {
            u.setAccessCount(u.getAccessCount() + 1);
            urlRepository.save(u);
        });
        return url;
    }

    private UrlResponse convertToResponse(Url url) {
        String shortUrl = baseUrl + "/r/" + url.getShortCode();
        return UrlResponse.builder()
            .id(url.getId())
            .originalUrl(url.getOriginalUrl())
            .shortCode(url.getShortCode())
            .shortUrl(shortUrl)
            .accessCount(url.getAccessCount())
            .createdAt(url.getCreatedAt())
            .build();
    }

    /**
     * Get URL creation usage statistics for a user
     */
    public UrlUsageLimitService.UrlUsageStats getUsageStats(Long userId) {
        return urlUsageLimitService.getUsageStats(userId);
    }
}
