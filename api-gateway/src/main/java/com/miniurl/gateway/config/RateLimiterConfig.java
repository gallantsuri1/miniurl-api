package com.miniurl.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            return exchange.getPrincipal()
                .map(principal -> {
                    if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                        return jwt.getSubject();
                    }
                    return principal.getName();
                })
                .defaultIfEmpty("anonymous");
        };
    }
}
