package com.venus.service;

import com.venus.model.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full Java compilation and execution service.
 *
 * Unlike JShell (which runs snippets in a shared session), this service:
 *  - Compiles each cell as a complete, self-contained Java program
 *  - Supports full class declarations with main() methods
 *  - Auto-wraps bare statements into a Main class when no class is detected
 *  - Respects installed Maven packages (added to compile + runtime classpath)
 *  - Each execution is independent (no shared state between Java-mode cells)
 *
 * WHY SUPPORT BOTH JSHELL AND FULL JAVA?
 *  JShell excels at exploration: no boilerplate, shared variables, instant feedback.
 *  Full Java excels at production patterns: OOP, multiple classes, package-level visibility,
 *  static initializers, complex generics, and code that mirrors real applications.
 *  Together they cover the complete Java learning and development spectrum.
 */
@Service
public class JavaCompilerService {

    private static final Logger log = LoggerFactory.getLogger(JavaCompilerService.class);
    private static final int TIMEOUT_SECONDS = 30;

    private final AtomicInteger execCounter = new AtomicInteger(0);

    /**
     * Compile and run a complete Java program.
     *
     * @param sessionId  Notebook session (used for result metadata only)
     * @param cellId     Cell that triggered this execution
     * @param code       Java source code (full class or bare statements)
     * @param classpath  List of JAR paths to include on classpath
     */
    public ExecutionResult execute(String sessionId, String cellId,
                                   String code, List<String> classpath) {
        long start = System.currentTimeMillis();

        String prepared = prepareCode(code);
        String className = extractClassName(prepared);

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("venus-java-");

            Path sourceFile = tempDir.resolve(className + ".java");
            Files.writeString(sourceFile, prepared);

            // ── Compile ───────────────────────────────────────────────
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return err(sessionId, cellId,
                    "Java compiler not available.\n" +
                    "Ensure a full JDK (not just JRE) is installed and JAVA_HOME points to it.",
                    start);
            }

            List<String> options = buildCompileOptions(tempDir, classpath);
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            try (StandardJavaFileManager fm =
                         compiler.getStandardFileManager(diagnostics, null, null)) {

                fm.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir.toFile()));

                Iterable<? extends JavaFileObject> units =
                        fm.getJavaFileObjects(sourceFile.toFile());

                JavaCompiler.CompilationTask task =
                        compiler.getTask(null, fm, diagnostics, options, null, units);

                if (!task.call()) {
                    StringBuilder errors = new StringBuilder();
                    for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                        if (d.getKind() == Diagnostic.Kind.ERROR) {
                            errors.append("Line ").append(d.getLineNumber())
                                  .append(": ").append(d.getMessage(null)).append("\n");
                        }
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    return ExecutionResult.builder()
                            .sessionId(sessionId).cellId(cellId)
                            .output("").error(errors.toString().trim())
                            .status("COMPILE_ERROR").success(false)
                            .executionTimeMs(elapsed)
                            .executionCount(execCounter.incrementAndGet())
                            .build();
                }
            }

            // ── Run ───────────────────────────────────────────────────
            List<String> cmd = buildRunCommand(tempDir, classpath, className);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            // Drain streams in background threads to prevent blocking
            Thread outT = captureStream(process.getInputStream(), stdout);
            Thread errT = captureStream(process.getErrorStream(), stderr);
            outT.start(); errT.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outT.join(2000); errT.join(2000);

            if (!finished) {
                process.destroyForcibly();
                return err(sessionId, cellId,
                    "Execution timed out after " + TIMEOUT_SECONDS + " seconds.", start);
            }

            long elapsed = System.currentTimeMillis() - start;
            int exitCode = process.exitValue();
            String errStr = stderr.toString().trim();
            boolean success = exitCode == 0 && errStr.isEmpty();

            return ExecutionResult.builder()
                    .sessionId(sessionId).cellId(cellId)
                    .output(stdout.toString())
                    .error(errStr)
                    .status(success ? "OK" : "RUNTIME_ERROR")
                    .success(success)
                    .executionTimeMs(elapsed)
                    .executionCount(execCounter.incrementAndGet())
                    .build();

        } catch (Exception e) {
            log.error("Java execution error in cell {}", cellId, e);
            return err(sessionId, cellId,
                e.getClass().getSimpleName() + ": " + e.getMessage(), start);
        } finally {
            deleteTempDir(tempDir);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * If the code has no class declaration, wrap it in a Main class with a main method.
     * This allows users to write bare statements like JShell while still using full Java semantics.
     */
    private String prepareCode(String code) {
        // Check if there is already a class declaration
        if (Pattern.compile("\\bclass\\s+\\w+").matcher(code).find()) {
            return code;
        }
        // Wrap bare code in boilerplate
        StringBuilder sb = new StringBuilder();
        sb.append("public class Main {\n");
        sb.append("    public static void main(String[] args) throws Exception {\n");
        for (String line : code.split("\n")) {
            sb.append("        ").append(line).append("\n");
        }
        sb.append("    }\n}\n");
        return sb.toString();
    }

    private String extractClassName(String code) {
        // Prefer public class name
        Matcher m = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(code);
        if (m.find()) return m.group(1);
        // Fall back to first class name
        m = Pattern.compile("\\bclass\\s+(\\w+)").matcher(code);
        if (m.find()) return m.group(1);
        return "Main";
    }

    private List<String> buildCompileOptions(Path outputDir, List<String> classpath) {
        List<String> opts = new ArrayList<>();
        opts.add("--release"); opts.add("21");
        if (!classpath.isEmpty()) {
            opts.add("-cp");
            opts.add(String.join(File.pathSeparator, classpath));
        }
        return opts;
    }

    private List<String> buildRunCommand(Path outputDir, List<String> classpath, String className) {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        List<String> cp = new ArrayList<>();
        cp.add(outputDir.toString());
        cp.addAll(classpath);
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, cp));
        cmd.add(className);
        return cmd;
    }

    private Thread captureStream(InputStream is, StringBuilder target) {
        return new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = r.readLine()) != null) {
                    target.append(line).append("\n");
                }
            } catch (IOException ignored) {}
        });
    }

    private ExecutionResult err(String sessionId, String cellId, String error, long start) {
        return ExecutionResult.builder()
                .sessionId(sessionId).cellId(cellId)
                .output("").error(error != null ? error : "Unknown error")
                .status("ERROR").success(false)
                .executionTimeMs(System.currentTimeMillis() - start)
                .executionCount(0)
                .build();
    }

    private void deleteTempDir(Path dir) {
        if (dir == null) return;
        try {
            Files.walk(dir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {}
    }
}
