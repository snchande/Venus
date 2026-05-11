package com.venus.controller;

import com.venus.model.PackageInfo;
import com.venus.service.PackageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for Maven package management.
 *
 * GET    /api/packages             - List installed packages
 * POST   /api/packages/install     - Install a package by coordinate
 * DELETE /api/packages/{coordinate} - Remove a package
 * GET    /api/packages/search?q=.. - Search Maven Central
 */
@RestController
@RequestMapping("/api/packages")
public class PackageController {

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    public ResponseEntity<List<PackageInfo>> listPackages() {
        return ResponseEntity.ok(packageService.getInstalledPackages());
    }

    @PostMapping("/install")
    public ResponseEntity<?> installPackage(@RequestBody Map<String, String> body) {
        String coordinate = body.get("coordinate");
        if (coordinate == null || coordinate.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'coordinate' field (groupId:artifactId:version)"));
        }

        try {
            PackageInfo pkg = packageService.installPackage(coordinate);
            return ResponseEntity.status(HttpStatus.CREATED).body(pkg);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to install package: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{groupId}/{artifactId}/{version}")
    public ResponseEntity<Map<String, Object>> removePackage(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @PathVariable String version) {

        String coordinate = groupId + ":" + artifactId + ":" + version;
        try {
            boolean removed = packageService.removePackage(coordinate);
            if (removed) {
                return ResponseEntity.ok(Map.of(
                    "removed", true,
                    "message", "Package removed. Restart JShell sessions to apply changes.",
                    "coordinate", coordinate
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchPackages(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query parameter 'q' is required"));
        }
        try {
            String results = packageService.searchPackages(q);
            // Return raw JSON from Maven Central search API
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Search failed: " + e.getMessage()));
        }
    }
}
