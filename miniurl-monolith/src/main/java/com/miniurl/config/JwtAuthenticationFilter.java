package com.miniurl.config;

import com.miniurl.service.CustomUserDetailsService;
import com.miniurl.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(-100)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip JWT authentication for public endpoints
        if (requestPath.startsWith("/auth/signup") ||
            requestPath.startsWith("/auth/login") ||
            requestPath.startsWith("/auth/verify-otp") ||
            requestPath.startsWith("/auth/resend-otp") ||
            requestPath.equals("/api/health") ||
            requestPath.startsWith("/r/") ||
            requestPath.equals("/login") ||
            requestPath.equals("/") ||
            requestPath.startsWith("/css/") ||
            requestPath.startsWith("/js/") ||
            requestPath.startsWith("/images/") ||
            // Skip Swagger/OpenAPI endpoints
            requestPath.startsWith("/v3/api-docs") ||
            requestPath.startsWith("/swagger-ui") ||
            requestPath.startsWith("/swagger-resources") ||
            requestPath.startsWith("/webjars/") ||
            // Skip actuator endpoints
            requestPath.startsWith("/actuator/") ||
            // Skip API auth endpoints
            requestPath.startsWith("/api/auth/login") ||
            // Skip common static resources browsers request
            requestPath.equals("/favicon.ico") ||
            requestPath.equals("/sw.js") ||
            requestPath.equals("/manifest.json")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only process JWT if Authorization header is present
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Let Spring Security handle session-based auth for browser requests
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String username;

        try {
            username = jwtUtil.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Validate token: signature, expiration, and user match
                if (jwtUtil.validateToken(jwt, userDetails)) {
                    // Additional explicit expiration check for defense in depth
                    if (jwtUtil.isTokenExpired(jwt)) {
                        logger.debug("JWT token expired for user: {}", username);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Check if token needs renewal and add new token to response header
                    String newToken = jwtUtil.renewTokenIfNeeded(jwt, userDetails);
                    if (newToken != null) {
                        response.setHeader("X-Authorization", newToken);
                        response.setHeader("X-Token-Renewed", "true");
                    }
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Explicitly handle expired tokens
            logger.debug("JWT token expired: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        } catch (Exception e) {
            // Log error but continue - let the security chain handle it
            logger.debug("JWT validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
