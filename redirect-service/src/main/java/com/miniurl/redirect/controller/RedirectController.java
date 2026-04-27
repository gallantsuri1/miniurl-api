package com.miniurl.redirect.controller;

import com.miniurl.common.dto.ClickEvent;
import com.miniurl.redirect.service.RedirectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime;

@Slf4j
@RestController
public class RedirectController {

    private final RedirectService redirectService;

    public RedirectController(RedirectService redirectService) {
        this.redirectService = redirectService;
    }

    @GetMapping("/r/{code}")
    public Mono<ResponseEntity<Object>> redirect(@PathVariable String code,
                                                  @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                                  @RequestHeader(value = "Referer", required = false) String referer,
                                                  @RequestHeader(value = "X-Forwarded-For", required = false) String xForwardedFor,
                                                  ServerWebExchange exchange) {
        
        Long userId = exchange.getAttribute("userId");
        
        return redirectService.resolveUrl(code)
            .flatMap(originalUrl -> {
                // Async click event publishing
                ClickEvent event = ClickEvent.builder()
                    .shortCode(code)
                    .originalUrl(originalUrl)
                    .ipAddress(xForwardedFor != null ? xForwardedFor.split(",")[0] : "unknown")
                    .userAgent(userAgent)
                    .referer(referer)
                    .timestamp(LocalDateTime.now())
                    .userId(userId)
                    .build();
                
                return redirectService.publishClickEvent(event)
                    .thenReturn(ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(originalUrl))
                        .build());
            })
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found")));
    }
}
