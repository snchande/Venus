package com.venus.model;

import java.time.LocalDateTime;

public class PackageInfo {

    private String groupId;
    private String artifactId;
    private String version;
    private String jarPath;
    private LocalDateTime installedAt = LocalDateTime.now();

    public PackageInfo() {}

    public PackageInfo(String groupId, String artifactId, String version,
                       String jarPath, LocalDateTime installedAt) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.jarPath = jarPath;
        this.installedAt = installedAt;
    }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final PackageInfo p = new PackageInfo();
        public Builder groupId(String v)       { p.groupId = v; return this; }
        public Builder artifactId(String v)    { p.artifactId = v; return this; }
        public Builder version(String v)       { p.version = v; return this; }
        public Builder jarPath(String v)       { p.jarPath = v; return this; }
        public Builder installedAt(LocalDateTime v) { p.installedAt = v; return this; }
        public PackageInfo build()             { return p; }
    }

    public String getCoordinate() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public String getId() { return getCoordinate(); }

    public String getDisplayName() { return artifactId + " " + version; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getJarPath() { return jarPath; }
    public void setJarPath(String jarPath) { this.jarPath = jarPath; }

    public LocalDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDateTime installedAt) { this.installedAt = installedAt; }
}
