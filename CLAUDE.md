# Venus Notebooks - Claude Instructions

## Project Overview
Venus Notebooks is a Java-based interactive notebook environment (similar to Jupyter) powered by
JShell (Java's interactive REPL). It runs as a local Spring Boot web server with a single-page
web UI for writing and executing code in **seven languages** — Java/JShell, JavaScript (Node.js),
TypeScript (Node.js type-stripping + optional `tsc`), C# / F# (.NET SDK), and C++ (MSVC/GCC/Clang) —
managing Maven, npm, and NuGet packages, and using Claude/Copilot/Gemini AI assistance.

## Technology Stack
- **Backend**: Java 21 + Spring Boot 3.2.x
- **REPL Engine**: JDK JShell API (`jdk.jshell` module)
- **Real-time**: STOMP over WebSocket (SockJS)
- **Frontend**: Vanilla HTML/CSS/JavaScript (no build step required)
- **AI**: Claude API via HTTP (Anthropic SDK-style REST calls)
- **Package Manager**: Maven Central HTTP download + JShell classpath injection
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

### Quick Start (scripts)
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
├── README.md                          # User-facing documentation
├── pom.xml                            # Maven build file
├── .gitignore
├── .claude/
│   ├── settings.json                  # Claude Code settings
│   └── commands/                      # Custom slash commands
│       ├── start.md                   # /start command
│       └── build.md                   # /build command
├── docs/
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── SETUP.md
│   └── USAGE.md
├── src/main/java/com/venus/
│   ├── VenusApplication.java          # Spring Boot entry point
│   ├── config/
│   │   ├── WebSocketConfig.java       # STOMP WebSocket config
│   │   └── CorsConfig.java            # CORS for dev
│   ├── model/                         # Data models (POJOs)
│   │   ├── Notebook.java
│   │   ├── Cell.java
│   │   ├── CellType.java
│   │   ├── PackageInfo.java
│   │   ├── VenusSettings.java
│   │   └── ExecutionResult.java
│   ├── shell/                         # JShell integration
│   │   ├── JShellManager.java         # Session management
│   │   └── ShellSession.java          # Per-session JShell instance
│   ├── service/                       # Business logic
│   │   ├── NotebookService.java       # CRUD for .vnb files
│   │   ├── PackageService.java        # Maven Central downloads
│   │   ├── ClaudeService.java         # Claude API integration
│   │   └── SettingsService.java       # Settings persistence
│   └── controller/                    # REST + WebSocket endpoints
│       ├── NotebookController.java
│       ├── ShellController.java
│       ├── PackageController.java
│       ├── LLMController.java
│       └── SettingsController.java
├── src/main/resources/
│   ├── application.properties
│   └── static/                        # Served at http://localhost:8585/
│       ├── index.html                 # Single-page app entry point
│       ├── css/venus.css
│       └── js/
│           ├── app.js                 # Main app initialization
│           ├── notebook.js            # Notebook editor
│           ├── console-tab.js         # Interactive console
│           ├── packages.js            # Package manager UI
│           ├── settings.js            # Settings panel
│           └── ai-assistant.js        # Claude AI panel
├── notebooks/                         # User notebooks (*.vnb JSON files)
│   └── welcome.vnb
├── data/                              # App data (settings.json, packages.json)
└── scripts/
    ├── start.sh
    └── start.bat
```

## Important Files
- `src/main/java/com/venus/shell/JShellManager.java` - Core JShell execution engine
- `src/main/java/com/venus/service/NodeJsExecutionService.java` - JavaScript subprocess executor
- `src/main/java/com/venus/service/TypeScriptExecutionService.java` - TypeScript executor (Node type-stripping + optional `tsc`)
- `src/main/java/com/venus/service/DotNetExecutionService.java` - C# + F# executor
- `src/main/java/com/venus/service/CppExecutionService.java` - C++ executor (auto-detects MSVC/GCC/Clang)
- `src/main/java/com/venus/service/ClaudeService.java` - Claude API integration
- `src/main/resources/static/index.html` - The entire frontend UI
- `src/main/resources/application.properties` - Server config (port 8585)
- `data/settings.json` - Runtime settings (Claude API key stored here)

## Development Guidelines
1. **JShell Module Access**: Always run with `--add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED`
2. **Notebook Format**: Notebooks are stored as `.vnb` JSON files in the `notebooks/` directory
3. **Package Storage**: Downloaded JARs go in `data/packages/` directory
4. **Settings**: Never commit `data/settings.json` (contains API keys)
5. **Frontend**: No build step - pure HTML/CSS/JS served by Spring Boot static resource handler
6. **WebSocket**: All real-time output uses STOMP over SockJS at `/ws`

## API Key Configuration
Set the Claude API key via:
1. The Settings tab in the UI
2. Environment variable: `ANTHROPIC_API_KEY`
3. Directly in `data/settings.json`

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
- **JShell not found**: Ensure JDK 11+ is installed (not just JRE). Check `java.home` system property.
- **WebSocket connection fails**: Check browser console for STOMP errors. Ensure `/ws` endpoint is accessible.
- **Package download fails**: Check internet connectivity and Maven Central availability.
- **Claude API errors**: Verify `ANTHROPIC_API_KEY` is set correctly in Settings.
