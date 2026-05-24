package com.venus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gemini AI integration via the local {@code gemini} CLI (Google Gemini).
 *
 * Routes all requests through the CLI executable — no API key configuration
 * required in Venus as long as the CLI is authenticated.
 *
 * Prerequisites:
 *   1. Install: npm install -g @google/gemini-cli  (or brew install gemini)
 *   2. Authenticate: run  gemini auth  in a terminal
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final SettingsService settingsService;

    public GeminiService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public String chat(String userMessage, String systemPrompt) throws IOException, InterruptedException {
        return chat(List.of(Map.of("role", "user", "content", userMessage)), systemPrompt);
    }

    public String chat(List<Map<String, String>> messages, String systemPrompt)
            throws IOException, InterruptedException {

        String geminiExe = findGeminiExecutable();
        if (geminiExe == null) {
            throw new IllegalStateException(
                "Gemini CLI not found.\n\n" +
                "Venus requires the Google Gemini CLI to be installed and signed in:\n" +
                "  1. Install: npm install -g @google/gemini-cli\n" +
                "  2. Authenticate: run  gemini auth  in a terminal\n\n" +
                "The CLI is checked in these locations:\n" +
                "  ~/.local/bin/gemini, ~/AppData/Roaming/npm/gemini.cmd, and your system PATH.");
        }

        return chatViaCli(messages, systemPrompt, geminiExe);
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
        return findGeminiExecutable() != null;
    }

    public String getStatusDetail() {
        String exe = findGeminiExecutable();
        if (exe == null) return "gemini CLI not found — install via: npm i -g @google/gemini-cli";
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

    private String chatViaCli(List<Map<String, String>> messages, String systemPrompt, String geminiExe)
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

        log.info("Gemini CLI chat via {}: {} messages", geminiExe, messages.size());

        ProcessBuilder pb = new ProcessBuilder(geminiExe, "-p", prompt.toString());
        pb.directory(com.venus.util.VenusHome.directory());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Close stdin immediately (prompt passed as argument)
        process.getOutputStream().close();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode  = process.waitFor();

        if (exitCode != 0 || output.isBlank()) {
            // Fallback: try piping via stdin instead
            return chatViaStdin(prompt.toString(), geminiExe);
        }

        return output;
    }

    private String chatViaStdin(String prompt, String geminiExe) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(geminiExe);
        pb.directory(com.venus.util.VenusHome.directory());
        pb.redirectErrorStream(false);
        Process process = pb.start();

        try (java.io.OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode  = process.waitFor();

        if (exitCode != 0 || output.isBlank()) {
            String err = stderr.isBlank() ? ("exit code " + exitCode) : stderr;
            log.warn("Gemini CLI failed ({}): {}", geminiExe, err);
            throw new IOException("Gemini CLI error: " + err
                + "\n\nRun `gemini auth` in a terminal to sign in.");
        }

        return output;
    }

    private String findGeminiExecutable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String home = System.getProperty("user.home", "");

        java.util.List<String> candidates = new java.util.ArrayList<>(java.util.List.of(
            home + "/.local/bin/gemini",
            home + "/AppData/Roaming/npm/gemini.cmd",
            home + "/AppData/Local/Programs/gemini/gemini.exe",
            "/usr/local/bin/gemini",
            "/usr/bin/gemini",
            "/opt/homebrew/bin/gemini"
        ));

        for (String path : candidates) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.canRead()) {
                log.debug("Found gemini at: {}", path);
                return path;
            }
        }

        try {
            List<String> cmd = isWindows
                ? List.of("cmd", "/c", "where", "gemini")
                : List.of("which", "gemini");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String result = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() == 0 && !result.isBlank()) {
                String found = result.split("\\r?\\n")[0].trim();
                log.debug("Found gemini via PATH: {}", found);
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
