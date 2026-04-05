package com.miniurl.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Rate limiting filter that applies IP-based rate limiting to all requests,
 * with dual-layer (per-IP + per-username/email) rate limiting for login,
 * password reset, and OTP endpoints.
 *
 * Rate limits by endpoint (production defaults):
 * - /auth/login, /api/auth/login: 100 req/15min per IP, 5 req/5min per username
 * - /auth/forgot-password, /api/auth/forgot-password: 60 req/hr per IP, 3 req/hr per email
 * - /auth/verify-otp, /auth/resend-otp, /api/auth/*: 30 req/15min per IP, 5 req/5min per email/username
 * - /auth/signup, /api/auth/signup: 20 requests per hour
 * - /api/auth/verify-email: 50 requests per hour (password reset token validation)
 * - /api/auth/verify-email-invite: 50 requests per hour (email invite token validation)
 * - /api/urls (POST): 500 requests per hour
 * - /api/** (general): 1000 requests per hour
 * - /r/** (redirects): 5000 requests per hour
 */
@Component
@Order(1) // Run early in the filter chain
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

        // For login endpoints: check both per-IP and per-username limits
        if (isLoginPath(requestURI)) {
            // Wrap request to cache body (so downstream filters can still read it)
            RepeatableRequestWrapper wrappedRequest = new RepeatableRequestWrapper(request);
            String username = extractUsername(wrappedRequest);

            Bucket ipBucket = rateLimitConfig.getLoginBucket(clientIp);
            Bucket userBucket = username != null ? rateLimitConfig.getLoginByUsernameBucket(username) : null;

            boolean ipAllowed = ipBucket.tryConsume(1);
            boolean userAllowed = userBucket == null || userBucket.tryConsume(1);

            if (!ipAllowed || !userAllowed) {
                String reason = !userAllowed
                    ? "Rate limit exceeded for username: " + username
                    : "Rate limit exceeded for IP: " + clientIp;
                logger.warn("Rate limit exceeded on path: {} | {}", requestURI, reason);

                response.setStatus(429);
                response.setContentType("application/json");
                response.setHeader("X-RateLimit-Limit", "1");
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("Retry-After", "300");
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}"
                );
                return;
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // For password reset endpoints: check both per-IP and per-email limits
        if (isPasswordResetPath(requestURI)) {
            RepeatableRequestWrapper wrappedRequest = new RepeatableRequestWrapper(request);
            String email = extractField(wrappedRequest, "email");

            Bucket ipBucket = rateLimitConfig.getPasswordResetBucket(clientIp);
            Bucket emailBucket = email != null ? rateLimitConfig.getPasswordResetByEmailBucket(email) : null;

            boolean ipAllowed = ipBucket.tryConsume(1);
            boolean emailAllowed = emailBucket == null || emailBucket.tryConsume(1);

            if (!ipAllowed || !emailAllowed) {
                String reason = !emailAllowed
                    ? "Rate limit exceeded for email: " + email
                    : "Rate limit exceeded for IP: " + clientIp;
                logger.warn("Rate limit exceeded on path: {} | {}", requestURI, reason);

                response.setStatus(429);
                response.setContentType("application/json");
                response.setHeader("X-RateLimit-Limit", "1");
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("Retry-After", "3600");
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}"
                );
                return;
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // For OTP endpoints: check both per-IP and per-email/username limits
        if (isOtpPath(requestURI)) {
            RepeatableRequestWrapper wrappedRequest = new RepeatableRequestWrapper(request);
            // Try username first (for verify-otp), then email (for resend-otp)
            String identifier = extractField(wrappedRequest, "username");
            if (identifier == null) {
                identifier = extractField(wrappedRequest, "email");
            }

            Bucket ipBucket = rateLimitConfig.getOtpBucket(clientIp);
            Bucket idBucket = identifier != null ? rateLimitConfig.getOtpByEmailBucket(identifier) : null;

            boolean ipAllowed = ipBucket.tryConsume(1);
            boolean idAllowed = idBucket == null || idBucket.tryConsume(1);

            if (!ipAllowed || !idAllowed) {
                String reason = !idAllowed
                    ? "Rate limit exceeded for: " + identifier
                    : "Rate limit exceeded for IP: " + clientIp;
                logger.warn("Rate limit exceeded on path: {} | {}", requestURI, reason);

                response.setStatus(429);
                response.setContentType("application/json");
                response.setHeader("X-RateLimit-Limit", "1");
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setHeader("Retry-After", "300");
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}"
                );
                return;
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // For all other endpoints: per-IP rate limiting only
        Bucket bucket = getBucketForPath(requestURI, clientIp);

        if (bucket != null && !bucket.tryConsume(1)) {
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, requestURI);

            response.setStatus(429);
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
     * Check if the path is a login endpoint
     */
    private boolean isLoginPath(String path) {
        return path.startsWith("/auth/login") || path.startsWith("/api/auth/login");
    }

    /**
     * Check if the path is a password reset endpoint
     */
    private boolean isPasswordResetPath(String path) {
        return path.startsWith("/auth/forgot-password") || path.startsWith("/api/auth/forgot-password");
    }

    /**
     * Check if the path is an OTP endpoint
     */
    private boolean isOtpPath(String path) {
        return path.startsWith("/auth/verify-otp") || path.startsWith("/auth/resend-otp") ||
               path.startsWith("/api/auth/verify-otp") || path.startsWith("/api/auth/resend-otp");
    }

    /**
     * Extract username from the cached request body
     */
    private String extractUsername(RepeatableRequestWrapper wrappedRequest) {
        return extractField(wrappedRequest, "username");
    }

    /**
     * Extract a specific field from the cached request body
     */
    private String extractField(RepeatableRequestWrapper wrappedRequest, String fieldName) {
        try {
            byte[] content = wrappedRequest.getContentAsByteArray();
            if (content.length > 0) {
                JsonNode jsonNode = objectMapper.readTree(content);
                JsonNode usernameNode = jsonNode.get("username");
                if (usernameNode != null && usernameNode.isTextual()) {
                    return usernameNode.asText();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not extract username from request body: {}", e.getMessage());
        }
        return null;
    }

    /**
     * HttpServletRequestWrapper that caches the body so it can be read multiple times
     */
    private static class RepeatableRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        public RepeatableRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        public byte[] getContentAsByteArray() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    /**
     * ServletInputStream that reads from a cached byte array
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Not needed for synchronous processing
        }
    }

    /**
     * Get the appropriate rate limit bucket for the request path
     */
    private Bucket getBucketForPath(String path, String ip) {
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
        if (path.startsWith("/api/urls")) {
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
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            ip = ip.split(",")[0].trim();
            return ip;
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

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

        ip = request.getRemoteAddr();

        if ("0:0:0:0:0:0:0:1".equals(ip) || "127.0.0.1".equals(ip)) {
            return "localhost";
        }

        return ip;
    }
}
