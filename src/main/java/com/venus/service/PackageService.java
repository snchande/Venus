package com.venus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.venus.model.PackageInfo;
import com.venus.shell.JShellManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages Maven package installation and removal.
 *
 * Downloads JARs from Maven Central and adds them to active JShell sessions.
 * Package list is persisted in data/packages.json.
 *
 * Download URL format:
 *   https://repo1.maven.org/maven2/{group-path}/{artifactId}/{version}/{artifactId}-{version}.jar
 *
 * Maven Central search API:
 *   https://search.maven.org/solrsearch/select?q=...
 */
@Service
public class PackageService {

    private static final Logger log = LoggerFactory.getLogger(PackageService.class);

    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";
    private static final String MAVEN_SEARCH_API = "https://search.maven.org/solrsearch/select";

    @Value("${venus.data.dir:data}")
    private String dataDir;

    private final JShellManager jShellManager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private List<PackageInfo> installedPackages;

    public PackageService(JShellManager jShellManager) {
        this.jShellManager = jShellManager;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClient.newHttpClient();
    }

    @PostConstruct
    public void init() throws IOException {
        Path packagesDir = Paths.get(dataDir, "packages");
        Files.createDirectories(packagesDir);
        installedPackages = loadPackageList();
    }

    public List<PackageInfo> getInstalledPackages() {
        return Collections.unmodifiableList(installedPackages);
    }

    /**
     * Install a Maven package by coordinate string (groupId:artifactId:version).
     * Downloads the JAR to data/packages/ and adds it to all active JShell sessions.
     */
    public PackageInfo installPackage(String coordinate) throws IOException, InterruptedException {
        String[] parts = coordinate.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Invalid coordinate format. Expected groupId:artifactId:version, got: " + coordinate);
        }

        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts[2].trim();

        // Check if already installed
        boolean alreadyInstalled = installedPackages.stream()
                .anyMatch(p -> p.getGroupId().equals(groupId)
                        && p.getArtifactId().equals(artifactId)
                        && p.getVersion().equals(version));

        if (alreadyInstalled) {
            return installedPackages.stream()
                    .filter(p -> p.getGroupId().equals(groupId)
                            && p.getArtifactId().equals(artifactId))
                    .findFirst()
                    .orElseThrow();
        }

        String jarPath = downloadJar(groupId, artifactId, version);
        PackageInfo pkg = new PackageInfo(groupId, artifactId, version, jarPath, LocalDateTime.now());

        // Add to all active JShell sessions
        for (String sessionId : jShellManager.getSessionIds()) {
            jShellManager.addJarToSession(sessionId, jarPath);
        }

        installedPackages.add(pkg);
        savePackageList();
        log.info("Installed package: {}", coordinate);
        return pkg;
    }

    /**
     * Remove an installed package by coordinate.
     * Note: Cannot unload from running JShell sessions; requires session restart.
     */
    public boolean removePackage(String coordinate) throws IOException {
        Optional<PackageInfo> pkg = installedPackages.stream()
                .filter(p -> p.getCoordinate().equals(coordinate))
                .findFirst();

        if (pkg.isEmpty()) return false;

        // Delete the JAR file
        Path jarPath = Paths.get(pkg.get().getJarPath());
        Files.deleteIfExists(jarPath);

        installedPackages.removeIf(p -> p.getCoordinate().equals(coordinate));
        savePackageList();
        log.info("Removed package: {}", coordinate);
        return true;
    }

    /**
     * Search Maven Central for packages matching the query.
     * Returns raw JSON response from the search API.
     */
    public String searchPackages(String query) throws IOException, InterruptedException {
        String url = MAVEN_SEARCH_API + "?q=" + query.replace(" ", "+")
                + "&rows=20&wt=json";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Maven search failed with status: " + response.statusCode());
        }
        return response.body();
    }

    /**
     * Apply all installed packages to a new JShell session.
     * Called when a new session is created for a notebook.
     */
    public void applyPackagesToSession(String sessionId) {
        for (PackageInfo pkg : installedPackages) {
            if (Files.exists(Paths.get(pkg.getJarPath()))) {
                jShellManager.addJarToSession(sessionId, pkg.getJarPath());
            }
        }
    }

    private String downloadJar(String groupId, String artifactId, String version)
            throws IOException, InterruptedException {

        // Maven Central URL format
        String groupPath = groupId.replace('.', '/');
        String jarFilename = artifactId + "-" + version + ".jar";
        String url = String.format("%s/%s/%s/%s/%s",
                MAVEN_CENTRAL_BASE, groupPath, artifactId, version, jarFilename);

        Path destDir = Paths.get(dataDir, "packages");
        Path destFile = destDir.resolve(groupId + "_" + artifactId + "_" + version + ".jar");

        // Skip download if already on disk
        if (Files.exists(destFile)) {
            log.info("JAR already downloaded: {}", destFile);
            return destFile.toString();
        }

        log.info("Downloading package from: {}", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException(String.format(
                "Failed to download %s:%s:%s - HTTP %d. Check that the package exists on Maven Central.",
                groupId, artifactId, version, response.statusCode()));
        }

        Files.copy(response.body(), destFile, StandardCopyOption.REPLACE_EXISTING);
        log.info("Downloaded JAR: {} ({} bytes)", destFile, Files.size(destFile));
        return destFile.toString();
    }

    private List<PackageInfo> loadPackageList() {
        Path path = Paths.get(dataDir, "packages.json");
        if (!Files.exists(path)) return new ArrayList<>();

        try {
            return objectMapper.readValue(path.toFile(),
                    new TypeReference<List<PackageInfo>>() {});
        } catch (IOException e) {
            log.warn("Failed to load package list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void savePackageList() {
        Path path = Paths.get(dataDir, "packages.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(path.toFile(), installedPackages);
        } catch (IOException e) {
            log.error("Failed to save package list: {}", e.getMessage());
        }
    }
}
