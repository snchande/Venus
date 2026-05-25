package com.barista.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    private String id;
    private String name;
    private String email;
    private String avatarUrl;
    private AuthProvider authProvider;
    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;

    public UserProfile() {}

    // ── Getters / Setters ────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    /** First name extracted from full name (before first space). */
    public String getFirstName() {
        if (name == null || name.isBlank()) return "there";
        int idx = name.indexOf(' ');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    // ── Builder ──────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final UserProfile p = new UserProfile();
        public Builder id(String v)              { p.id = v; return this; }
        public Builder name(String v)            { p.name = v; return this; }
        public Builder email(String v)           { p.email = v; return this; }
        public Builder avatarUrl(String v)       { p.avatarUrl = v; return this; }
        public Builder authProvider(AuthProvider v) { p.authProvider = v; return this; }
        public Builder createdAt(LocalDateTime v)   { p.createdAt = v; return this; }
        public Builder lastSeenAt(LocalDateTime v)  { p.lastSeenAt = v; return this; }
        public UserProfile build() { return p; }
    }
}
