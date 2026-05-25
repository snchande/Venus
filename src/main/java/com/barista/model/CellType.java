package com.barista.model;

/**
 * Types of cells in a Arima notebook.
 */
public enum CellType {
    /** Executable Java code cell (JShell snippet or full Java compile) */
    CODE,
    /** Markdown text cell (rendered in browser) */
    MARKDOWN,
    /**
     * Pipeline orchestration cell.
     * Contains //@ directives that declare a named execution workflow.
     * Analogous to an Ant build target — names a sequence of cell anchors to run.
     */
    PIPELINE
}
