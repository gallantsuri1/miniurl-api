package com.miniurl.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting configuration using Bucket4j with Caffeine cache.
 *
 * Provides rate limit buckets for different endpoints:
 * - Login: 5 requests per 15 minutes (brute force protection)
 * - Password Reset: 3 requests per hour (email bombing prevention)
 * - OTP Verification: 5 requests per 15 minutes
 * - Signup: 5 requests per hour (per email bombing prevention)
 * - Email Verification: 10 requests per hour (token validation)
 * - URL Creation: 100 requests per hour
 * - General API: 300 requests per hour
 * - Redirects: 1000 requests per hour
 */
@Component
public class RateLimitConfig {

    // Caffeine caches with TTL expiration to prevent memory leaks
    // Entries automatically expire after period of inactivity
    private final Cache<String, Bucket> loginBuckets;
    private final Cache<String, Bucket> passwordResetBuckets;
    private final Cache<String, Bucket> otpBuckets;
    private final Cache<String, Bucket> signupBuckets;
    private final Cache<String, Bucket> emailVerificationBuckets;
    private final Cache<String, Bucket> emailInviteVerificationBuckets;
    private final Cache<String, Bucket> urlCreationBuckets;
    private final Cache<String, Bucket> generalApiBuckets;
    private final Cache<String, Bucket> redirectBuckets;

    @Value("${app.rate-limit.login.requests:5}")
    private int loginRequests;

    @Value("${app.rate-limit.login.seconds:900}")
    private int loginSeconds;

    @Value("${app.rate-limit.password-reset.requests:20}")
    private int passwordResetRequests;

    @Value("${app.rate-limit.password-reset.seconds:3600}")
    private int passwordResetSeconds;

    @Value("${app.rate-limit.otp.requests:5}")
    private int otpRequests;

    @Value("${app.rate-limit.otp.seconds:900}")
    private int otpSeconds;

    @Value("${app.rate-limit.signup.requests:5}")
    private int signupRequests;

    @Value("${app.rate-limit.signup.seconds:3600}")
    private int signupSeconds;

    @Value("${app.rate-limit.email-verification.requests:10}")
    private int emailVerificationRequests;

    @Value("${app.rate-limit.email-verification.seconds:3600}")
    private int emailVerificationSeconds;

    @Value("${app.rate-limit.email-invite-verification.requests:10}")
    private int emailInviteVerificationRequests;

    @Value("${app.rate-limit.email-invite-verification.seconds:3600}")
    private int emailInviteVerificationSeconds;

    @Value("${app.rate-limit.url-creation.requests:100}")
    private int urlCreationRequests;

    @Value("${app.rate-limit.url-creation.seconds:3600}")
    private int urlCreationSeconds;

    @Value("${app.rate-limit.general-api.requests:300}")
    private int generalApiRequests;

    @Value("${app.rate-limit.general-api.seconds:3600}")
    private int generalApiSeconds;

    @Value("${app.rate-limit.redirect.requests:1000}")
    private int redirectRequests;

    @Value("${app.rate-limit.redirect.seconds:3600}")
    private int redirectSeconds;

    public RateLimitConfig() {
        // Initialize Caffeine caches with TTL expiration
        // Entries expire after 1 hour of inactivity to prevent memory leaks
        // Maximum 10,000 entries per cache
        this.loginBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
        
        this.passwordResetBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
        
        this.otpBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
        
        this.signupBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.emailVerificationBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.emailInviteVerificationBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.urlCreationBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
        
        this.generalApiBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
        
        this.redirectBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    }

    /**
     * Get login rate limit bucket for IP
     */
    public Bucket getLoginBucket(String ip) {
        return loginBuckets.get(ip, k -> createLoginBucket(ip));
    }

    /**
     * Get password reset rate limit bucket for IP
     */
    public Bucket getPasswordResetBucket(String ip) {
        return passwordResetBuckets.get(ip, k -> createPasswordResetBucket(ip));
    }

    /**
     * Get OTP verification rate limit bucket for IP
     */
    public Bucket getOtpBucket(String ip) {
        return otpBuckets.get(ip, k -> createOtpBucket(ip));
    }

    /**
     * Get signup rate limit bucket for IP
     */
    public Bucket getSignupBucket(String ip) {
        return signupBuckets.get(ip, k -> createSignupBucket(ip));
    }

    /**
     * Get email verification rate limit bucket for IP
     */
    public Bucket getEmailVerificationBucket(String ip) {
        return emailVerificationBuckets.get(ip, k -> createEmailVerificationBucket(ip));
    }

    /**
     * Get email invite verification rate limit bucket for IP
     */
    public Bucket getEmailInviteVerificationBucket(String ip) {
        return emailInviteVerificationBuckets.get(ip, k -> createEmailInviteVerificationBucket(ip));
    }

    /**
     * Get URL creation rate limit bucket for IP
     */
    public Bucket getUrlCreationBucket(String ip) {
        return urlCreationBuckets.get(ip, k -> createUrlCreationBucket(ip));
    }

    /**
     * Get general API rate limit bucket for IP
     */
    public Bucket getGeneralApiBucket(String ip) {
        return generalApiBuckets.get(ip, k -> createGeneralApiBucket(ip));
    }

    /**
     * Get redirect rate limit bucket for IP
     */
    public Bucket getRedirectBucket(String ip) {
        return redirectBuckets.get(ip, k -> createRedirectBucket(ip));
    }

    /**
     * Create login bucket with rate limit
     */
    private Bucket createLoginBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(loginRequests, Refill.greedy(loginRequests, Duration.ofSeconds(loginSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create password reset bucket with rate limit
     */
    private Bucket createPasswordResetBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(passwordResetRequests, Refill.greedy(passwordResetRequests, Duration.ofSeconds(passwordResetSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create OTP bucket with rate limit
     */
    private Bucket createOtpBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(otpRequests, Refill.greedy(otpRequests, Duration.ofSeconds(otpSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create signup bucket with rate limit
     */
    private Bucket createSignupBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(signupRequests, Refill.greedy(signupRequests, Duration.ofSeconds(signupSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create email verification bucket with rate limit
     */
    private Bucket createEmailVerificationBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(emailVerificationRequests, Refill.greedy(emailVerificationRequests, Duration.ofSeconds(emailVerificationSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create email invite verification bucket with rate limit
     */
    private Bucket createEmailInviteVerificationBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(emailInviteVerificationRequests, Refill.greedy(emailInviteVerificationRequests, Duration.ofSeconds(emailInviteVerificationSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create URL creation bucket with rate limit
     */
    private Bucket createUrlCreationBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(urlCreationRequests, Refill.greedy(urlCreationRequests, Duration.ofSeconds(urlCreationSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create general API bucket with rate limit
     */
    private Bucket createGeneralApiBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(generalApiRequests, Refill.greedy(generalApiRequests, Duration.ofSeconds(generalApiSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create redirect bucket with rate limit
     */
    private Bucket createRedirectBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(redirectRequests, Refill.greedy(redirectRequests, Duration.ofSeconds(redirectSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Cleanup old buckets to prevent memory leaks
     * With Caffeine, entries automatically expire after TTL
     * This method provides monitoring and manual cleanup if needed
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanup() {
        // Caffeine automatically expires entries, but we can force cleanup
        // and log statistics for monitoring
        long totalEntries = loginBuckets.estimatedSize() +
                           passwordResetBuckets.estimatedSize() +
                           otpBuckets.estimatedSize() +
                           signupBuckets.estimatedSize() +
                           emailVerificationBuckets.estimatedSize() +
                           emailInviteVerificationBuckets.estimatedSize() +
                           urlCreationBuckets.estimatedSize() +
                           generalApiBuckets.estimatedSize() +
                           redirectBuckets.estimatedSize();

        // Force expiration of stale entries
        loginBuckets.cleanUp();
        passwordResetBuckets.cleanUp();
        otpBuckets.cleanUp();
        signupBuckets.cleanUp();
        emailVerificationBuckets.cleanUp();
        emailInviteVerificationBuckets.cleanUp();
        urlCreationBuckets.cleanUp();
        generalApiBuckets.cleanUp();
        redirectBuckets.cleanUp();
        
        if (totalEntries > 1000) {
            System.out.printf("RateLimitConfig: Cleaned up stale entries. Remaining: %d%n", 
                loginBuckets.estimatedSize() + passwordResetBuckets.estimatedSize() +
                otpBuckets.estimatedSize() + signupBuckets.estimatedSize() +
                urlCreationBuckets.estimatedSize() + generalApiBuckets.estimatedSize() +
                redirectBuckets.estimatedSize());
        }
    }
}
