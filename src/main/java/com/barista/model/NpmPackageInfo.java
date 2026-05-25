package com.barista.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NpmPackageInfo {

    private String name;
    private String version;
    private LocalDateTime installedAt = LocalDateTime.now();

    public NpmPackageInfo() {}

    public NpmPackageInfo(String name, String version, LocalDateTime installedAt) {
        this.name = name;
        this.version = version;
        this.installedAt = installedAt;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final NpmPackageInfo p = new NpmPackageInfo();
        public Builder name(String v)    { p.name = v; return this; }
        public Builder version(String v) { p.version = v; return this; }
        public Builder installedAt(LocalDateTime v) { p.installedAt = v; return this; }
        public NpmPackageInfo build() { return p; }
    }

    public String getId()          { return name + "@" + version; }
    public String getDisplayName() { return name + " " + version; }

    public String getName()    { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public LocalDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDateTime v) { this.installedAt = v; }
}
