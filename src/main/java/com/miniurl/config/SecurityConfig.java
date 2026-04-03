package com.miniurl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final CustomAuthenticationFailureHandler authenticationFailureHandler;
    private final LockoutPreventionFilter lockoutPreventionFilter;

    public SecurityConfig(UserDetailsService userDetailsService,
                         JwtAuthenticationFilter jwtAuthenticationFilter,
                         RateLimitingFilter rateLimitingFilter,
                         CustomAuthenticationFailureHandler authenticationFailureHandler,
                         LockoutPreventionFilter lockoutPreventionFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.lockoutPreventionFilter = lockoutPreventionFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Enable CSRF for API endpoints that use session-based authentication
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                // Ignore CSRF for API endpoints that use JWT
                .ignoringRequestMatchers(
                    "/api/**",
                    "/auth/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/.well-known/**"  // Browser-specific files (Chrome DevTools)
                )
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - Swagger/OpenAPI (disabled in production via OpenApiConfig)
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                // Public endpoints - Health and Actuator
                .requestMatchers(
                    "/api/health",
                    "/actuator/**"
                ).permitAll()
                // Public endpoints - Authentication (no authentication required)
                .requestMatchers(
                    "/auth/signup",
                    "/auth/login",
                    "/auth/verify-otp",
                    "/auth/resend-otp",
                    "/auth/forgot-password",
                    "/auth/reset-password",
                    "/auth/activate-email",
                    "/api/auth/login",
                    "/api/auth/signup",
                    "/api/auth/verify-email",
                    "/api/auth/verify-email-invite",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password"
                ).permitAll()
                // Public endpoint - URL Redirect
                .requestMatchers("/r/**").permitAll()
                // Public endpoint - Global feature flags (no auth needed)
                .requestMatchers("/api/features/global").permitAll()
                // Self-invite endpoint (public, but checks USER_SIGNUP global feature)
                .requestMatchers("/api/self-invite/**").permitAll()
                // Admin endpoints
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // User endpoints
                .requestMatchers("/api/profile/**", "/api/settings/**").authenticated()
                // Feature flags by role (authenticated users)
                .requestMatchers("/api/features").authenticated()
                // General API endpoints
                .requestMatchers("/api/**").authenticated()
                // Default - require authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // For API requests, return JSON
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
                })
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(lockoutPreventionFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Get CORS origins from environment variable or system property
        String corsOrigins = System.getenv("APP_CORS_ALLOWED_ORIGINS");
        if (corsOrigins == null || corsOrigins.isEmpty()) {
            corsOrigins = System.getProperty("APP_CORS_ALLOWED_ORIGINS");
        }

        // Determine if running in production profile
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile == null) {
            activeProfile = System.getProperty("SPRING_PROFILES_ACTIVE", "dev");
        }
        boolean isProduction = "prod".equals(activeProfile);
        
        if (corsOrigins != null && !corsOrigins.isEmpty()) {
            // Parse comma-separated origins from environment variable
            configuration.setAllowedOrigins(
                Arrays.stream(corsOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .toList()
            );
        } else if (isProduction) {
            // CRITICAL: Fail fast in production if CORS is not configured
            throw new IllegalStateException(
                "SECURITY CRITICAL: APP_CORS_ALLOWED_ORIGINS environment variable must be set in production. " +
                "Example: APP_CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com"
            );
        } else {
            // Development defaults - only allowed in non-production profiles
            configuration.setAllowedOrigins(List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://localhost:3000",
                "http://127.0.0.1:3000"
            ));
        }
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("X-Authorization", "X-Token-Renewed"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);  // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
