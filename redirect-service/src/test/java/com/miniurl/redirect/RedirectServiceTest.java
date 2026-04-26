package com.miniurl.redirect;

import com.miniurl.common.dto.ClickEvent;
import com.miniurl.redirect.producer.ClickEventProducer;
import com.miniurl.redirect.service.RedirectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedirectService Tests")
class RedirectServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @Mock
    private WebClient webClient;

    @Mock
    private ClickEventProducer clickEventProducer;

    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        redirectService = new RedirectService(redisTemplate, webClient, clickEventProducer);
    }

    @Test
    @DisplayName("resolveUrl returns cached URL on cache hit")
    void resolveUrlCacheHit() {
        when(valueOps.get("url:cache:abc123")).thenReturn(Mono.just("https://example.com"));

        StepVerifier.create(redirectService.resolveUrl("abc123"))
                .expectNext("https://example.com")
                .verifyComplete();
    }

    @Test
    @DisplayName("resolveUrl returns empty when cache miss and URL service unavailable")
    void resolveUrlCacheMissNoFallback() {
        when(valueOps.get("url:cache:abc123")).thenReturn(Mono.empty());

        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/internal/urls/resolve/{code}", "abc123")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("URL service down")));

        StepVerifier.create(redirectService.resolveUrl("abc123"))
                .verifyComplete();
    }

    @Test
    @DisplayName("resolveUrl uses different cache keys for different codes")
    void resolveUrlDifferentCacheKeys() {
        when(valueOps.get("url:cache:code1")).thenReturn(Mono.just("https://example1.com"));
        when(valueOps.get("url:cache:code2")).thenReturn(Mono.just("https://example2.com"));

        StepVerifier.create(redirectService.resolveUrl("code1"))
                .expectNext("https://example1.com")
                .verifyComplete();

        StepVerifier.create(redirectService.resolveUrl("code2"))
                .expectNext("https://example2.com")
                .verifyComplete();

        verify(valueOps, times(1)).get("url:cache:code1");
        verify(valueOps, times(1)).get("url:cache:code2");
    }

    @Test
    @DisplayName("publishClickEvent delegates to ClickEventProducer")
    void publishClickEvent() {
        ClickEvent event = ClickEvent.builder()
                .shortCode("abc123")
                .originalUrl("https://example.com")
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .referer("https://referrer.com")
                .timestamp(LocalDateTime.now())
                .userId(42L)
                .build();

        when(clickEventProducer.sendClickEvent(event)).thenReturn(Mono.empty().then());

        StepVerifier.create(redirectService.publishClickEvent(event))
                .verifyComplete();

        verify(clickEventProducer, times(1)).sendClickEvent(event);
    }

    @Test
    @DisplayName("publishClickEvent propagates producer error")
    void publishClickEventError() {
        ClickEvent event = ClickEvent.builder()
                .shortCode("abc123")
                .originalUrl("https://example.com")
                .build();

        when(clickEventProducer.sendClickEvent(event))
                .thenReturn(Mono.error(new RuntimeException("Kafka unavailable")));

        StepVerifier.create(redirectService.publishClickEvent(event))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("resolveUrl caches URL from fallback service on cache miss")
    void resolveUrlCacheMissWithFallback() {
        when(valueOps.get("url:cache:xyz789")).thenReturn(Mono.empty());
        when(valueOps.set("url:cache:xyz789", "https://fallback.com", java.time.Duration.ofHours(1)))
                .thenReturn(Mono.just(true));

        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/internal/urls/resolve/{code}", "xyz789")).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("https://fallback.com"));

        StepVerifier.create(redirectService.resolveUrl("xyz789"))
                .expectNext("https://fallback.com")
                .verifyComplete();

        verify(valueOps, times(1)).get("url:cache:xyz789");
        verify(valueOps, times(1)).set("url:cache:xyz789", "https://fallback.com", java.time.Duration.ofHours(1));
    }

    @Test
    @DisplayName("resolveUrl calls opsForValue once per resolution")
    void resolveUrlUsesOpsForValue() {
        when(valueOps.get("url:cache:test")).thenReturn(Mono.just("https://test.com"));

        StepVerifier.create(redirectService.resolveUrl("test"))
                .expectNext("https://test.com")
                .verifyComplete();

        verify(redisTemplate, times(1)).opsForValue();
    }
}
