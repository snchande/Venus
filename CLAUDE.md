# Venus Notebooks - Claude Instructions

> **Architecture guardrails for all AI agents live in [`AGENTS.md`](AGENTS.md).** Read it before making non-trivial changes. The companion files for Copilot (`.github/copilot-instructions.md`) and Gemini (`GEMINI.md`) all point back to `AGENTS.md` as the single source of truth.
>
> **Skills and subagents** are registered in [`.claude/`](.claude/README.md). Skills (`architecture-check`, `add-execution-language`, `add-tutorial`, `add-rest-endpoint`) auto-invoke when relevant. Subagents: the **primary `venus` agent** plus the specialists `venus-architect`, `venus-security`, `venus-tutorial-writer`.

## The `venus` agent + welcome behavior

When a user starts working in this repo — greets you, asks "what can I do / how do I start", or seems new — act as the **`venus` agent** ([`.claude/agents/venus.md`](.claude/agents/venus.md)) and deliver the common welcome from [`docs/WELCOME.md`](docs/WELCOME.md). Present the three paths and let them pick:

1. **Open the UI** — offer to run `venus start` (or `venus open` if already running) → http://localhost:8585.
2. **Drive Venus over MCP** — Venus exposes an MCP server at `/api/mcp/sse` + `/api/mcp/messages` with tools `venus_execute_code`, `venus_list_notebooks`, `venus_read_notebook`, `venus_run_pipeline`, `venus_search_cells`, `venus_load_module`, `venus_create_notebook`, `venus_append_cell`. Offer to help connect an MCP client.
3. **Personalize & extend** — *your* differentiator: you can change Venus itself (add a language, theme tweak, tutorial, bug fix) following the guardrails below, then package a PR.

Always offer to open docs (`venus docs` or read the relevant file). **Key difference to state:** the plain `venus` CLI operates/automates Venus (incl. MCP) but cannot change its code; an agentic CLI like you can also personalize and extend it. Same welcome is delivered by the `venus welcome` command for terminal users.

## Project Overview
Venus Notebooks is a Java-based interactive notebook environment (similar to Jupyter) powered by
JShell (Java's interactive REPL). It runs as a local Spring Boot web server with a single-page
web UI for writing and executing code in **seven languages** — Java/JShell, JavaScript (Node.js),
TypeScript (Node.js type-stripping + optional `tsc`), C# / F# (.NET SDK), and C++ (MSVC/GCC/Clang) —
managing Maven, npm, and NuGet packages, and using Claude/Copilot/Gemini AI assistance.

## Technology Stack
- **Backend**: Java 21 + Spring Boot 3.2.x
- **REPL Engine**: JDK JShell API (`jdk.jshell` module)
- **Subprocess runtimes**: Node.js (JS/TS), .NET SDK (C#/F#), MSVC/GCC/Clang (C++)
- **Real-time**: STOMP over WebSocket (SockJS)
- **Frontend**: Vanilla HTML/CSS/JavaScript (no build step required)
- **AI**: Three providers — Claude, GitHub Copilot, Gemini — each invoked as a **local CLI subprocess** via `ProcessBuilder` (no HTTP API key managed by Venus)
- **Package Managers**: Maven Central (JShell classpath), npm registry (`data/npm-modules/` for JS/TS), NuGet.org (`#r "nuget:"` for C#/F#)
- **Auth**: Spring Security with two modes — `local` (default, OS username, no login) and `oauth` (OAuth2 social login via `data/oauth-config.json`)
- **MCP**: Built-in Model Context Protocol server (HTTP+SSE, JSON-RPC 2.0) at `/api/mcp` exposing Venus as a tool server
- **Data Science**: XChart, Apache Commons Math, Tablesaw, OpenCSV — bundled, auto-imported in JShell
- **Storage**: JSON files on disk (`notebooks/` and `data/` directories)

## Key Commands

### Build
```bash
mvn clean package -DskipTests
```

### Run (Development)
```bash
mvn spring-boot:run
```

### Run (Production JAR)
```bash
java --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED \
     -jar target/venus-notebooks-1.0.0-SNAPSHOT.jar
```

### Test
```bash
mvn test
```

### Quick Start — Venus CLI (recommended)
The cross-platform `venus` CLI handles build, start, stop, status, and browser-open in one command.
All three launchers accept the same subcommands: `start [--bg] · stop · status · build · rebuild · open · logs · version · help`.
```bash
# Windows CMD            Windows PowerShell        Linux / macOS
venus                    ./venus.ps1               ./venus.sh
venus start --bg         ./venus.ps1 start -Bg     ./venus.sh start --bg
venus status             ./venus.ps1 status        ./venus.sh status
```
`start` auto-builds the JAR if missing, launches with the required JShell `--add-opens` flags, and opens
`http://localhost:8585`. The foreground loop also honors exit code 42 (UI-requested restart).

### Quick Start (minimal scripts)
```bash
# Unix/Mac
./scripts/start.sh

# Windows
scripts\start.bat
```

## Project Structure
```
venus/
├── CLAUDE.md                          # This file
├── AGENTS.md                          # Architecture guardrails (source of truth for all AI agents)
├── GEMINI.md / .github/copilot-instructions.md  # Companion agent files → defer to AGENTS.md
├── README.md                          # User-facing documentation
├── pom.xml                            # Maven build file
├── venus.cmd / venus.ps1 / venus.sh   # Cross-platform Venus CLI launchers
├── .claude/
│   ├── settings.json                  # Claude Code settings (permissions, registry)
│   ├── README.md                      # Registers commands/skills/agents
│   ├── commands/                      # Slash commands: start, build, create-notebook
│   ├── skills/                        # architecture-check, add-execution-language, add-tutorial, add-rest-endpoint
│   └── agents/                        # venus-architect, venus-security, venus-tutorial-writer
├── docs/
│   ├── ARCHITECTURE.md  API.md  SETUP.md  USAGE.md
│   ├── cheatsheet.html                # Developer cheatsheet (gitignored)
│   ├── brochure/                      # Product brochure (HTML + PDF)
│   ├── view/                          # Standalone docs viewer
│   └── screenshots/
├── scripts/
│   ├── start.sh / start.bat           # Minimal launchers (watchdog loop)
│   └── security-check.sh / security-check.ps1  # Pre-flight security scan
├── src/main/java/com/venus/
│   ├── VenusApplication.java          # Spring Boot entry point
│   ├── config/                        # WebSocketConfig, CorsConfig, SecurityConfig, OAuthClientConfig
│   ├── model/                         # Notebook, Cell, CellType, PackageInfo, NpmPackageInfo,
│   │                                  #   NuGetPackageInfo, VenusSettings, ExecutionResult,
│   │                                  #   AuthProvider, OAuthConfig, UserProfile
│   ├── shell/                         # JShellManager (sessions) + ShellSession (per-session JShell)
│   ├── util/                          # VenusDisplay (chart→PNG), VenusInput, VariableInspector
│   ├── service/                       # Business logic — one per concern:
│   │   ├── NotebookService            #   CRUD for .vnb files
│   │   ├── PackageService             #   Maven Central downloads → JShell classpath
│   │   ├── NpmPackageService          #   npm install → data/npm-modules/
│   │   ├── NuGetService               #   NuGet package management for C#/F#
│   │   ├── JavaCompilerService        #   Full javac compile-and-run (Java mode)
│   │   ├── NodeJsExecutionService     #   JavaScript subprocess
│   │   ├── TypeScriptExecutionService #   TS via Node type-stripping + optional tsc
│   │   ├── DotNetExecutionService     #   C# (dotnet run) + F# (dotnet fsi)
│   │   ├── CppExecutionService        #   C++ via MSVC/GCC/Clang
│   │   ├── OrchestrationService       #   Pipeline DAG: topo-sort, cycle detection, deps
│   │   ├── ClaudeService / GitHubCopilotService / CopilotCliService / GeminiService  # AI CLIs
│   │   ├── OAuthConfigService / UserService  # Auth
│   │   └── SettingsService            #   Settings persistence
│   └── controller/                    # REST + WebSocket — thin, one service call each:
│       ├── NotebookController  ShellController  PackageController
│       ├── NpmPackageController  NuGetController  LLMController
│       ├── SettingsController  SystemController  UserController
│       └── McpController               #   MCP server (/api/mcp/sse, /api/mcp/messages)
├── src/main/resources/
│   ├── application.properties         # Port 8585, auth mode, MCP, actuator
│   └── static/                        # Served at http://localhost:8585/ (no build step)
│       ├── index.html                 # Single-page app entry point
│       ├── css/venus.css
│       └── js/                        # app, notebook, console-tab, packages, npm-packages,
│                                      #   nuget, orchestration, settings, ai-assistant,
│                                      #   docs, server-lifecycle
├── notebooks/                         # *.vnb JSON files
│   ├── tutorials/                     # 25 built-in tutorials (gitignored personal dirs excluded)
│   ├── examples/                      # Example & demo notebooks
│   └── {userId}/                      # Per-user notebooks (e.g. local-{osuser})
└── data/                              # Runtime data — gitignored (settings, packages, users)
```

## Important Files
- `src/main/java/com/venus/shell/JShellManager.java` - Core JShell execution engine
- `src/main/java/com/venus/service/NodeJsExecutionService.java` - JavaScript subprocess executor
- `src/main/java/com/venus/service/TypeScriptExecutionService.java` - TypeScript executor (Node type-stripping + optional `tsc`)
- `src/main/java/com/venus/service/DotNetExecutionService.java` - C# + F# executor
- `src/main/java/com/venus/service/CppExecutionService.java` - C++ executor (auto-detects MSVC/GCC/Clang)
- `src/main/java/com/venus/service/ClaudeService.java` - Claude integration via the local `claude` CLI subprocess
- `src/main/java/com/venus/service/OrchestrationService.java` - Pipeline/dependency-graph engine
- `src/main/java/com/venus/controller/McpController.java` - MCP server (HTTP+SSE, JSON-RPC 2.0)
- `src/main/java/com/venus/config/SecurityConfig.java` - Auth modes (local / OAuth2)
- `src/main/resources/static/index.html` - The entire frontend UI
- `src/main/resources/application.properties` - Server config (port 8585, auth mode, MCP)
- `data/settings.json` - Runtime settings (gitignored); `data/oauth-config.json` - OAuth client secrets (gitignored)

## Development Guidelines
1. **JShell Module Access**: Always run with `--add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED`
2. **Notebook Format**: Notebooks are stored as `.vnb` JSON files in the `notebooks/` directory
3. **Package Storage**: Maven JARs in `data/packages/`; npm modules in `data/npm-modules/`; NuGet recorded in `data/nuget-packages.json`
4. **Secrets**: Never commit `data/settings.json` or `data/oauth-config.json` — both are gitignored. Do not add new files under `data/` to git.
5. **Frontend**: No build step - pure HTML/CSS/JS served by Spring Boot static resource handler
6. **WebSocket**: All real-time output uses STOMP over SockJS at `/ws`
7. **No `Runtime.exec(String)`**: build subprocesses with `ProcessBuilder(List<String>)` only — command injection is blocked by `scripts/security-check`

## AI Provider Configuration
Venus does **not** store an API key for AI. All three providers run as local CLI subprocesses, using
whatever auth the CLI already has:
1. Install a CLI — `claude` ([claude.ai/code](https://claude.ai/code)), `github-copilot-cli`, or `gemini`
2. Authenticate it once in a terminal (e.g. `claude auth`)
3. Pick the active provider in **Settings → AI Provider** (or via `PUT /api/settings`)

`GET /api/settings/status` reports each provider's availability (`claudeCliAvailable`,
`githubCopilotAvailable`, `geminiCliAvailable`).

## Common Tasks

### Add a new REST endpoint
1. Add method to appropriate controller in `src/main/java/com/venus/controller/`
2. Add corresponding service method if needed
3. Update `docs/API.md`

### Add a new UI tab
1. Add tab button in `index.html`
2. Add tab content `<div>` in `index.html`
3. Create corresponding JS file in `static/js/`
4. Import the JS file in `index.html`

### Modify notebook file format
1. Update `src/main/java/com/venus/model/Notebook.java`
2. Update `src/main/java/com/venus/model/Cell.java`
3. Update `NotebookService.java` serialization
4. Update frontend `notebook.js`

## Troubleshooting
- **JShell not found**: Ensure a full JDK 17+ (21 recommended) is installed (not just JRE). Check `java.home` system property.
- **WebSocket connection fails**: Check browser console for STOMP errors. Ensure `/ws` endpoint is accessible.
- **Package download fails**: Check internet connectivity and Maven Central / npm registry / NuGet.org availability.
- **AI not responding**: The selected provider's CLI must be installed and authenticated. Run `claude auth` (or the Copilot/Gemini equivalent) and check **Settings → Server Status** — the active provider should show ✓ Found.
- **TypeScript cells fail**: Node.js 22.6+ required for built-in type-stripping; install `tsc` (`npm i -g typescript`) for type-check diagnostics.
- **C++ / C# / F# cells fail**: Ensure a C++ compiler (MSVC/GCC/Clang) or the .NET SDK is on `PATH`; `venus status` reports what's detected.
