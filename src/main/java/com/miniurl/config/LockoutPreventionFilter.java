package com.miniurl.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to prevent login attempts when account is already locked
 * Blocks POST requests to /login with error=locked parameter
 */
@Component
@Order(-200) // Run before other filters
public class LockoutPreventionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LockoutPreventionFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String errorParam = request.getParameter("error");
        String method = request.getMethod();
        
        // Block POST to /login?error=locked (prevents redirect loop)
        if ("POST".equalsIgnoreCase(method) && 
            "/login".equals(path) && 
            "locked".equals(errorParam)) {
            
            logger.warn("Blocked POST to /login?error=locked - redirecting to GET");
            // Redirect to GET instead of processing POST
            response.sendRedirect("/login?error=locked");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
