package com.barista.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.barista.model.NpmPackageInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages npm package installation and removal for JavaScript cells.
 *
 * Packages are installed via `npm install --prefix data/npm-modules <name>@<version>`.
 * The resulting node_modules directory is added to NODE_PATH when running JS cells,
 * so users can call require('package-name') directly.
 *
 * Package list is persisted in data/npm-packages.json.
 */
@Service
public class NpmPackageService {

    private static final Logger log = LoggerFactory.getLogger(NpmPackageService.class);
    private static final String NPM_REGISTRY_SEARCH = "https://registry.npmjs.org/-/v1/search";

    // On Windows, npm is npm.cmd — a batch script.
    // ProcessBuilder cannot run .cmd files via "cmd /c npm" reliably because cmd.exe
    // spawns a child process whose I/O isn't fully captured. Use "npm.cmd" directly instead —
    // ProcessBuilder can resolve .cmd files on PATH when given the full name with extension.
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /** Build the npm command: ["npm.cmd", ...] on Windows, ["npm", ...] on Unix. */
    private static List<String> npm(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(IS_WINDOWS ? "npm.cmd" : "npm");
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }

    @Value("${barista.data.dir:data}")
    private String dataDir;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private List<NpmPackageInfo> installedPackages;

    public NpmPackageService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.httpClient = HttpClient.newHttpClient();
    }

    @PostConstruct
    public void init() throws IOException {
        Path npmDir = Paths.get(dataDir, "npm-modules");
        Files.createDirectories(npmDir);
        installedPackages = loadPackageList();
    }

    public List<NpmPackageInfo> getInstalledPackages() {
        return Collections.unmodifiableList(installedPackages);
    }

    /** Path to pass as NODE_PATH when running JS cells */
    public String getNodeModulesPath() {
        return Paths.get(dataDir, "npm-modules", "node_modules").toAbsolutePath().toString();
    }

    /**
     * Install an npm package. Version defaults to "latest" if not specified.
     */
    public NpmPackageInfo installPackage(String name, String version) throws IOException, InterruptedException {
        String resolvedVersion = (version == null || version.isBlank()) ? "latest" : version.trim();
        String spec = name + "@" + resolvedVersion;

        // Check already installed (skip reinstall for same name, but allow version change)
        boolean alreadyInstalled = installedPackages.stream()
                .anyMatch(p -> p.getName().equals(name) && p.getVersion().equals(resolvedVersion));
        if (alreadyInstalled) {
            return installedPackages.stream()
                    .filter(p -> p.getName().equals(name))
                    .findFirst().orElseThrow();
        }

        log.info("Installing npm package: {}", spec);

        Path prefixDir = Paths.get(dataDir, "npm-modules").toAbsolutePath();
        Files.createDirectories(prefixDir);
        ProcessBuilder pb = new ProcessBuilder(npm("install", "--save", spec));
        pb.directory(prefixDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) output.append(line).append("\n");
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("npm install timed out for: " + spec);
        }
        if (process.exitValue() != 0) {
            throw new IOException("npm install failed for " + spec + ":\n" + output.toString().trim());
        }

        // Resolve actual installed version from node_modules/<name>/package.json
        String actualVersion = resolveInstalledVersion(name, resolvedVersion);

        // Remove old entry for same package name (version upgrade)
        installedPackages.removeIf(p -> p.getName().equals(name));

        NpmPackageInfo pkg = new NpmPackageInfo(name, actualVersion, LocalDateTime.now());
        installedPackages.add(pkg);
        savePackageList();
        log.info("Installed npm package: {}@{}", name, actualVersion);
        return pkg;
    }

    /** Remove an installed npm package */
    public boolean removePackage(String name) throws IOException, InterruptedException {
        Optional<NpmPackageInfo> existing = installedPackages.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
        if (existing.isEmpty()) return false;

        Path prefixDir = Paths.get(dataDir, "npm-modules").toAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(npm("uninstall", name));
        pb.directory(prefixDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor(60, TimeUnit.SECONDS);

        installedPackages.removeIf(p -> p.getName().equals(name));
        savePackageList();
        log.info("Removed npm package: {}", name);
        return true;
    }

    /**
     * Search npm registry for packages.
     * Returns list of maps with name, version, description fields.
     */
    public List<Map<String, String>> searchPackages(String query) throws IOException, InterruptedException {
        String url = NPM_REGISTRY_SEARCH + "?text=" + query.replace(" ", "+") + "&size=10";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("npm search failed: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode objects = root.path("objects");
        List<Map<String, String>> results = new ArrayList<>();
        for (JsonNode obj : objects) {
            JsonNode pkg = obj.path("package");
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", pkg.path("name").asText(""));
            item.put("version", pkg.path("version").asText(""));
            item.put("description", pkg.path("description").asText(""));
            results.add(item);
        }
        return results;
    }

    private String resolveInstalledVersion(String name, String requestedVersion) {
        try {
            Path pkgJson = Paths.get(dataDir, "npm-modules", "node_modules", name, "package.json");
            if (Files.exists(pkgJson)) {
                JsonNode node = objectMapper.readTree(pkgJson.toFile());
                String ver = node.path("version").asText("");
                if (!ver.isEmpty()) return ver;
            }
        } catch (Exception ignored) {}
        return requestedVersion.equals("latest") ? "latest" : requestedVersion;
    }

    private List<NpmPackageInfo> loadPackageList() {
        Path path = Paths.get(dataDir, "npm-packages.json");
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<List<NpmPackageInfo>>() {});
        } catch (IOException e) {
            log.warn("Failed to load npm package list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void savePackageList() {
        Path path = Paths.get(dataDir, "npm-packages.json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), installedPackages);
        } catch (IOException e) {
            log.error("Failed to save npm package list: {}", e.getMessage());
        }
    }
}
