package com.miniurl.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter that applies IP-based rate limiting to all requests.
 *
 * Rate limits by endpoint:
 * - /auth/login, /api/auth/login: 5 requests per 15 minutes
 * - /auth/forgot-password, /api/auth/forgot-password: 20 requests per hour
 * - /auth/verify-otp, /auth/resend-otp, /api/auth/*: 5 requests per 15 minutes
 * - /auth/signup, /api/auth/signup: 5 requests per hour
 * - /api/auth/verify-email: 10 requests per hour (password reset token validation)
 * - /api/auth/verify-email-invite: 10 requests per hour (email invite token validation)
 * - /api/urls (POST): 100 requests per hour
 * - /api/** (general): 300 requests per hour
 * - /r/** (redirects): 1000 requests per hour
 */
@Component
@Order(1) // Run early in the filter chain
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimitConfig rateLimitConfig;

    public RateLimitingFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String clientIp = getClientIp(request);

        // Skip rate limiting for static resources and health checks
        if (isExcludedPath(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get appropriate bucket based on endpoint
        Bucket bucket = getBucketForPath(requestURI, clientIp);

        if (bucket != null && !bucket.tryConsume(1)) {
            // Rate limit exceeded
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, requestURI);
            
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json");
            response.setHeader("X-RateLimit-Limit", "1");
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Get the appropriate rate limit bucket for the request path
     */
    private Bucket getBucketForPath(String path, String ip) {
        if (path.startsWith("/auth/login") || path.startsWith("/api/auth/login")) {
            return rateLimitConfig.getLoginBucket(ip);
        }
        if (path.startsWith("/auth/forgot-password") || path.startsWith("/api/auth/forgot-password")) {
            return rateLimitConfig.getPasswordResetBucket(ip);
        }
        if (path.startsWith("/auth/verify-otp") || path.startsWith("/auth/resend-otp") ||
            path.startsWith("/api/auth/verify-otp") || path.startsWith("/api/auth/resend-otp")) {
            return rateLimitConfig.getOtpBucket(ip);
        }
        if (path.startsWith("/auth/signup") || path.startsWith("/api/auth/signup")) {
            return rateLimitConfig.getSignupBucket(ip);
        }
        if (path.startsWith("/auth/verify-email") || path.startsWith("/api/auth/verify-email")) {
            return rateLimitConfig.getEmailVerificationBucket(ip);
        }
        if (path.startsWith("/auth/verify-email-invite") || path.startsWith("/api/auth/verify-email-invite")) {
            return rateLimitConfig.getEmailInviteVerificationBucket(ip);
        }
        if (path.startsWith("/api/urls") && "POST".equals("POST")) {
            return rateLimitConfig.getUrlCreationBucket(ip);
        }
        if (path.startsWith("/r/")) {
            return rateLimitConfig.getRedirectBucket(ip);
        }
        if (path.startsWith("/api/")) {
            return rateLimitConfig.getGeneralApiBucket(ip);
        }
        return null; // No rate limit for other paths
    }

    /**
     * Check if path should be excluded from rate limiting
     */
    private boolean isExcludedPath(String path) {
        return path.startsWith("/actuator/") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/swagger-resources") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.equals("/favicon.ico") ||
               path.equals("/sw.js") ||
               path.equals("/manifest.json") ||
               // Exclude web pages (no rate limiting for browsing)
               path.equals("/login") ||
               path.equals("/") ||
               path.equals("/signup") ||
               path.equals("/forgot-password") ||
               path.equals("/reset-password") ||
               path.equals("/activate") ||
               path.equals("/set-password") ||
               path.startsWith("/dashboard") ||
               path.startsWith("/profile") ||
               path.startsWith("/settings") ||
               path.startsWith("/admin");
    }

    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header (for requests behind proxy/load balancer)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first one (client IP)
            ip = ip.split(",")[0].trim();
            return ip;
        }

        // Check X-Real-IP header (for Nginx proxy)
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // Check other proxy headers
        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("CF-Connecting-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // Fallback to remote address
        ip = request.getRemoteAddr();
        
        // Handle IPv6 localhost
        if ("0:0:0:0:0:0:0:1".equals(ip) || "127.0.0.1".equals(ip)) {
            return "localhost";
        }
        
        return ip;
    }
}
