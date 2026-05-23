# Venus Notebooks — Changelog

All notable changes to Venus Notebooks are documented here.
Dates are in `YYYY-MM-DD` format.

---

## [2.1.0] — 2026-05-10

Venus 2.1 adds **TypeScript** as a full first-class language — bringing the total to **seven execution modes** (JShell · Java · JavaScript · TypeScript · C# · F# · C++). The integration leverages Node.js's built-in type-stripping (Node 22.6+), so no additional runtime is required beyond the existing Node.js dependency.

### TypeScript Language Support

- **TypeScript cells** — new `typescript` mode using Node.js's built-in type-stripping
  - Each cell runs as a per-cell isolated `node --experimental-strip-types script.ts` subprocess (Node 22.6+; Node 24+ runs `.ts` files natively)
  - Built-in **typed** Venus preamble: `venus.table(rows)`, `venus.display(value)`, `venus.html(content)`, `venus.stats(arr)` — full TS signatures injected at the top of every cell
  - Shares `data/npm-modules/` with JavaScript cells — `import * as ss from "simple-statistics"` works without any extra setup
  - Line-number correction on errors (preamble offset removed)
  - CodeMirror syntax highlighting uses `text/typescript`
  - TS blue (`#3178c6`) cell badge and `◆` icon

- **Optional `tsc` type-check** — if the TypeScript compiler is on the PATH, Venus runs `tsc --noEmit` before each cell with relaxed-strict settings:
  - Type errors (e.g. `Type 'string' is not assignable to type 'number'`) reported **before** execution starts
  - Type-check failures are folded into the cell's error stream alongside runtime errors
  - Without `tsc`, cells still run — only the type-check pass is skipped
  - Install with `npm install -g typescript` to enable

- **Console TypeScript runtime** — Interactive Console adds a `◆ TypeScript` button alongside JShell/Java/JS for ad-hoc TS expressions

- **AI integration** — the AI Assistant recognises `typescript` mode and emits `` ```ts `` fenced blocks in its responses; the language-conversion banner offers TS targets when cycling cell modes

### Tutorials & Examples

- **5 new tutorial notebooks** — full TS 101 → 501 series matching the JS coverage:
  - `ts-101` — Types, inference, interfaces, functions, arrays, tuples
  - `ts-201` — Generics, classes, modules, union/intersection types
  - `ts-301` — Conditional types, mapped types, `infer`, template literal types
  - `ts-401` — Async patterns, Result types, branded types, builder pattern, ES2024 features
  - `ts-501` — Typed data analysis with stats, HTML reports, and defensive parsing
- **New example notebook** — `typescript-intro` — five-minute TS tour
- Tutorial total goes from **23 → 28**

### REST API & Status

- `POST /api/shell/execute` accepts `"mode": "typescript"`
- `GET /api/settings/status` now reports `typescriptAvailable`, `tscAvailable`, and `typescriptDetail`
- TypeScript cells participate in pipelines via the existing `//@ depends:` system (per-cell isolation, same model as JavaScript and C++)

### Documentation

- In-app docs (Usage Guide, Setup, Tutorials, API Reference, Architecture, Developer Guide) updated with TypeScript sections
- README, `docs/USAGE.md`, `docs/API.md`, `docs/ARCHITECTURE.md`, `docs/SETUP.md`, and `docs/cheatsheet.html` extended
- Mode cycle in mode-toggle tooltip: **JShell → Java → JS → TS → C# → F# → C++ → JShell**

### Internal

- New `service/TypeScriptExecutionService.java` — mirrors `NodeJsExecutionService` with TS type-stripping + optional `tsc` integration
- Wired into `ShellController`, `OrchestrationService`, and `SettingsController`
- No new dependencies in `pom.xml` — TypeScript support is delivered purely through Node.js subprocesses

---

## [2.0.0] — 2026-04-17

Venus 2.0 adds **C# and F#** as full first-class languages — including pipeline dependency injection, NuGet package management, and cross-notebook cell references across all five execution modes. This is a **major feature release**.

### C# Language Support

- **C# cells** — new `csharp` mode using `dotnet run` (standard .NET SDK, no extra tools)
  - Each cell runs as a **C# 9+ top-level program** compiled and executed per cell
  - Auto-injects standard usings: `System`, `System.Linq`, `System.Collections.Generic`, `System.Text`, `System.IO`
  - Built-in helpers: `VenusHtml(html)`, `VenusDisplay(obj)`, `VenusTable<T>(list)`
  - Line-number correction on compiler errors (preamble offset removed)
  - Type declarations (`class`, `record`, `struct`, `enum`, `namespace`) are automatically re-ordered to satisfy the C# 9+ CS8803 rule
  - Inline `#r "nuget:"` directives are stripped — use the NuGet tab instead

- **C# pipeline dependency injection** — `//@ depends:` works across isolated subprocesses:
  - Ancestor cells' source is injected into the dependent cell's compilation unit
  - Ancestor output is silenced via `Console.SetOut(TextWriter.Null)` — only the current cell's output is visible
  - Full transitive closure resolved in topological order
  - Session anchor cache stores each successfully executed anchor's source for reuse

### F# Language Support

- **F# cells** — new `fsharp` mode using `dotnet fsi --exec` (built into .NET SDK 6+)
  - Each cell runs as an `.fsx` script; no `dotnet-script` required
  - Pre-opened namespaces: `System`, `System.Linq`, `System.Collections.Generic`
  - Built-in helpers: `venusHtml`, `venusDisplay`, `venusTable`
  - Inline `#r "nuget:"` directives are extracted from user code and placed at the top of the script file (before any `open` statements), ensuring correct resolution order

- **F# pipeline dependency injection** — full `//@ depends:` support:
  - Ancestor source injected with `System.Console.SetOut(System.IO.TextWriter.Null)` wrapping for output suppression
  - NuGet directives from all ancestors are deduplicated and placed at the very top of the combined script

### Cross-Notebook References (all 5 languages)

- `//@ depends: notebook:{notebookId}/{anchorName}` syntax now works in **C# and F# cells**
- **JShell / Java**: foreign cell executed in the shared session (as before)
- **C# / F#**: Venus builds an **expanded source** — the full transitive dependency chain of the foreign cell, annotation-stripped and concatenated in topological order — and caches it under the cross-notebook key; when the dependent cell runs, this expanded source is injected with output suppressed
- New example notebooks demonstrating cross-notebook C# references:
  - `csharp-shared-utils` — reusable types (`Transaction`, `Product`) and helpers (`Stats`, `Format`)
  - `csharp-cross-notebook` — finance analysis pipeline importing from `csharp-shared-utils`

### NuGet Package Manager

- New `NuGet (C# / F#)` sub-tab in the Packages panel
- Install packages by `PackageId` + `Version`; popular package quick-fill buttons
- Packages stored in `data/nuget-packages.json`, injected per-cell automatically
- REST API: `GET /api/nuget`, `POST /api/nuget/install`, `DELETE /api/nuget/{packageId}`

### Execution Model

- **Mode cycle** extended: `JShell → Java → JS → C# → F# → JShell`
- **Cell badges** — purple for C# (`#a855f7`), orange for F# (`#f97316`)
- Session restart now clears the C#/F# anchor source cache in addition to JShell state

### Tutorial Library

- 4 new tutorial notebooks: `csharp-101`, `csharp-201`, `fsharp-101`, `fsharp-201`
- 2 new example notebooks (cross-notebook pipeline demo): `csharp-shared-utils`, `csharp-cross-notebook`
- Notebook browser now includes an **Examples & Demos** subcategory

### AI Assistant

- System prompt updated with C# and F# context, helpers, pipeline dep injection rules, and NuGet guidance
- Cell mode labels updated (`C# (dotnet run)`, `F# (dotnet fsi)`)

### Open Source Preparation

- Added `CONTRIBUTING.md` — full contributor guide including dev setup, coding standards, PR process
- Added `MAINTAINER.md` — governance model, maintainer hierarchy, release process
- Rewrote `README.md` — expanded GitHub landing page with full feature and tutorial documentation
- Updated `.gitignore` — added `data/npm-modules/`, `data/users/`, `notebooks/local-*/`

### Documentation

- `docs/ARCHITECTURE.md` — complete rewrite covering all 5 execution modes, pipeline system, cross-notebook refs, session anchor cache, real-time output sentinels, server lifecycle, data science stack, and security model
- `docs/SETUP.md` — added .NET SDK install guide; removed `dotnet-script` references
- `docs/USAGE.md` — full C# and F# sections with helpers, NuGet, pipeline dep examples
- `docs/API.md` — NuGet endpoints documented; cross-notebook ref syntax added
- `README.md` — replaced `dotnet-script` requirement with standard .NET SDK 6+

---

## [1.2.0] — 2026-03-29

### Multi-Runtime Interactive Console

**Console tab completely reworked — now supports three independent runtimes with code completion.**

#### New Features
- **Runtime selector bar** in the Console tab — three toggle buttons:
  - `☕ JShell` — Java snippets with shared session state (server-side execution)
  - `♨ Java` — compile and run a full Java class per command
  - `⬡ JavaScript` — Node.js REPL
- **Active runtime badge** in the console header showing the current runtime icon + name
- **Tab completion** for all three runtimes:
  - **JShell** — server-side completion via `JShell.sourceCodeAnalysis().completionSuggestions()`
  - **Java / JavaScript** — client-side keyword/snippet hints from a curated static list
  - Completion **hint box** drops up above the input; clickable items + keyboard cycling
  - Press Tab repeatedly to cycle through suggestions; any other key hides the box
- Input placeholder text updates dynamically to match the selected runtime
- Console output now supports `VENUS_HTML:` sentinel — inline SVG/HTML charts render in the console, same as in notebook cells
- Input prefix icon reflects the active runtime (`[☕]`, `[♨]`, `[⬡]`)
- History buffer expanded to 500 entries

#### New REST Endpoint
- `POST /api/shell/complete` — returns JShell completion suggestions
  - Body: `{ sessionId, source, cursor }`
  - Response: `{ completions: [...] }`

#### Backend Changes
- `JShellManager.complete(sessionId, source, cursor)` — new method wrapping `SourceCodeAnalysis`
- `ShellSession.getJShell()` — new accessor needed by JShellManager completion

#### CSS Changes (`venus.css`)
- `.console-runtime-bar` / `.console-runtime-btn` / `.console-runtime-btn.active`
- `.console-runtime-badge`
- `.console-hint-box` / `.hint-item` / `.hint-item.active` / `.hint-more`

---

### Notebook Browser Redesign

**Replaced the single flat dropdown with a structured two-section browser.**

#### New Features
- **Notebook Browser** (click the folder icon) now shows two distinct sections:
  - **My Notebooks** — personal notebooks with a `+ New Notebook` button
  - **Venus Tutorials** — built-in read-only tutorials (separate from user notebooks)
- Tutorial notebooks are grouped by **language** (JShell / Java / JavaScript), then **subcategory**:
  - Basics & Foundations
  - Advanced
  - Data Science & Analytics
- Each tutorial card shows:
  - Level badge (e.g. `101`, `201`)
  - Language icon tag
  - `tutorial` read-only badge
- **Filter search** works across both sections simultaneously
- Tutorial notebooks open in a read-only tab — auto-save is disabled; status bar notes `(tutorial — read-only)`
- `loadNotebook(id, isTutorial)` now routes tutorial IDs to `/api/notebooks/tutorials/{id}`

#### CSS Changes (`venus.css`)
- `.nbb-section` / `.nbb-section-hdr` / `.nbb-section-title` / `.nbb-section-note`
- `.nbb-action-btn`
- `.nbb-lang-group` / `.nbb-lang-hdr`
- `.nbb-subcat` / `.nbb-subcat-label`
- `.nbb-level` / `.nbb-lang-tag` / `.nbb-ro-tag`
- `.nbb-tutorials` — left accent border on tutorial cards
- `.nb-browser-card-list` — grid wrapper for card sets
- `.nb-browser-list` forced to `display: block` to allow section-based layout

---

### Tutorial System

**New `notebooks/tutorials/` directory — globally accessible, not user-scoped.**

#### Tutorials Added
| ID | Title | Language | Level | Subcategory |
|----|-------|----------|-------|-------------|
| `jshell-101` | JShell Basics | JShell | 101 | Basics & Foundations |
| `jshell-201` | JShell Intermediate | JShell | 201 | Basics & Foundations |
| `jshell-301` | JShell Advanced | JShell | 301 | Advanced |
| `jshell-401` | JShell Functional & Concurrency | JShell | 401 | Advanced |
| `jshell-501` | JShell Design Patterns | JShell | 501 | Advanced |
| `java-101` | Java Basics | Java | 101 | Basics & Foundations |
| `java-201` | Java Intermediate | Java | 201 | Basics & Foundations |
| `java-301` | Java Advanced | Java | 301 | Advanced |
| `java-401` | Java Functional & Streams | Java | 401 | Advanced |
| `java-501` | Java Design Patterns | Java | 501 | Advanced |
| `java-601` | Java Data Science | Java | 601 | Data Science & Analytics |
| `js-101` | JavaScript Basics | JavaScript | 101 | Basics & Foundations |
| `js-201` | JavaScript Intermediate | JavaScript | 201 | Basics & Foundations |
| `js-301` | JavaScript Advanced | JavaScript | 301 | Advanced |
| `js-401` | JavaScript Data Science | JavaScript | 401 | Data Science & Analytics |
| `js-501` | JavaScript D3 Visualization | JavaScript | 501 | Data Science & Analytics |

#### New API Endpoints
- `GET /api/notebooks/tutorials` — list all tutorials with metadata
- `GET /api/notebooks/tutorials/{id}` — load a single tutorial

#### Backend Changes
- `NotebookService.listTutorials()` — scans `notebooks/tutorials/`, returns sorted list
- `NotebookService.getTutorial(id)` — reads single tutorial from tutorials directory
- `NotebookController` — added `/tutorials` and `/tutorials/{id}` routes (declared before `/{id}` to prevent Spring MVC path-variable collision)
- `NotebookService.readNotebookMeta()` — now includes `metadata` map field

---

## [1.1.0] — 2026-03-14

### Cell Orchestration & Pipeline System
- New `PIPELINE` cell type — orchestrates other cells in dependency order
- `//@ annotation` DSL in Java comments: `anchor:`, `depends:`, `pipeline:`, `steps:`, `description:`, `on-error:`
- `OrchestrationService` — Kahn's topological sort, DFS cycle detection, transitive closure
- `orchestration.js` — client-side dependency status badges (pending / running / ok / error / stale)
- New API endpoints: `POST /execute-pipeline`, `POST /execute-with-deps`, `POST /run-to-here`, `GET /validate-graph/{id}`
- `Cell.java` extended: `mode`, `anchor`, `dependsOn` (List), `pipelineSteps` (List)

### Multi-Mode Cells
- Every CODE cell now has a `mode` field: `jshell` (default) or `java` or `nodejs`
- `JavaCompilerService` — compiles to a temp dir, runs in subprocess, captures stdout/stderr
- `NodeJsExecutionService` — executes JavaScript via Node.js subprocess
- `ShellController` routes `mode` field to the correct engine
- Cell mode button cycles: JShell → Java → JavaScript → JShell

### In-line Chart Output
- `VENUS_HTML:` sentinel: output lines starting with this prefix are rendered as inline HTML/SVG
- `venus.html(content)` helper function available in all JavaScript cells
- `VenusDisplay` (Java) renders XChart charts as base64 PNG inline in cell output

### Data Science Stack (Built-in, no install)
- XChart 3.8.6 — chart rendering
- Commons Math 3.6.1 — statistics, regression, distributions
- Tablesaw 0.43.1 — DataFrames
- OpenCSV 5.9 — CSV parsing
- All imported automatically in every JShell session

### Documentation System
- `docs.js` — 8-section help overlay (Usage, Tutorials, Pipeline, MCP & Agents, API, Architecture, Setup, Developer Guide)
- Accessible via **Help** button in toolbar

### Bug Fixes
- `PackageService.applyPackagesToSession()` now called when a JShell session is first created (packages were missing from new sessions)
- Kernel restart now re-applies packages automatically
- `console-tab.js` CSS class names fixed (`cout-*` — was using `console-entry-*`)
- JShell error messages now include contextual hints
- `showOutput` in `notebook.js` now shows separate compile-error formatting

---

## [1.0.0] — 2026-03-08

### Initial Release
- Spring Boot 3.2.3 server on port 8585
- JShell-powered interactive notebook cells
- Notebook CRUD — `.vnb` JSON format saved to `notebooks/{userId}/`
- Maven package installer — downloads JARs from Maven Central, injects into JShell classpath
- npm package installer — downloads npm packages to `data/npm-modules/`
- Claude AI assistant — chat and notebook generation via Anthropic API
- STOMP WebSocket real-time output
- Dark/light theme
- Session management with per-notebook JShell isolation
- Step Navigator — walk through cells one by one

---

## Versioning

Venus Notebooks follows [Semantic Versioning](https://semver.org/):

- **MAJOR** — breaking changes to notebook file format or API
- **MINOR** — new features, new tabs, new endpoints, new cell types
- **PATCH** — bug fixes, style tweaks, documentation updates
