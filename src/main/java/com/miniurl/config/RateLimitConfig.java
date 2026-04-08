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
 * Provides rate limit buckets for different endpoints (production defaults):
 * - Login (per-IP): 100 requests per 15 minutes (allows shared NATs/corporate networks)
 * - Login (per-username): 5 requests per 5 minutes (brute-force protection, works for non-existent users too)
 * - Password Reset (per-IP): 60 requests per hour
 * - Password Reset (per-email): 3 requests per hour (prevents targeting specific accounts)
 * - OTP Verification (per-IP): 30 requests per 15 minutes
 * - OTP Verification (per-email/username): 5 requests per 5 minutes (brute-force protection)
 * - Signup: 20 requests per hour
 * - Email Verification: 50 requests per hour
 * - Email Invite Verification: 50 requests per hour
 * - URL Creation: 500 requests per hour
 * - General API: 1000 requests per hour
 * - Redirects: 5000 requests per hour
 */
@Component
public class RateLimitConfig {

    // Caffeine caches with TTL expiration to prevent memory leaks
    // Entries automatically expire after period of inactivity
    private final Cache<String, Bucket> loginBuckets;             // per-IP
    private final Cache<String, Bucket> loginByUsernameBuckets;   // per-username
    private final Cache<String, Bucket> passwordResetBuckets;     // per-IP
    private final Cache<String, Bucket> passwordResetByEmailBuckets; // per-email
    private final Cache<String, Bucket> otpBuckets;               // per-IP
    private final Cache<String, Bucket> otpByEmailBuckets;        // per-email/username
    private final Cache<String, Bucket> signupBuckets;
    private final Cache<String, Bucket> emailVerificationBuckets;
    private final Cache<String, Bucket> emailInviteVerificationBuckets;
    private final Cache<String, Bucket> urlCreationBuckets;
    private final Cache<String, Bucket> generalApiBuckets;
    private final Cache<String, Bucket> redirectBuckets;

    // --- Login: per-IP (high limit for shared NATs) ---
    @Value("${app.rate-limit.login.requests:100}")
    private int loginRequests;

    @Value("${app.rate-limit.login.seconds:900}")
    private int loginSeconds;

    // --- Login: per-username (brute-force protection, works for non-existent users) ---
    @Value("${app.rate-limit.login-by-username.requests:5}")
    private int loginByUsernameRequests;

    @Value("${app.rate-limit.login-by-username.seconds:300}")
    private int loginByUsernameSeconds;

    @Value("${app.rate-limit.password-reset.requests:60}")
    private int passwordResetRequests;

    @Value("${app.rate-limit.password-reset.seconds:3600}")
    private int passwordResetSeconds;

    // --- Password Reset: per-email (prevents targeting specific accounts) ---
    @Value("${app.rate-limit.password-reset-by-email.requests:3}")
    private int passwordResetByEmailRequests;

    @Value("${app.rate-limit.password-reset-by-email.seconds:3600}")
    private int passwordResetByEmailSeconds;

    @Value("${app.rate-limit.otp.requests:30}")
    private int otpRequests;

    @Value("${app.rate-limit.otp.seconds:900}")
    private int otpSeconds;

    // --- OTP: per-email/username (brute-force protection) ---
    @Value("${app.rate-limit.otp-by-email.requests:5}")
    private int otpByEmailRequests;

    @Value("${app.rate-limit.otp-by-email.seconds:300}")
    private int otpByEmailSeconds;

    @Value("${app.rate-limit.signup.requests:20}")
    private int signupRequests;

    @Value("${app.rate-limit.signup.seconds:3600}")
    private int signupSeconds;

    @Value("${app.rate-limit.email-verification.requests:50}")
    private int emailVerificationRequests;

    @Value("${app.rate-limit.email-verification.seconds:3600}")
    private int emailVerificationSeconds;

    @Value("${app.rate-limit.email-invite-verification.requests:50}")
    private int emailInviteVerificationRequests;

    @Value("${app.rate-limit.email-invite-verification.seconds:3600}")
    private int emailInviteVerificationSeconds;

    @Value("${app.rate-limit.url-creation.requests:500}")
    private int urlCreationRequests;

    @Value("${app.rate-limit.url-creation.seconds:3600}")
    private int urlCreationSeconds;

    @Value("${app.rate-limit.general-api.requests:1000}")
    private int generalApiRequests;

    @Value("${app.rate-limit.general-api.seconds:3600}")
    private int generalApiSeconds;

    @Value("${app.rate-limit.redirect.requests:5000}")
    private int redirectRequests;

    @Value("${app.rate-limit.redirect.seconds:3600}")
    private int redirectSeconds;

    public RateLimitConfig() {
        // Initialize Caffeine caches with TTL expiration
        // Entries expire after 1 hour of inactivity to prevent memory leaks
        // Maximum 10,000 entries per cache (100,000 for redirects)
        this.loginBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.loginByUsernameBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

        this.passwordResetBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.passwordResetByEmailBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.otpBuckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

        this.otpByEmailBuckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(5, TimeUnit.MINUTES)
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
     * Get login rate limit bucket for IP (high limit for shared NATs)
     */
    public Bucket getLoginBucket(String ip) {
        return loginBuckets.get(ip, k -> createLoginBucket(ip));
    }

    /**
     * Get login rate limit bucket for username (brute-force protection, works for non-existent users)
     */
    public Bucket getLoginByUsernameBucket(String username) {
        return loginByUsernameBuckets.get(username, k -> createLoginByUsernameBucket(username));
    }

    /**
     * Get password reset rate limit bucket for IP
     */
    public Bucket getPasswordResetBucket(String ip) {
        return passwordResetBuckets.get(ip, k -> createPasswordResetBucket(ip));
    }

    /**
     * Get password reset rate limit bucket for email (prevents targeting specific accounts)
     */
    public Bucket getPasswordResetByEmailBucket(String email) {
        return passwordResetByEmailBuckets.get(email, k -> createPasswordResetByEmailBucket(email));
    }

    /**
     * Get OTP verification rate limit bucket for IP
     */
    public Bucket getOtpBucket(String ip) {
        return otpBuckets.get(ip, k -> createOtpBucket(ip));
    }

    /**
     * Get OTP verification rate limit bucket for email/username (brute-force protection)
     */
    public Bucket getOtpByEmailBucket(String emailOrUsername) {
        return otpByEmailBuckets.get(emailOrUsername, k -> createOtpByEmailBucket(emailOrUsername));
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
     * Create login bucket with rate limit (per-IP, high limit for shared NATs)
     */
    private Bucket createLoginBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(loginRequests, Refill.greedy(loginRequests, Duration.ofSeconds(loginSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    /**
     * Create login bucket with rate limit (per-username, brute-force protection)
     */
    private Bucket createLoginByUsernameBucket(String username) {
        Bandwidth limit = Bandwidth.classic(loginByUsernameRequests, Refill.greedy(loginByUsernameRequests, Duration.ofSeconds(loginByUsernameSeconds)));
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
     * Create password reset bucket with rate limit (per-email)
     */
    private Bucket createPasswordResetByEmailBucket(String email) {
        Bandwidth limit = Bandwidth.classic(passwordResetByEmailRequests, Refill.greedy(passwordResetByEmailRequests, Duration.ofSeconds(passwordResetByEmailSeconds)));
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
     * Create OTP bucket with rate limit (per-email/username)
     */
    private Bucket createOtpByEmailBucket(String emailOrUsername) {
        Bandwidth limit = Bandwidth.classic(otpByEmailRequests, Refill.greedy(otpByEmailRequests, Duration.ofSeconds(otpByEmailSeconds)));
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
        long totalEntries = loginBuckets.estimatedSize() +
                           loginByUsernameBuckets.estimatedSize() +
                           passwordResetBuckets.estimatedSize() +
                           passwordResetByEmailBuckets.estimatedSize() +
                           otpBuckets.estimatedSize() +
                           otpByEmailBuckets.estimatedSize() +
                           signupBuckets.estimatedSize() +
                           emailVerificationBuckets.estimatedSize() +
                           emailInviteVerificationBuckets.estimatedSize() +
                           urlCreationBuckets.estimatedSize() +
                           generalApiBuckets.estimatedSize() +
                           redirectBuckets.estimatedSize();

        loginBuckets.cleanUp();
        loginByUsernameBuckets.cleanUp();
        passwordResetBuckets.cleanUp();
        passwordResetByEmailBuckets.cleanUp();
        otpBuckets.cleanUp();
        otpByEmailBuckets.cleanUp();
        signupBuckets.cleanUp();
        emailVerificationBuckets.cleanUp();
        emailInviteVerificationBuckets.cleanUp();
        urlCreationBuckets.cleanUp();
        generalApiBuckets.cleanUp();
        redirectBuckets.cleanUp();

        if (totalEntries > 1000) {
            System.out.printf("RateLimitConfig: Cleaned up stale entries. Remaining: %d%n",
                loginBuckets.estimatedSize() + loginByUsernameBuckets.estimatedSize() +
                passwordResetBuckets.estimatedSize() + passwordResetByEmailBuckets.estimatedSize() +
                otpBuckets.estimatedSize() + otpByEmailBuckets.estimatedSize() +
                signupBuckets.estimatedSize() + urlCreationBuckets.estimatedSize() +
                generalApiBuckets.estimatedSize() + redirectBuckets.estimatedSize());
        }
    }
}
