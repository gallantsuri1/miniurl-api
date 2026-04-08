package com.miniurl.config;

import com.miniurl.entity.User;
import com.miniurl.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Custom authentication failure handler that implements account lockout
 * after 5 failed login attempts for 5 minutes
 */
@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class);

    private final UserRepository userRepository;

    public CustomAuthenticationFailureHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        
        String username = request.getParameter("username");
        String errorParam = request.getParameter("error");
        
        logger.info("=== Authentication Failure Handler ===");
        logger.info("Username: {}, Error param: {}", username, errorParam);
        
        // If already locked, don't process again - just redirect back
        if ("locked".equals(errorParam)) {
            logger.warn("Authentication attempt on already locked error page - redirecting to /login?error=locked");
            setDefaultFailureUrl("/login?error=locked");
            super.onAuthenticationFailure(request, response, exception);
            return;
        }
        
        // Find user and increment failed attempts
        if (username != null && !username.isEmpty()) {
            User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);
            
            if (user != null) {
                logger.info("Found user: {}, Failed attempts before: {}, Is locked: {}", 
                    username, user.getFailedLoginAttempts(), user.isAccountLocked());
                
                // Check if account is already locked (before this attempt)
                if (user.isAccountLocked()) {
                    logger.warn("Login attempt on already locked account: {} - redirecting to error=locked", username);
                    setDefaultFailureUrl("/login?error=locked");
                } else {
                    // Increment failed attempts
                    user.incrementFailedLoginAttempts();
                    userRepository.save(user);
                    
                    logger.info("Failed attempts after increment: {}, Is locked now: {}", 
                        user.getFailedLoginAttempts(), user.isAccountLocked());
                    
                    // Check if account just got locked on THIS attempt
                    if (user.isAccountLocked()) {
                        logger.warn("🔒 Account LOCKED after {} failed attempts: {} - redirecting to error=locked", 
                            user.getFailedLoginAttempts(), username);
                        setDefaultFailureUrl("/login?error=locked");
                    } else {
                        logger.debug("Failed login attempt {} for user: {} - redirecting to error=true", 
                            user.getFailedLoginAttempts(), username);
                        setDefaultFailureUrl("/login?error=true");
                    }
                }
            } else {
                logger.debug("User not found: {} - redirecting to error=true", username);
                // User not found - generic error
                setDefaultFailureUrl("/login?error=true");
            }
        } else {
            logger.debug("No username provided - redirecting to error=true");
            // No username provided - generic error
            setDefaultFailureUrl("/login?error=true");
        }
        
        logger.info("Redirecting to failure URL");
        // Call parent to handle the redirect
        super.onAuthenticationFailure(request, response, exception);
    }
}
