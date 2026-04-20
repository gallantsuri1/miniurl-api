package com.miniurl.feature.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a feature flag for a specific role.
 * Contains role-based enabled status linked to a master feature.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "feature_id", nullable = false, foreignKey = @ForeignKey(name = "fk_feature_flags_feature"))
    private Feature feature;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public FeatureFlag() {
    }

    public FeatureFlag(Feature feature, Long roleId, boolean enabled) {
        this.feature = feature;
        this.roleId = roleId;
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

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

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
        return "FeatureFlag{" +
                "id=" + id +
                ", feature=" + (feature != null ? feature.getFeatureKey() : "null") +
                ", roleId=" + roleId +
                ", enabled=" + enabled +
                '}';
    }
}
