package com.venus.controller;

import com.venus.model.NpmPackageInfo;
import com.venus.service.NpmPackageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for npm package management (JavaScript cells).
 *
 * GET    /api/npm/packages             — list installed packages
 * POST   /api/npm/packages/install     — install a package { name, version? }
 * DELETE /api/npm/packages/{name}      — remove a package
 * GET    /api/npm/packages/search?q=   — search npm registry
 * GET    /api/npm/status               — check if Node.js is available
 */
@RestController
@RequestMapping("/api/npm")
public class NpmPackageController {

    private final NpmPackageService npmPackageService;

    public NpmPackageController(NpmPackageService npmPackageService) {
        this.npmPackageService = npmPackageService;
    }

    @GetMapping("/packages")
    public ResponseEntity<List<NpmPackageInfo>> listPackages() {
        return ResponseEntity.ok(npmPackageService.getInstalledPackages());
    }

    @PostMapping("/packages/install")
    public ResponseEntity<?> installPackage(@RequestBody Map<String, String> body) {
        String name    = body.get("name");
        String version = body.getOrDefault("version", "latest");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        try {
            NpmPackageInfo pkg = npmPackageService.installPackage(name.trim(), version.trim());
            return ResponseEntity.ok(pkg);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/packages/{name}")
    public ResponseEntity<?> removePackage(@PathVariable String name) {
        try {
            boolean removed = npmPackageService.removePackage(name);
            return ResponseEntity.ok(Map.of("removed", removed));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/packages/search")
    public ResponseEntity<?> searchPackages(@RequestParam String q) {
        try {
            List<Map<String, String>> results = npmPackageService.searchPackages(q);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> nodeStatus() {
        try {
            Process p = new ProcessBuilder("node", "--version").start();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            String version = new String(p.getInputStream().readAllBytes()).trim();
            return ResponseEntity.ok(Map.of("available", p.exitValue() == 0, "version", version));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("available", false, "version", ""));
        }
    }
}
