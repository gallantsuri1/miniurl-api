package com.miniurl.common.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
public class ClickEvent {
    private String shortCode;
    private String originalUrl;
    private String ipAddress;
    private String userAgent;
    private String referer;
    private LocalDateTime timestamp;
    private Long userId; // Optional, if authenticated
}
