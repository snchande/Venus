package com.barista.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.barista.model.NuGetPackageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages installed NuGet packages for C# and F# cells.
 *
 * Packages are stored in {@code data/nuget-packages.json}. At execution time,
 * each installed package is prepended as a {@code #r "nuget: ..."} directive
 * so dotnet-script / dotnet fsi can resolve it automatically.
 *
 * NuGet itself handles downloading and caching — Arima just tracks which
 * packages the user wants pre-loaded in every cell.
 */
@Service
public class NuGetService {

    private static final Logger log = LoggerFactory.getLogger(NuGetService.class);
    private static final String PACKAGES_FILE = "nuget-packages.json";

    @Value("${barista.data.dir:data}")
    private String dataDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Return all installed NuGet packages. */
    public List<NuGetPackageInfo> getInstalledPackages() {
        Path file = packagesFile();
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            return objectMapper.readValue(file.toFile(),
                    new TypeReference<List<NuGetPackageInfo>>() {});
        } catch (IOException e) {
            log.error("Failed to read nuget-packages.json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Add a NuGet package to the installed list.
     * If the package ID is already present, the version is updated.
     */
    public NuGetPackageInfo installPackage(String packageId, String version) {
        List<NuGetPackageInfo> packages = getInstalledPackages();

        // Remove existing entry for same package ID (version upgrade)
        packages.removeIf(p -> p.getPackageId().equalsIgnoreCase(packageId));

        NuGetPackageInfo pkg = new NuGetPackageInfo(packageId, version);
        packages.add(pkg);
        save(packages);
        log.info("Installed NuGet package: {} {}", packageId, version);
        return pkg;
    }

    /** Remove a NuGet package from the installed list. */
    public boolean removePackage(String packageId) {
        List<NuGetPackageInfo> packages = getInstalledPackages();
        boolean removed = packages.removeIf(p -> p.getPackageId().equalsIgnoreCase(packageId));
        if (removed) {
            save(packages);
            log.info("Removed NuGet package: {}", packageId);
        }
        return removed;
    }

    /**
     * Build the preamble block of {@code #r "nuget: ..."} directives to prepend
     * to every C# or F# script, so installed packages are always available.
     */
    public String buildNuGetPreamble() {
        List<NuGetPackageInfo> packages = getInstalledPackages();
        if (packages.isEmpty()) return "";
        return packages.stream()
                .map(NuGetPackageInfo::toDirective)
                .collect(Collectors.joining("\n")) + "\n";
    }

    private void save(List<NuGetPackageInfo> packages) {
        try {
            Files.createDirectories(Paths.get(dataDir));
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(packagesFile().toFile(), packages);
        } catch (IOException e) {
            log.error("Failed to save nuget-packages.json: {}", e.getMessage());
        }
    }

    private Path packagesFile() {
        return Paths.get(dataDir, PACKAGES_FILE);
    }
}
