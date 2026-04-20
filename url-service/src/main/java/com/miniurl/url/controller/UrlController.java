package com.miniurl.url.controller;

import com.miniurl.common.dto.ApiResponse;
import com.miniurl.common.dto.CreateUrlRequest;
import com.miniurl.common.dto.PagedResponse;
import com.miniurl.common.dto.PageableRequest;
import com.miniurl.common.dto.UrlResponse;
import com.miniurl.url.service.UrlService;
import com.miniurl.url.service.UrlUsageLimitService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UrlResponse>> createUrl(
            @RequestBody CreateUrlRequest request,
            @RequestAttribute("userId") Long userId) {
        UrlResponse response = urlService.createUrl(request, userId);
        return ResponseEntity.ok(ApiResponse.success("URL created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UrlResponse>>> getUserUrls(
            @RequestAttribute("userId") Long userId) {
        List<UrlResponse> urls = urlService.getUserUrls(userId);
        return ResponseEntity.ok(ApiResponse.success("User URLs retrieved successfully", urls));
    }

    @GetMapping("/paged")
    public ResponseEntity<ApiResponse<PagedResponse<UrlResponse>>> getUserUrlsPaged(
            @RequestBody PageableRequest pageableRequest,
            @RequestAttribute("userId") Long userId) {
        PagedResponse<UrlResponse> response = urlService.getUserUrls(userId, pageableRequest);
        return ResponseEntity.ok(ApiResponse.success("User URLs retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UrlResponse>> getUrlById(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId) {
        UrlResponse response = urlService.getUrlById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("URL retrieved successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUrl(
            @PathVariable Long id,
            @RequestAttribute("userId") Long userId) {
        urlService.deleteUrl(id, userId);
        return ResponseEntity.ok(ApiResponse.success("URL deleted successfully", null));
    }

    @GetMapping("/usage-stats")
    public ResponseEntity<ApiResponse<UrlUsageLimitService.UrlUsageStats>> getUsageStats(
            @RequestAttribute("userId") Long userId) {
        UrlUsageLimitService.UrlUsageStats stats = urlService.getUsageStats(userId);
        return ResponseEntity.ok(ApiResponse.success("URL usage stats retrieved successfully", stats));
    }
}
