package com.barista.controller;

import com.barista.model.Notebook;
import com.barista.model.UserProfile;
import com.barista.service.NotebookService;
import com.barista.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for notebook CRUD operations.
 *
 * Personal notebooks (user-scoped):
 *   GET    /api/notebooks                  - List user's personal notebooks
 *   POST   /api/notebooks                  - Create a new notebook
 *   GET    /api/notebooks/{id}             - Get a personal notebook
 *   PUT    /api/notebooks/{id}             - Save/update a notebook
 *   DELETE /api/notebooks/{id}             - Delete a notebook
 *   PATCH  /api/notebooks/{id}/metadata    - Update metadata (tags, folder) without full save
 *
 * Tutorial notebooks (shared, read-only):
 *   GET    /api/notebooks/tutorials        - List all tutorial notebooks
 *   GET    /api/notebooks/tutorials/{id}   - Get a tutorial notebook
 */
@RestController
@RequestMapping("/api/notebooks")
public class NotebookController {

    private final NotebookService notebookService;
    private final UserService userService;

    public NotebookController(NotebookService notebookService, UserService userService) {
        this.notebookService = notebookService;
        this.userService = userService;
    }

    // ── Tutorial endpoints (read-only, not user-scoped) ──────────────

    @GetMapping("/tutorials")
    public ResponseEntity<List<Map<String, Object>>> listTutorials() {
        return ResponseEntity.ok(notebookService.listTutorials());
    }

    @GetMapping("/tutorials/{id}")
    public ResponseEntity<Notebook> getTutorial(@PathVariable String id) {
        return notebookService.getTutorial(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Personal notebook endpoints ───────────────────────────────

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listNotebooks() {
        String userId = currentUserId();
        return ResponseEntity.ok(notebookService.listNotebooks(userId));
    }

    @PostMapping
    public ResponseEntity<Notebook> createNotebook(@RequestBody(required = false) Map<String, String> body) {
        String name = body != null ? body.get("name") : null;
        Notebook nb = notebookService.createNotebook(name, currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(nb);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notebook> getNotebook(@PathVariable String id) {
        return notebookService.getNotebook(id, currentUserId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Notebook> saveNotebook(@PathVariable String id,
                                                  @RequestBody Notebook notebook) {
        notebook.setId(id);
        Notebook saved = notebookService.saveNotebook(notebook, currentUserId());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteNotebook(@PathVariable String id) {
        boolean deleted = notebookService.deleteNotebook(id, currentUserId());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /**
     * Patch just the metadata fields (tags, folder, description, etc.) without
     * sending the full notebook payload.  Reads the current notebook, merges the
     * patch fields into its metadata, and re-saves.
     */
    @PatchMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> patchMetadata(@PathVariable String id,
                                                              @RequestBody Map<String, Object> patch) {
        String uid = currentUserId();
        return notebookService.getNotebook(id, uid)
                .map(nb -> {
                    if (nb.getMetadata() == null) nb.setMetadata(new HashMap<>());
                    nb.getMetadata().putAll(patch);
                    notebookService.saveNotebook(nb, uid);
                    return ResponseEntity.ok(nb.getMetadata());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String currentUserId() {
        UserProfile user = userService.getCurrentUser();
        if (user == null) throw new IllegalStateException("No authenticated user");
        return user.getId();
    }
}
