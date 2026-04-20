package com.miniurl.feature.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a global feature flag.
 * Contains enabled status for features not tied to specific roles (e.g., USER_SIGNUP).
 */
@Entity
@Table(name = "global_flags")
public class GlobalFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "feature_id", nullable = false, foreignKey = @ForeignKey(name = "fk_global_flags_feature"))
    private Feature feature;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GlobalFlag() {
    }

    public GlobalFlag(Feature feature, boolean enabled) {
        this.feature = feature;
        this.enabled = enabled;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Feature getFeature() { return feature; }
    public void setFeature(Feature feature) { this.feature = feature; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public void toggle() {
        this.enabled = !this.enabled;
    }

    @Override
    public String toString() {
        return "GlobalFlag{" +
                "id=" + id +
                ", feature=" + (feature != null ? feature.getFeatureKey() : "null") +
                ", enabled=" + enabled +
                '}';
    }
}
