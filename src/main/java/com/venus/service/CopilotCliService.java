package com.venus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * GitHub Copilot CLI integration via the local {@code copilot} CLI.
 *
 * Routes all requests through the CLI executable — similar to ClaudeService
 * and GeminiService. Prompts are piped via stdin.
 *
 * Prerequisites:
 *   Install the Copilot CLI and authenticate it.
 *   The CLI is expected to be available as {@code copilot} on the system PATH.
 */
@Service
public class CopilotCliService {

    private static final Logger log = LoggerFactory.getLogger(CopilotCliService.class);

    private final SettingsService settingsService;

    public CopilotCliService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public String chat(String userMessage, String systemPrompt) throws IOException, InterruptedException {
        return chat(List.of(Map.of("role", "user", "content", userMessage)), systemPrompt);
    }

    public String chat(List<Map<String, String>> messages, String systemPrompt)
            throws IOException, InterruptedException {

        String exe = findCopilotExecutable();
        if (exe == null) {
            throw new IllegalStateException(
                "Copilot CLI not found.\n\n" +
                "Install the Copilot CLI and make sure the `copilot` command is available on your PATH.\n" +
                "After installing, authenticate it before using it in Venus.");
        }
        return chatViaCli(messages, systemPrompt, exe);
    }

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

    public String explainCode(String code) throws IOException, InterruptedException {
        return chat("Explain this Java code clearly and concisely:\n\n```java\n" + code + "\n```", null);
    }

    public String fixError(String code, String error) throws IOException, InterruptedException {
        return chat(String.format("""
            This Java code has an error. Please provide a corrected version and explain what was wrong.

            Code:
            ```java
            %s
            ```

            Error:
            ```
            %s
            ```
            """, code, error), null);
    }

    public boolean isAvailable() {
        return findCopilotExecutable() != null;
    }

    public String getStatusDetail() {
        String exe = findCopilotExecutable();
        if (exe == null) return "copilot CLI not found — install and add to PATH";
        try {
            ProcessBuilder pb = new ProcessBuilder(exe, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            return out.isBlank() ? ("✓ Found: " + exe) : ("✓ " + out.split("\\r?\\n")[0]);
        } catch (Exception e) {
            return "✓ Found: " + exe;
        }
    }

    private String chatViaCli(List<Map<String, String>> messages, String systemPrompt, String exe)
            throws IOException, InterruptedException {

        String sys = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : getDefaultSystemPrompt();
        StringBuilder prompt = new StringBuilder();
        prompt.append(sys).append("\n\n---\n\n");
        for (Map<String, String> msg : messages) {
            String role    = msg.get("role");
            String content = msg.get("content");
            if ("user".equals(role))           prompt.append("**User:** ").append(content).append("\n\n");
            else if ("assistant".equals(role)) prompt.append("**Assistant:** ").append(content).append("\n\n");
        }
        prompt.append("**Assistant:**");

        log.info("Copilot CLI chat via {}: {} messages", exe, messages.size());

        // Pipe prompt via stdin. Run from the Venus repo root so Copilot loads
        // .github/copilot-instructions.md + AGENTS.md (no-op when VENUS_HOME is
        // unset — see VenusHome).
        ProcessBuilder pb = new ProcessBuilder(exe);
        pb.directory(com.venus.util.VenusHome.directory());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        try (java.io.OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.toString().getBytes(StandardCharsets.UTF_8));
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode  = process.waitFor();

        if (exitCode != 0 || output.isBlank()) {
            String err = stderr.isBlank() ? ("exit code " + exitCode) : stderr;
            log.warn("Copilot CLI failed ({}): {}", exe, err);
            throw new IOException("Copilot CLI error: " + err
                + "\n\nMake sure the copilot CLI is authenticated.");
        }

        return output;
    }

    private String findCopilotExecutable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String home = System.getProperty("user.home", "");

        java.util.List<String> candidates = new java.util.ArrayList<>(java.util.List.of(
            home + "/AppData/Roaming/npm/copilot.cmd",
            home + "/AppData/Local/Programs/copilot/copilot.exe",
            home + "/.local/bin/copilot",
            "/usr/local/bin/copilot",
            "/usr/bin/copilot",
            "/opt/homebrew/bin/copilot"
        ));

        for (String path : candidates) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.canRead()) {
                log.debug("Found copilot at: {}", path);
                return path;
            }
        }

        // Fall back to PATH lookup
        try {
            List<String> cmd = isWindows
                ? List.of("cmd", "/c", "where", "copilot")
                : List.of("which", "copilot");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && !result.isBlank()) {
                String found = result.split("\\r?\\n")[0].trim();
                log.debug("Found copilot via PATH: {}", found);
                return found;
            }
        } catch (Exception ignore) {}

        return null;
    }

    private String getDefaultSystemPrompt() {
        return """
            You are the AI assistant for Venus Notebooks, an interactive notebook environment.
            You help users write, debug, and understand code in Java, JavaScript, C++, C#, and F#.

            Key capabilities:
            - Java 21 with JShell (no class/method wrappers needed for simple code)
            - JavaScript/Node.js
            - C++17 with MSVC
            - C# and F# with .NET

            Be concise, helpful, and provide working code examples.
            Format code blocks with appropriate language markers (```java```, ```cpp```, etc.).
            """;
    }
}
