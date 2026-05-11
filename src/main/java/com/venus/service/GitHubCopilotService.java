package com.venus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * GitHub Copilot AI integration via the GitHub Models REST API.
 *
 * Authentication is resolved automatically from the locally-installed
 * {@code gh} CLI (GitHub CLI).  No manual token entry is required — the
 * service runs {@code gh auth token} at call time to obtain the OAuth token
 * that {@code gh auth login} already stored on this machine.
 *
 * API endpoint: https://models.inference.ai.azure.com/chat/completions
 * (OpenAI-compatible chat-completions format)
 *
 * Prerequisites:
 *   1. Install the GitHub CLI: https://cli.github.com
 *   2. Authenticate:  gh auth login
 *   3. (Optional) Install the Copilot extension: gh extension install github/gh-copilot
 *
 * Works with any GitHub account that has GitHub Copilot access.
 */
@Service
public class GitHubCopilotService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCopilotService.class);

    private static final String GITHUB_MODELS_URL =
            "https://models.inference.ai.azure.com/chat/completions";

    private final SettingsService settingsService;
    private final ObjectMapper    objectMapper;
    private final HttpClient      httpClient;

    public GitHubCopilotService(SettingsService settingsService) {
        this.settingsService = settingsService;
        this.objectMapper    = new ObjectMapper();
        this.httpClient      = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // ── Public API ──────────────────────────────────────────────────────

    /** Single-turn chat. */
    public String chat(String userMessage, String systemPrompt)
            throws IOException, InterruptedException {
        return chat(List.of(Map.of("role", "user", "content", userMessage)), systemPrompt);
    }

    /** Multi-turn chat using a conversation history. */
    public String chat(List<Map<String, String>> messages, String systemPrompt)
            throws IOException, InterruptedException {

        String token = resolveToken();

        String model = settingsService.getSettings().getGithubCopilotModel();
        if (model == null || model.isBlank()) model = "gpt-4o";

        // Build messages array: system message first (if present), then history
        List<Map<String, String>> apiMessages = new ArrayList<>();
        String sys = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt : getDefaultSystemPrompt();
        apiMessages.add(Map.of("role", "system", "content", sys));
        apiMessages.addAll(messages);

        // Serialize request body
        Map<String, Object> requestBody = Map.of(
            "model",       model,
            "messages",    apiMessages,
            "max_tokens",  settingsService.getSettings().getClaudeMaxTokens(),
            "temperature", 0.7
        );
        String requestJson = objectMapper.writeValueAsString(requestBody);

        log.info("GitHub Copilot chat: model={}, messages={}", model, apiMessages.size());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_MODELS_URL))
                .header("Content-Type",  "application/json")
                .header("Accept",        "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int statusCode = response.statusCode();
        String body    = response.body();

        if (statusCode == 401 || statusCode == 403) {
            throw new IllegalStateException(
                "GitHub token rejected (HTTP " + statusCode + ").\n\n" +
                "Make sure you are signed in to the GitHub CLI:\n" +
                "  gh auth login\n\n" +
                "Your account must have GitHub Copilot access for the GitHub Models API.");
        }
        if (statusCode != 200) {
            log.warn("GitHub Models API error {}: {}", statusCode, body);
            throw new IOException("GitHub Models API error (HTTP " + statusCode + "): " + body);
        }

        // Parse the OpenAI-compatible response
        JsonNode root    = objectMapper.readTree(body);
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            throw new IOException("GitHub Models API returned no choices. Response: " + body);
        }
        return choices.get(0).path("message").path("content").asText("").trim();
    }

    /** Generate a Venus notebook from a natural language prompt. */
    public String generateNotebook(String prompt) throws IOException, InterruptedException {
        String systemPrompt = """
            You are Venus Notebooks AI assistant. Generate a Venus notebook in JSON format.

            A Venus notebook is a JSON object with this structure:
            {
              "id": "generate-a-uuid",
              "name": "Notebook Title",
              "description": "Brief description",
              "cells": [
                {
                  "id": "cell-1",
                  "type": "MARKDOWN",
                  "source": "# Title\\n\\nMarkdown content here",
                  "output": "",
                  "executed": false
                },
                {
                  "id": "cell-2",
                  "type": "CODE",
                  "source": "// Java code here\\nSystem.out.println(\\"Hello\\");",
                  "output": "",
                  "executed": false
                }
              ],
              "metadata": {}
            }

            Rules:
            - Use Java 21 syntax
            - Include markdown cells for explanations
            - Code cells should be self-contained and runnable
            - Respond ONLY with valid JSON, no other text
            - Generate a proper UUID for the id field
            - Generate unique UUIDs for each cell id
            """;
        return chat("Create a Venus notebook for: " + prompt, systemPrompt);
    }

    /** Explain code in plain English. */
    public String explainCode(String code) throws IOException, InterruptedException {
        return chat("Explain this code clearly and concisely:\n\n```\n" + code + "\n```", null);
    }

    /** Suggest a fix for an error. */
    public String fixError(String code, String error) throws IOException, InterruptedException {
        String prompt = String.format("""
            This code has an error. Provide a corrected version and explain what was wrong.

            Code:
            ```
            %s
            ```

            Error:
            ```
            %s
            ```
            """, code, error);
        return chat(prompt, null);
    }

    /**
     * Available if a manual token is configured in settings OR the gh CLI is installed.
     */
    public boolean isAvailable() {
        String saved = settingsService.getSettings().getGithubToken();
        return (saved != null && !saved.isBlank()) || findGhExecutable() != null;
    }

    public String getStatusDetail() {
        String saved = settingsService.getSettings().getGithubToken();
        if (saved != null && !saved.isBlank()) {
            return "✓ Personal Access Token configured (Settings → GitHub Token)";
        }
        String gh = findGhExecutable();
        if (gh == null) return "No token set and gh CLI not found — enter a PAT in Settings or install gh CLI";
        try {
            ProcessBuilder pb = new ProcessBuilder(gh, "auth", "status");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            if (out.contains("Logged in")) {
                String line = java.util.Arrays.stream(out.split("\\n"))
                        .filter(l -> l.contains("Logged in")).findFirst().orElse(out);
                return "✓ " + line.trim();
            }
            return "gh found — not authenticated (run: gh auth login)";
        } catch (Exception e) {
            return "gh found — status check failed: " + e.getMessage();
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Resolve a GitHub token. Prefers a manually saved PAT from settings;
     * falls back to {@code gh auth token} if the gh CLI is installed.
     */
    private String resolveToken() throws IOException, InterruptedException {
        // 1. Prefer manually-entered PAT from Settings
        String saved = settingsService.getSettings().getGithubToken();
        if (saved != null && !saved.isBlank()) {
            log.debug("Using GitHub token from settings (length={})", saved.length());
            return saved;
        }

        // 2. Fall back to gh CLI
        String gh = findGhExecutable();
        if (gh == null) {
            throw new IllegalStateException(
                "GitHub Copilot is not configured.\n\n" +
                "Option A — enter a GitHub Personal Access Token in Settings → AI Assistant.\n" +
                "  Create one at https://github.com/settings/tokens (needs 'models:read' scope).\n\n" +
                "Option B — install the GitHub CLI and sign in:\n" +
                "  1. Install: https://cli.github.com\n" +
                "  2. Run: gh auth login");
        }

        ProcessBuilder pb = new ProcessBuilder(gh, "auth", "token");
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String token  = new String(p.getInputStream().readAllBytes(),  StandardCharsets.UTF_8).trim();
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode  = p.waitFor();

        if (exitCode != 0 || token.isBlank()) {
            String hint = stderr.isBlank() ? "exit code " + exitCode : stderr;
            throw new IllegalStateException(
                "gh CLI found but not signed in.\n\n" +
                "Run: gh auth login\n\n" +
                "(gh reported: " + hint + ")");
        }

        log.debug("Resolved GitHub token via gh auth token (length={})", token.length());
        return token;
    }

    /**
     * Locate the {@code gh} executable.
     * Checks common install paths first, then falls back to PATH lookup.
     */
    private String findGhExecutable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String home = System.getProperty("user.home", "");

        List<String> candidates = new java.util.ArrayList<>(List.of(
            // Windows
            home + "/AppData/Local/Programs/gh/bin/gh.exe",
            "C:/Program Files/GitHub CLI/gh.exe",
            home + "/scoop/shims/gh.exe",
            // Unix / macOS
            "/usr/local/bin/gh",
            "/opt/homebrew/bin/gh",
            "/usr/bin/gh",
            home + "/.local/bin/gh"
        ));

        for (String path : candidates) {
            if (new java.io.File(path).exists()) {
                log.debug("Found gh at: {}", path);
                return path;
            }
        }

        // Fall back to PATH
        try {
            List<String> cmd = isWindows
                    ? List.of("cmd", "/c", "where", "gh")
                    : List.of("which", "gh");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && !result.isBlank()) {
                String found = result.split("\\r?\\n")[0].trim();
                log.debug("Found gh via PATH: {}", found);
                return found;
            }
        } catch (Exception ignore) {}

        return null;
    }

    private String getDefaultSystemPrompt() {
        return """
            You are the AI assistant for Venus Notebooks, an interactive multi-language notebook environment.
            Supported languages: Java 21 (JShell), full Java compile, Node.js, C#, and F#.

            Help users write, debug, and understand code in any of these languages.
            Be concise, helpful, and provide working code examples when relevant.
            Format code blocks with appropriate language markers (```java, ```csharp, etc.).
            """;
    }
}
