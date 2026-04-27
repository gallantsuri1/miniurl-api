package com.miniurl.url.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.miniurl.url.service.UrlService;

import java.util.Optional;

@RestController
@RequestMapping("/internal/urls")
@RequiredArgsConstructor
public class InternalUrlController {

    private final UrlService urlService;

    @GetMapping("/resolve/{code}")
    public ResponseEntity<String> resolve(@PathVariable String code) {
        return urlService.resolveShortCode(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
