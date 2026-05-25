package com.barista.controller;

import com.barista.model.NuGetPackageInfo;
import com.barista.service.NuGetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for NuGet package management (C# and F# cells).
 *
 * GET    /api/nuget             - List installed NuGet packages
 * POST   /api/nuget/install     - Install a package by PackageId and Version
 * DELETE /api/nuget/{packageId} - Remove a package
 */
@RestController
@RequestMapping("/api/nuget")
public class NuGetController {

    private final NuGetService nuGetService;

    public NuGetController(NuGetService nuGetService) {
        this.nuGetService = nuGetService;
    }

    @GetMapping
    public ResponseEntity<List<NuGetPackageInfo>> listPackages() {
        return ResponseEntity.ok(nuGetService.getInstalledPackages());
    }

    @PostMapping("/install")
    public ResponseEntity<?> installPackage(@RequestBody Map<String, String> body) {
        String packageId = body.get("packageId");
        String version   = body.get("version");

        if (packageId == null || packageId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'packageId' field"));
        }
        if (version == null || version.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'version' field"));
        }

        NuGetPackageInfo pkg = nuGetService.installPackage(packageId.trim(), version.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(pkg);
    }

    @DeleteMapping("/{packageId}")
    public ResponseEntity<?> removePackage(@PathVariable String packageId) {
        boolean removed = nuGetService.removePackage(packageId);
        if (removed) {
            return ResponseEntity.ok(Map.of(
                "removed", true,
                "message", "Package removed. Changes apply to new cell executions.",
                "packageId", packageId
            ));
        }
        return ResponseEntity.notFound().build();
    }
}
