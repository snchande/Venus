package com.barista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.barista.model.Cell;
import com.barista.model.CellType;
import com.barista.model.Notebook;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NotebookService {

    private static final Logger log = LoggerFactory.getLogger(NotebookService.class);
    private static final String EXTENSION = ".vnb";

    @Value("${barista.notebooks.dir:notebooks}")
    private String notebooksDir;

    @Autowired
    private UserService userService;

    private final ObjectMapper objectMapper;

    public NotebookService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() throws IOException {
        // Ensure root notebooks dir exists
        Files.createDirectories(Paths.get(notebooksDir));
        // Ensure shared tutorials dir exists
        Files.createDirectories(tutorialsDir());

        // Create local user folder
        String localUserId = userService.getLocalUserId();
        Path userDir = userDir(localUserId);
        Files.createDirectories(userDir);

        // Migrate any legacy .vnb files from the flat root to the local user folder
        migrateRootNotebooks(userDir);

        // Seed with welcome notebook if user folder is empty
        if (listNotebooks(localUserId).isEmpty()) {
            createWelcomeNotebook(localUserId);
        }
    }

    // ── Public CRUD (all user-scoped) ────────────────────────────

    public List<Map<String, Object>> listNotebooks(String userId) {
        Path dir = userDir(userId);
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(EXTENSION) && Files.isRegularFile(p))
                    .map(this::readNotebookMeta)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            m -> m.get("modified").toString(), Comparator.reverseOrder()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list notebooks for {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public Optional<Notebook> getNotebook(String id, String userId) {
        Path path = notebookPath(id, userId);
        if (!Files.exists(path)) return Optional.empty();
        try {
            Notebook nb = objectMapper.readValue(path.toFile(), Notebook.class);
            nb.setFilename(path.getFileName().toString());
            return Optional.of(nb);
        } catch (IOException e) {
            log.error("Failed to read notebook {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public Notebook createNotebook(String name, String userId) {
        Notebook nb = new Notebook();
        nb.setId(UUID.randomUUID().toString());
        nb.setName(name == null || name.isBlank() ? "Untitled Notebook" : name);
        nb.setCells(new ArrayList<>());
        nb.setCreated(LocalDateTime.now());
        nb.setModified(LocalDateTime.now());
        nb.setMetadata(new HashMap<>());

        Cell starter = new Cell();
        starter.setId(UUID.randomUUID().toString());
        starter.setType(CellType.CODE);
        starter.setSource("// Start coding here!\nSystem.out.println(\"Hello from Arima Notebooks!\");");
        nb.getCells().add(starter);

        saveNotebook(nb, userId);
        return nb;
    }

    public Notebook saveNotebook(Notebook notebook, String userId) {
        notebook.setModified(LocalDateTime.now());
        if (notebook.getId() == null) notebook.setId(UUID.randomUUID().toString());
        if (notebook.getCreated() == null) notebook.setCreated(LocalDateTime.now());

        Path path = notebookPath(notebook.getId(), userId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), notebook);
            log.debug("Saved notebook: {} ({}) for user {}", notebook.getName(), notebook.getId(), userId);
        } catch (IOException e) {
            log.error("Failed to save notebook {}: {}", notebook.getId(), e.getMessage());
            throw new RuntimeException("Failed to save notebook", e);
        }
        return notebook;
    }

    public boolean deleteNotebook(String id, String userId) {
        try {
            return Files.deleteIfExists(notebookPath(id, userId));
        } catch (IOException e) {
            log.error("Failed to delete notebook {}: {}", id, e.getMessage());
            return false;
        }
    }

    // ── Tutorial / Example support (shared, not user-scoped) ────────

    private Path tutorialsDir() {
        return Paths.get(notebooksDir, "tutorials");
    }

    private Path examplesDir() {
        return Paths.get(notebooksDir, "examples");
    }

    /** Lists all tutorial AND example notebooks merged into one list. */
    public List<Map<String, Object>> listTutorials() {
        List<Map<String, Object>> all = new java.util.ArrayList<>();
        all.addAll(scanDir(tutorialsDir(), "tutorial"));
        all.addAll(scanDir(examplesDir(), "example"));
        all.sort(Comparator.comparing(m -> m.getOrDefault("id", "").toString()));
        return all;
    }

    private List<Map<String, Object>> scanDir(Path dir, String defaultCategory) {
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.toString().endsWith(EXTENSION) && Files.isRegularFile(p))
                    .map(p -> readNotebookMeta(p, defaultCategory))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to scan directory {}: {}", dir, e.getMessage());
            return List.of();
        }
    }

    /** Loads a tutorial or example notebook by ID, checking both directories. */
    public Optional<Notebook> getTutorial(String id) {
        // Check tutorials first, then examples
        for (Path dir : new Path[]{tutorialsDir(), examplesDir()}) {
            Path path = dir.resolve(id + EXTENSION);
            if (Files.exists(path)) {
                try {
                    Notebook nb = objectMapper.readValue(path.toFile(), Notebook.class);
                    nb.setFilename(path.getFileName().toString());
                    return Optional.of(nb);
                } catch (IOException e) {
                    log.error("Failed to read notebook {}: {}", id, e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    // ── Path helpers ─────────────────────────────────────────────

    private Path notebookPath(String id, String userId) {
        return userDir(userId).resolve(id + EXTENSION);
    }

    private Path userDir(String userId) {
        return Paths.get(notebooksDir, userId);
    }

    // ── Metadata ─────────────────────────────────────────────────

    private Map<String, Object> readNotebookMeta(Path path) {
        return readNotebookMeta(path, null);
    }

    private Map<String, Object> readNotebookMeta(Path path, String defaultCategory) {
        try {
            Notebook nb = objectMapper.readValue(path.toFile(), Notebook.class);
            Map<String, Object> meta = new HashMap<>();
            meta.put("id", nb.getId());
            meta.put("name", nb.getName());
            meta.put("description", nb.getDescription());
            meta.put("created", nb.getCreated());
            meta.put("modified", nb.getModified());
            meta.put("cellCount", nb.getCells() == null ? 0 : nb.getCells().size());
            Map<String, Object> nbMeta = nb.getMetadata() != null
                    ? new HashMap<>(nb.getMetadata())
                    : new HashMap<>();
            // Stamp category if not already set in the file metadata
            if (defaultCategory != null && !nbMeta.containsKey("category")) {
                nbMeta.put("category", defaultCategory);
            }
            meta.put("metadata", nbMeta);
            return meta;
        } catch (IOException e) {
            log.warn("Failed to read notebook metadata from {}: {}", path, e.getMessage());
            return null;
        }
    }

    // ── Migration ────────────────────────────────────────────────

    private void migrateRootNotebooks(Path userDir) {
        Path rootDir = Paths.get(notebooksDir);
        try (Stream<Path> entries = Files.list(rootDir)) {
            entries
                .filter(p -> p.toString().endsWith(EXTENSION) && Files.isRegularFile(p))
                .forEach(p -> {
                    Path dest = userDir.resolve(p.getFileName());
                    if (!Files.exists(dest)) {
                        try {
                            Files.move(p, dest, StandardCopyOption.ATOMIC_MOVE);
                            log.info("Migrated notebook {} → {}", p.getFileName(), userDir.getFileName());
                        } catch (IOException e) {
                            log.warn("Could not migrate {}: {}", p.getFileName(), e.getMessage());
                        }
                    }
                });
        } catch (IOException e) {
            log.warn("Migration scan failed: {}", e.getMessage());
        }
    }

    // ── Welcome notebook ─────────────────────────────────────────

    private void createWelcomeNotebook(String userId) {
        Notebook nb = new Notebook();
        nb.setId("welcome");
        nb.setName("Welcome to Arima Notebooks");
        nb.setDescription("A quick introduction to Arima Notebooks");
        nb.setCells(new ArrayList<>());
        nb.setCreated(LocalDateTime.now());
        nb.setModified(LocalDateTime.now());
        nb.setMetadata(new HashMap<>());

        nb.getCells().add(makeCell("cell-1", CellType.MARKDOWN,
                "# Welcome to Arima Notebooks\n\nArima Notebooks is an interactive Java environment powered by JShell.\n\n- Write and execute Java code cell by cell\n- Install Maven packages from the **Packages** tab\n- Get AI assistance from the **AI** tab\n- Configure settings in the **Settings** tab"));
        nb.getCells().add(makeCell("cell-2", CellType.CODE,
                "// Hello World - click Run or press Shift+Enter\nSystem.out.println(\"Hello from Arima Notebooks!\");"));
        nb.getCells().add(makeCell("cell-3", CellType.CODE,
                "// Variables persist between cells\nvar greeting = \"Arima\";\nvar version = 1.0;\nString.format(\"Welcome to %s v%.1f\", greeting, version)"));
        nb.getCells().add(makeCell("cell-4", CellType.CODE,
                "// Java streams work great in Arima!\nimport java.util.stream.*;\n\nIntStream.range(1, 6)\n         .mapToObj(i -> \"Item \" + i)\n         .forEach(System.out::println);"));

        saveNotebook(nb, userId);
        log.info("Created welcome notebook for user {}", userId);
    }

    private Cell makeCell(String id, CellType type, String source) {
        Cell c = new Cell();
        c.setId(id);
        c.setType(type);
        c.setSource(source);
        return c;
    }
}
