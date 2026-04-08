package com.miniurl.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Resilience4j configuration for Circuit Breaker, Bulkhead, and Retry patterns.
 * 
 * Provides programmatic configuration and access to resilience patterns.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Circuit Breaker Registry with custom configurations
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Database circuit breaker - more tolerant
        CircuitBreakerConfig dbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(60)
            .waitDurationInOpenState(Duration.ofSeconds(20))
            .permittedNumberOfCallsInHalfOpenState(10)
            .minimumNumberOfCalls(5)
            .slidingWindowSize(100)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                java.sql.SQLException.class,
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                org.springframework.dao.DataAccessException.class
            )
            .build();

        // Email service circuit breaker - longer wait time
        CircuitBreakerConfig emailConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(5)
            .minimumNumberOfCalls(3)
            .slidingWindowSize(50)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(jakarta.mail.MessagingException.class)
            .build();

        // URL validation circuit breaker
        CircuitBreakerConfig urlConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(70)
            .waitDurationInOpenState(Duration.ofSeconds(15))
            .permittedNumberOfCallsInHalfOpenState(10)
            .minimumNumberOfCalls(5)
            .build();

        Map<String, CircuitBreakerConfig> configs = new HashMap<>();
        configs.put("database", dbConfig);
        configs.put("emailService", emailConfig);
        configs.put("urlValidation", urlConfig);

        return CircuitBreakerRegistry.of(configs);
    }

    /**
     * Bulkhead Registry with custom configurations for thread pool isolation
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        // URL creation bulkhead
        BulkheadConfig urlCreationConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(50)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build();

        // Email bulkhead - prevent email issues from blocking other operations
        BulkheadConfig emailConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(20)
            .maxWaitDuration(Duration.ofSeconds(10))
            .build();

        // Admin operations bulkhead
        BulkheadConfig adminConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(30)
            .maxWaitDuration(Duration.ofSeconds(5))
            .build();

        // Redirect bulkhead - high throughput
        BulkheadConfig redirectConfig = BulkheadConfig.custom()
            .maxConcurrentCalls(200)
            .maxWaitDuration(Duration.ofSeconds(2))
            .build();

        Map<String, BulkheadConfig> configs = new HashMap<>();
        configs.put("urlCreation", urlCreationConfig);
        configs.put("email", emailConfig);
        configs.put("admin", adminConfig);
        configs.put("redirect", redirectConfig);

        return BulkheadRegistry.of(configs);
    }

    /**
     * Retry Registry with custom configurations
     */
    @Bean
    public RetryRegistry retryRegistry() {
        // Database retry - quick retries with interval function
        RetryConfig dbRetryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2))
            .retryExceptions(
                java.sql.SQLException.class,
                java.net.ConnectException.class,
                java.net.SocketTimeoutException.class,
                org.springframework.dao.TransientDataAccessResourceException.class
            )
            .ignoreExceptions(java.net.UnknownHostException.class)
            .build();

        // Email retry - longer wait between retries
        RetryConfig emailRetryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(2000, 2))
            .retryExceptions(jakarta.mail.MessagingException.class)
            .build();

        // URL validation retry
        RetryConfig urlRetryConfig = RetryConfig.custom()
            .maxAttempts(2)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2))
            .build();

        Map<String, RetryConfig> configs = new HashMap<>();
        configs.put("database", dbRetryConfig);
        configs.put("emailService", emailRetryConfig);
        configs.put("urlValidation", urlRetryConfig);

        return RetryRegistry.of(configs);
    }

    /**
     * Get database circuit breaker
     */
    public CircuitBreaker databaseCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("database");
    }

    /**
     * Get email circuit breaker
     */
    public CircuitBreaker emailCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("emailService");
    }

    /**
     * Get URL creation bulkhead
     */
    public Bulkhead urlCreationBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead("urlCreation");
    }

    /**
     * Get email bulkhead
     */
    public Bulkhead emailBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead("email");
    }
}
