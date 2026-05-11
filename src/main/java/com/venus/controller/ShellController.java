package com.venus.controller;

import com.venus.model.ExecutionResult;
import com.venus.model.Notebook;
import com.venus.model.PackageInfo;
import com.venus.service.CppExecutionService;
import com.venus.service.DotNetExecutionService;
import com.venus.service.JavaCompilerService;
import com.venus.service.NodeJsExecutionService;
import com.venus.service.NotebookService;
import com.venus.service.OrchestrationService;
import com.venus.service.PackageService;
import com.venus.service.UserService;
import com.venus.shell.JShellManager;
import com.venus.shell.ShellSession;
import com.venus.util.VenusInput;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST + WebSocket endpoints for code execution.
 *
 * Supports two execution modes per cell:
 *   "jshell" (default) — JShell snippet mode, shared state across cells in one session
 *   "java"             — Full Java compilation via javax.tools, each cell is independent
 *
 * REST:
 *   POST /api/shell/execute          - Execute code (synchronous, returns result)
 *   POST /api/shell/{id}/restart     - Restart a JShell session
 *   DELETE /api/shell/{id}           - Close a session
 *   GET  /api/shell/sessions         - List active sessions
 *   GET  /api/shell/{id}/info        - Get session info
 *
 * WebSocket (STOMP):
 *   Client sends to: /app/shell/{sessionId}
 *   Results broadcast to: /topic/shell/{sessionId}
 */
@RestController
@RequestMapping("/api/shell")
public class ShellController {

    private final JShellManager jShellManager;
    private final PackageService packageService;
    private final JavaCompilerService javaCompilerService;
    private final NodeJsExecutionService nodeJsExecutionService;
    private final DotNetExecutionService dotNetExecutionService;
    private final CppExecutionService cppExecutionService;
    private final OrchestrationService orchestrationService;
    private final NotebookService notebookService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    public ShellController(JShellManager jShellManager,
                           PackageService packageService,
                           JavaCompilerService javaCompilerService,
                           NodeJsExecutionService nodeJsExecutionService,
                           DotNetExecutionService dotNetExecutionService,
                           CppExecutionService cppExecutionService,
                           OrchestrationService orchestrationService,
                           NotebookService notebookService,
                           UserService userService,
                           SimpMessagingTemplate messagingTemplate) {
        this.jShellManager = jShellManager;
        this.packageService = packageService;
        this.javaCompilerService = javaCompilerService;
        this.nodeJsExecutionService = nodeJsExecutionService;
        this.dotNetExecutionService = dotNetExecutionService;
        this.cppExecutionService = cppExecutionService;
        this.orchestrationService = orchestrationService;
        this.notebookService = notebookService;
        this.userService = userService;
        this.messagingTemplate = messagingTemplate;
    }

    /** Execute code synchronously via REST */
    @PostMapping("/execute")
    public ResponseEntity<ExecutionResult> execute(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "default");
        String code      = body.get("code");
        String cellId    = body.get("cellId");
        String mode      = body.getOrDefault("mode", "jshell");
        // Optional stdin: newline-separated lines the user typed in the cell's Stdin panel
        String stdin     = body.getOrDefault("stdin", "");

        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ExecutionResult result;

        try {
            if ("java".equals(mode)) {
                List<String> classpath = packageService.getInstalledPackages().stream()
                        .map(PackageInfo::getJarPath)
                        .collect(Collectors.toList());
                result = javaCompilerService.execute(sessionId, cellId, code, classpath);
            } else if ("nodejs".equals(mode)) {
                result = nodeJsExecutionService.execute(sessionId, cellId, code);
            } else if ("csharp".equals(mode)) {
                result = dotNetExecutionService.executeCSharp(sessionId, cellId, code);
            } else if ("fsharp".equals(mode)) {
                result = dotNetExecutionService.executeFSharp(sessionId, cellId, code);
            } else if ("cpp".equals(mode)) {
                result = cppExecutionService.execute(sessionId, cellId, code);
            } else {
                boolean isNewSession = !jShellManager.hasSession(sessionId);
                if (isNewSession) {
                    jShellManager.getOrCreateSession(sessionId);
                    packageService.applyPackagesToSession(sessionId);
                }
                // Pre-feed any stdin lines from the cell's Stdin panel.
                // These are consumed non-interactively (non-blocking).
                // If code needs MORE input than was pre-filled, interactive mode kicks in.
                String[] stdinLines = stdin.isBlank()
                        ? new String[0]
                        : stdin.split("\n", -1);
                VenusInput.provide(stdinLines);

                // Set up the interactive stdin callback.
                // Fired when code requests input but the pre-filled queue is empty.
                // Drains any partial output already in the buffer so the browser shows
                // what was printed BEFORE the prompt, then sends an input_needed event.
                final String topic   = "/topic/shell/" + sessionId;
                final String fCellId = cellId;
                VenusInput.setInputNeededCallback(() -> {
                    ShellSession sess = jShellManager.getSession(sessionId);
                    String partial = (sess != null) ? sess.drainOutput() : "";
                    // Send a single combined message — the client appends the output text
                    // first, then shows the input prompt, avoiding any ordering race.
                    messagingTemplate.convertAndSend(topic, Map.of(
                        "type", "input_needed", "cellId", fCellId, "text", partial
                    ));
                });

                try {
                    result = jShellManager.execute(sessionId, code, cellId);
                } finally {
                    VenusInput.setInputNeededCallback(null);
                }
            }
        } catch (Exception e) {
            VenusInput.clear(); // tidy up if execution threw before consuming input
            // Return a proper error result instead of HTTP 500
            result = ExecutionResult.builder()
                    .sessionId(sessionId).cellId(cellId)
                    .error("Internal error: " + e.getMessage())
                    .status("ERROR").success(false)
                    .executionTimeMs(0L).executionCount(0)
                    .build();
        }

        // Track this cell as executed in the session so orchestration can use cached output
        if (cellId != null && result.isSuccess()) {
            orchestrationService.markCellExecuted(sessionId, cellId);
        }

        return ResponseEntity.ok(result);
    }

    /** Restart a JShell session (clears all variables) */
    @PostMapping("/{sessionId}/restart")
    public ResponseEntity<Map<String, String>> restartSession(@PathVariable String sessionId) {
        jShellManager.restartSession(sessionId);
        orchestrationService.clearSessionModules(sessionId); // cross-notebook modules must reload
        orchestrationService.clearSessionCache(sessionId);   // cell execution cache must reset
        dotNetExecutionService.clearSessionAnchors(sessionId); // C#/F# anchor cache must reload
        cppExecutionService.clearSessionAnchors(sessionId);   // C++ anchor cache must reload
        // Re-apply packages after restart so imports still work
        packageService.applyPackagesToSession(sessionId);
        return ResponseEntity.ok(Map.of(
            "message", "Session restarted",
            "sessionId", sessionId
        ));
    }

    /** Close and remove a session */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Boolean>> closeSession(@PathVariable String sessionId) {
        jShellManager.closeSession(sessionId);
        return ResponseEntity.ok(Map.of("closed", true));
    }

    /** List all active session IDs */
    @GetMapping("/sessions")
    public ResponseEntity<Set<String>> listSessions() {
        return ResponseEntity.ok(jShellManager.getSessionIds());
    }

    /** Get info about a specific session */
    @GetMapping("/{sessionId}/info")
    public ResponseEntity<Map<String, Object>> getSessionInfo(@PathVariable String sessionId) {
        ShellSession session = jShellManager.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> info = new HashMap<>();
        info.put("sessionId", session.getSessionId());
        info.put("executionCount", session.getExecutionCount());
        info.put("classpath", session.getClasspath());
        return ResponseEntity.ok(info);
    }

    // ── Orchestration endpoints ──────────────────────────────────────────

    /**
     * Execute a PIPELINE cell — resolves anchor dependencies, toposorts, runs in order.
     * Body: { notebookId, pipelineCellId, sessionId }
     */
    @PostMapping("/execute-pipeline")
    public ResponseEntity<?> executePipeline(@RequestBody Map<String, String> body) {
        String notebookId    = body.get("notebookId");
        String pipelineCellId = body.get("pipelineCellId");
        String sessionId     = body.getOrDefault("sessionId", "default");

        if (notebookId == null || pipelineCellId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "notebookId and pipelineCellId required"));
        }
        try {
            Notebook notebook = notebookService.getNotebook(notebookId, userService.getCurrentUser().getId())
                    .or(() -> notebookService.getTutorial(notebookId))
                    .orElse(null);
            if (notebook == null) return ResponseEntity.notFound().build();

            if (!jShellManager.hasSession(sessionId)) {
                jShellManager.getOrCreateSession(sessionId);
                packageService.applyPackagesToSession(sessionId);
            }

            boolean forceRun = "true".equalsIgnoreCase(body.get("forceRun"));
            OrchestrationService.PipelineResult result =
                    orchestrationService.executePipeline(notebook, pipelineCellId, sessionId, forceRun);

            return ResponseEntity.ok(Map.of(
                "results", result.results(),
                "success", result.success(),
                "error",   result.error() != null ? result.error() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Execute a cell with all its declared //@ depends: resolved first.
     * Body: { notebookId, cellId, sessionId }
     */
    @PostMapping("/execute-with-deps")
    public ResponseEntity<?> executeWithDeps(@RequestBody Map<String, String> body) {
        String notebookId = body.get("notebookId");
        String cellId     = body.get("cellId");
        String sessionId  = body.getOrDefault("sessionId", "default");

        if (notebookId == null || cellId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "notebookId and cellId required"));
        }
        try {
            Notebook notebook = notebookService.getNotebook(notebookId, userService.getCurrentUser().getId())
                    .or(() -> notebookService.getTutorial(notebookId))
                    .orElse(null);
            if (notebook == null) return ResponseEntity.notFound().build();

            if (!jShellManager.hasSession(sessionId)) {
                jShellManager.getOrCreateSession(sessionId);
                packageService.applyPackagesToSession(sessionId);
            }

            boolean forceRun = "true".equalsIgnoreCase(body.get("forceRun"));
            OrchestrationService.PipelineResult result =
                    orchestrationService.executeWithDependencies(notebook, cellId, sessionId, forceRun);

            return ResponseEntity.ok(Map.of(
                "results", result.results(),
                "success", result.success(),
                "error",   result.error() != null ? result.error() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Execute all cells from notebook top down to and including the given cell.
     * Body: { notebookId, cellId, sessionId }
     */
    @PostMapping("/run-to-here")
    public ResponseEntity<?> runToHere(@RequestBody Map<String, String> body) {
        String notebookId = body.get("notebookId");
        String cellId     = body.get("cellId");
        String sessionId  = body.getOrDefault("sessionId", "default");

        if (notebookId == null || cellId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "notebookId and cellId required"));
        }
        try {
            Notebook notebook = notebookService.getNotebook(notebookId, userService.getCurrentUser().getId())
                    .or(() -> notebookService.getTutorial(notebookId))
                    .orElse(null);
            if (notebook == null) return ResponseEntity.notFound().build();

            if (!jShellManager.hasSession(sessionId)) {
                jShellManager.getOrCreateSession(sessionId);
                packageService.applyPackagesToSession(sessionId);
            }

            OrchestrationService.PipelineResult result =
                    orchestrationService.executeToHere(notebook, cellId, sessionId);

            return ResponseEntity.ok(Map.of(
                "results", result.results(),
                "success", result.success(),
                "error",   result.error() != null ? result.error() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validate the dependency graph for a notebook.
     * Returns list of validation errors (empty = valid graph).
     */
    @GetMapping("/validate-graph/{notebookId}")
    public ResponseEntity<?> validateGraph(@PathVariable String notebookId) {
        try {
            Notebook notebook = notebookService.getNotebook(notebookId, userService.getCurrentUser().getId())
                    .or(() -> notebookService.getTutorial(notebookId))
                    .orElse(null);
            if (notebook == null) return ResponseEntity.notFound().build();
            List<String> errors = orchestrationService.validateGraph(notebook);
            return ResponseEntity.ok(Map.of("valid", errors.isEmpty(), "errors", errors));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Code completion — JShell mode.
     * Body: { sessionId, source, cursor }  →  { completions: [...] }
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> complete(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.getOrDefault("sessionId", "console");
        String source    = (String) body.getOrDefault("source", "");
        int cursor = source.length();
        if (body.get("cursor") instanceof Number n) cursor = n.intValue();
        List<String> completions = jShellManager.complete(sessionId, source, cursor);
        return ResponseEntity.ok(Map.of("completions", completions));
    }

    /**
     * WebSocket handler for real-time code execution.
     * Client sends: /app/shell/{sessionId}
     * Result broadcast to: /topic/shell/{sessionId}
     */
    @MessageMapping("/shell/{sessionId}")
    public void executeViaWebSocket(@DestinationVariable String sessionId,
                                     Map<String, String> payload) {
        String code   = payload.get("code");
        String cellId = payload.get("cellId");
        String mode   = payload.getOrDefault("mode", "jshell");

        if (code == null || code.isBlank()) return;

        if ("java".equals(mode)) {
            List<String> classpath = packageService.getInstalledPackages().stream()
                    .map(PackageInfo::getJarPath)
                    .collect(Collectors.toList());
            javaCompilerService.execute(sessionId, cellId, code, classpath);
        } else if ("nodejs".equals(mode)) {
            nodeJsExecutionService.execute(sessionId, cellId, code);
        } else if ("csharp".equals(mode)) {
            dotNetExecutionService.executeCSharp(sessionId, cellId, code);
        } else if ("fsharp".equals(mode)) {
            dotNetExecutionService.executeFSharp(sessionId, cellId, code);
        } else if ("cpp".equals(mode)) {
            cppExecutionService.execute(sessionId, cellId, code);
        } else {
            if (!jShellManager.hasSession(sessionId)) {
                jShellManager.getOrCreateSession(sessionId);
                packageService.applyPackagesToSession(sessionId);
            }
            jShellManager.execute(sessionId, code, cellId);
        }
    }

    /**
     * WebSocket handler for interactive stdin.
     * Browser sends a line of user input to /app/shell/{sessionId}/input.
     * The line is added to VenusInput's queue, unblocking any pending take() in STDIN.
     */
    @MessageMapping("/shell/{sessionId}/input")
    public void handleStdinInput(@DestinationVariable String sessionId,
                                 Map<String, String> payload) {
        String line = payload.getOrDefault("line", "");
        VenusInput.addLine(line);
    }
}
