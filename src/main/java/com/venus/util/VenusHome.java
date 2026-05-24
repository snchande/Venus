package com.venus.util;

import java.io.File;

/**
 * Resolves the Venus project root for spawning AI CLI subprocesses.
 *
 * <p>The {@code venus} launchers export {@code VENUS_HOME} so the in-UI AI panel
 * runs Claude / Copilot / Gemini from the repo root, where they pick up
 * {@code AGENTS.md}, {@code .claude/} skills + agents, and the provider
 * instruction files. When the variable is absent (e.g. dev runs via
 * {@code mvn spring-boot:run}) the JVM working directory is already the repo
 * root, so the CLI inherits the right context anyway.
 */
public final class VenusHome {

    private VenusHome() {}

    /**
     * The directory AI CLI subprocesses should run in, or {@code null} to inherit
     * the JVM's working directory. Passing {@code null} to
     * {@link ProcessBuilder#directory(File)} is a no-op, so callers can apply the
     * result unconditionally.
     */
    public static File directory() {
        String home = System.getenv("VENUS_HOME");
        if (home == null || home.isBlank()) {
            return null;
        }
        File dir = new File(home);
        return dir.isDirectory() ? dir : null;
    }
}
