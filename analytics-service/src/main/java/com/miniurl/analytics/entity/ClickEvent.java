package com.miniurl.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "click_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, index = true)
    private String shortCode;

    @Column(nullable = false)
    private String originalUrl;

    private String ipAddress;
    private String userAgent;
    private String referer;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private Long userId;
}
