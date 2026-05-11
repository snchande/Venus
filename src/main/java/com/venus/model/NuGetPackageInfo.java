package com.venus.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A NuGet package installed in Venus Notebooks.
 * Stored in data/nuget-packages.json.
 *
 * Each installed package is injected as a {@code #r "nuget: PackageId, Version"}
 * directive at the top of every C# (.csx) and F# (.fsx) script cell at execution time.
 */
public class NuGetPackageInfo {

    private String packageId;
    private String version;
    private String installedAt;

    public NuGetPackageInfo() {}

    public NuGetPackageInfo(String packageId, String version) {
        this.packageId   = packageId;
        this.version     = version;
        this.installedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /** The NuGet #r directive line for this package. */
    public String toDirective() {
        return "#r \"nuget: " + packageId + ", " + version + "\"";
    }

    /** Human-readable display name. */
    public String getDisplayName() {
        return packageId + " " + version;
    }

    public String getPackageId()            { return packageId; }
    public void   setPackageId(String v)    { this.packageId = v; }
    public String getVersion()              { return version; }
    public void   setVersion(String v)      { this.version = v; }
    public String getInstalledAt()          { return installedAt; }
    public void   setInstalledAt(String v)  { this.installedAt = v; }
}
