package com.venus.controller;

import com.venus.model.VenusSettings;
import com.venus.service.CopilotCliService;
import com.venus.service.CppExecutionService;
import com.venus.service.DotNetExecutionService;
import com.venus.service.GeminiService;
import com.venus.service.SettingsService;
import com.venus.service.TypeScriptExecutionService;
import com.venus.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * REST endpoints for application settings.
 *
 * GET  /api/settings   - Get current settings (API key is masked)
 * PUT  /api/settings   - Update settings
 * GET  /api/settings/status - Check server status
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final UserService userService;
    private final DotNetExecutionService dotNetExecutionService;
    private final CopilotCliService copilotCliService;
    private final GeminiService geminiService;
    private final CppExecutionService cppExecutionService;
    private final TypeScriptExecutionService typeScriptExecutionService;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${venus.auth.mode:local}")
    private String currentAuthMode;

    @Value("${venus.version:1.0.0-dev}")
    private String appVersion;

    @Value("${venus.build.timestamp:dev-build}")
    private String buildTimestamp;

    public SettingsController(SettingsService settingsService,
                              UserService userService,
                              DotNetExecutionService dotNetExecutionService,
                              CopilotCliService copilotCliService,
                              GeminiService geminiService,
                              CppExecutionService cppExecutionService,
                              TypeScriptExecutionService typeScriptExecutionService,
                              ConfigurableApplicationContext applicationContext) {
        this.settingsService = settingsService;
        this.userService = userService;
        this.dotNetExecutionService = dotNetExecutionService;
        this.copilotCliService = copilotCliService;
        this.geminiService = geminiService;
        this.cppExecutionService = cppExecutionService;
        this.typeScriptExecutionService = typeScriptExecutionService;
        this.applicationContext = applicationContext;
    }

    @GetMapping
    public ResponseEntity<VenusSettings> getSettings() {
        VenusSettings settings = settingsService.getSettings();
        // Mask the API key in the response
        VenusSettings masked = copyWithMaskedKey(settings);
        return ResponseEntity.ok(masked);
    }

    @PutMapping
    public ResponseEntity<VenusSettings> updateSettings(@RequestBody VenusSettings newSettings) {
        VenusSettings saved = settingsService.updateSettings(newSettings);
        return ResponseEntity.ok(copyWithMaskedKey(saved));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        VenusSettings settings = settingsService.getSettings();

        // Check if the claude CLI is available on this machine
        boolean cliAvailable = isClaudeCliAvailable();

        String provider = settings.getAiProvider() != null ? settings.getAiProvider() : "claude_cli";
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("version", appVersion);
        status.put("buildTimestamp", buildTimestamp);
        status.put("javaVersion", System.getProperty("java.version"));
        status.put("javaHome", System.getProperty("java.home"));
        status.put("aiProvider", provider);
        status.put("claudeCliAvailable", cliAvailable);
        status.put("claudeModel", settings.getClaudeModel());
        status.put("githubCopilotAvailable", copilotCliService.isAvailable());
        status.put("githubCopilotStatus", copilotCliService.getStatusDetail());
        status.put("geminiCliAvailable", geminiService.isAvailable());
        status.put("geminiModel", settings.getGeminiModel());
        status.put("geminiCliStatus", geminiService.getStatusDetail());
        status.put("theme", settings.getTheme());
        status.put("dotnetAvailable", dotNetExecutionService.isDotNetAvailable());
        status.put("dotnetScriptAvailable", dotNetExecutionService.isDotNetScriptAvailable());
        status.put("cppAvailable", cppExecutionService.isAvailable());
        status.put("cppCompilerDetail", cppExecutionService.getCompilerDetail());
        status.put("typescriptAvailable", typeScriptExecutionService.isAvailable());
        status.put("typescriptDetail", typeScriptExecutionService.getStatusDetail());
        status.put("tscAvailable", typeScriptExecutionService.isTscAvailable());
        return ResponseEntity.ok(status);
    }

    /** Quick check: is the claude CLI installed and reachable? */
    private boolean isClaudeCliAvailable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String home = System.getProperty("user.home", "");
        for (String path : List.of(
                home + "/.local/bin/claude",
                home + "/AppData/Local/Programs/claude/claude.exe",
                home + "/AppData/Roaming/npm/claude.cmd",
                "/usr/local/bin/claude", "/usr/bin/claude", "/opt/homebrew/bin/claude")) {
            if (new java.io.File(path).exists()) return true;
        }
        try {
            List<String> cmd = isWindows ? List.of("cmd", "/c", "where", "claude") : List.of("which", "claude");
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return p.waitFor() == 0 && !out.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Change the auth mode (local / oauth).
     * Writes venus.auth.mode to application.properties in the working directory,
     * which Spring Boot loads with higher priority than the classpath copy.
     * A server restart is required for the change to take effect.
     */
    @PutMapping("/auth-mode")
    public ResponseEntity<Map<String, Object>> setAuthMode(@RequestBody Map<String, String> body) {
        String mode = body.get("mode");
        if (!"local".equals(mode) && !"oauth".equals(mode)) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode must be 'local' or 'oauth'"));
        }
        try {
            writeAuthModeToProperties(mode);
            return ResponseEntity.ok(Map.of(
                "saved", true,
                "currentMode", currentAuthMode,
                "newMode", mode,
                "restartRequired", !mode.equals(currentAuthMode)
            ));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Writes or updates the venus.auth.mode property in ./application.properties
     * (working-directory copy that Spring Boot loads at higher priority than classpath).
     */
    private void writeAuthModeToProperties(String mode) throws IOException {
        Path propsFile = Paths.get("application.properties");
        List<String> lines;
        if (Files.exists(propsFile)) {
            lines = new ArrayList<>(Files.readAllLines(propsFile, StandardCharsets.UTF_8));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("venus.auth.mode=") || lines.get(i).startsWith("#venus.auth.mode")) {
                    lines.set(i, "venus.auth.mode=" + mode);
                    found = true;
                    break;
                }
            }
            if (!found) lines.add("venus.auth.mode=" + mode);
        } else {
            lines = List.of("# Venus auth mode override (auto-generated)", "venus.auth.mode=" + mode);
        }
        Files.write(propsFile, lines, StandardCharsets.UTF_8);
    }

    /**
     * Gracefully shut down the Venus Notebooks server.
     * Responds with 200 before closing the Spring context so the browser
     * receives the acknowledgement before the connection drops.
     */
    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> shutdown() {
        // Schedule shutdown on a new thread so this response is sent first
        Thread shutdownThread = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            applicationContext.close();
        }, "venus-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
        return ResponseEntity.ok(Map.of("message", "Venus Notebooks is shutting down…"));
    }

    /**
     * Reset all settings to factory defaults, preserving the API key.
     */
    @PostMapping("/reset")
    public ResponseEntity<VenusSettings> resetToDefaults() {
        VenusSettings cur      = settingsService.getSettings();
        VenusSettings defaults = new VenusSettings();
        defaults.setAnthropicApiKey(settingsService.getApiKey());      // preserve keys
        defaults.setGithubToken(cur.getGithubToken());
        VenusSettings saved = settingsService.updateSettings(defaults);
        return ResponseEntity.ok(copyWithMaskedKey(saved));
    }

    /** Return a copy of settings with sensitive keys partially masked */
    private VenusSettings copyWithMaskedKey(VenusSettings s) {
        VenusSettings c = new VenusSettings();
        // AI provider
        c.setAiProvider(s.getAiProvider());
        // Claude AI
        c.setAnthropicApiKey(maskApiKey(s.getAnthropicApiKey()));
        c.setClaudeModel(s.getClaudeModel());
        c.setClaudeMaxTokens(s.getClaudeMaxTokens());
        // GitHub Copilot
        c.setGithubToken(maskApiKey(s.getGithubToken()));
        c.setGithubCopilotModel(s.getGithubCopilotModel());
        // Gemini
        c.setGeminiModel(s.getGeminiModel());
        // Editor
        c.setTheme(s.getTheme());
        c.setEditorFontSize(s.getEditorFontSize());
        c.setShowLineNumbers(s.isShowLineNumbers());
        c.setFocusExecutingCell(s.isFocusExecutingCell());
        c.setWrapLongLines(s.isWrapLongLines());
        c.setDefaultCellMode(s.getDefaultCellMode());
        // Notebook behaviour
        c.setAutoSaveIntervalSecs(s.getAutoSaveIntervalSecs());
        c.setAutoClearOutputOnRun(s.isAutoClearOutputOnRun());
        c.setConfirmBeforeDelete(s.isConfirmBeforeDelete());
        c.setConfirmBeforeRestart(s.isConfirmBeforeRestart());
        // Execution
        c.setMaxExecutionTimeMs(s.getMaxExecutionTimeMs());
        c.setMaxOutputLines(s.getMaxOutputLines());
        c.setEnableInlineCharts(s.isEnableInlineCharts());
        // Console
        c.setConsoleFontSize(s.getConsoleFontSize());
        c.setConsoleHistorySize(s.getConsoleHistorySize());
        c.setEnableAutoComplete(s.isEnableAutoComplete());
        // Storage
        c.setNotebooksDir(s.getNotebooksDir());
        c.setServerPort(s.getServerPort());
        return c;
    }

    private String maskApiKey(String key) {
        if (key == null || key.length() < 8) return key;
        return key.substring(0, 7) + "..." + key.substring(key.length() - 4);
    }
}
