package com.miniurl.config;

import com.miniurl.dto.ApiResponse;
import com.miniurl.exception.ResourceNotFoundException;
import com.miniurl.exception.UnauthorizedException;
import com.miniurl.exception.UrlValidationException;
import com.miniurl.exception.AliasNotAvailableException;
import com.miniurl.exception.UrlLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 * Handles all exceptions and returns appropriate error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        logger.debug("Validation error: {}", errors);
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("Validation failed: " + String.join(", ", errors.values())));
    }

    /**
     * Handle resource not found errors (404 Not Found)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        logger.debug("Resource not found: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handle unauthorized errors (401 Unauthorized)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse> handleUnauthorizedException(UnauthorizedException ex) {
        logger.debug("Unauthorized: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handle bad credentials errors (401 Unauthorized)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse> handleBadCredentialsException(BadCredentialsException ex) {
        logger.debug("Bad credentials: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Invalid username or password"));
    }

    /**
     * Handle URL validation errors (400 Bad Request)
     */
    @ExceptionHandler(UrlValidationException.class)
    public ResponseEntity<ApiResponse> handleUrlValidationException(UrlValidationException ex) {
        logger.debug("URL validation error: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handle alias not available errors (400 Bad Request)
     */
    @ExceptionHandler(AliasNotAvailableException.class)
    public ResponseEntity<ApiResponse> handleAliasNotAvailableException(AliasNotAvailableException ex) {
        logger.debug("Alias not available: {}", ex.getMessage());
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handle URL limit exceeded errors (429 Too Many Requests)
     */
    @ExceptionHandler(UrlLimitExceededException.class)
    public ResponseEntity<ApiResponse> handleUrlLimitExceededException(UrlLimitExceededException ex) {
        logger.warn("URL limit exceeded: {} - {}", ex.getLimitType(), ex.getMessage());
        
        ApiResponse response = ApiResponse.builder()
            .success(false)
            .message(ex.getMessage())
            .data(Map.of(
                "limitType", ex.getLimitType(),
                "limit", ex.getLimit(),
                "currentCount", ex.getCurrentCount(),
                "retryMessage", ex.getRetryMessage(),
                "retryAfter", ex.getRetryAfter()
            ))
            .build();
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(response);
    }

    /**
     * Handle all other exceptions (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGlobalException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
