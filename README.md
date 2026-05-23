# Venus Notebooks

> **Interactive multi-language notebooks in your browser — Java, C++, C#, F#, JavaScript, TypeScript — with AI assistance**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Version](https://img.shields.io/badge/version-1.2.0-informational.svg)](CHANGELOG.md)

Venus Notebooks is a **locally-hosted, browser-based notebook environment** — like Jupyter, but for seven languages. Write and run Java, JShell, JavaScript, TypeScript, C#, F#, and C++ cells side by side, manage Maven, npm, and NuGet dependencies, chain cells with pipeline orchestration, and get AI assistance from Claude, GitHub Copilot, or Gemini — all without leaving your browser.

No cloud account. No data sent anywhere. Runs entirely on your machine.

![Venus Notebooks UI](docs/screenshots/ui-preview.png)

---

## Why Venus?

Java developers have always been second-class citizens in the notebook world. Jupyter supports Python natively; everything else is a plugin or a workaround. Venus was built from the ground up for Java:

- **JShell-native** — the official Java REPL, not a workaround
- **Full class compilation** — switch any cell to full `javac` compile+run mode
- **Maven built in** — install any Maven Central package in seconds; it's immediately on the classpath
- **Real JVM** — no sandboxing, no limitations — it's your JDK
- **JavaScript too** — Node.js cells for when you need it
- **TypeScript built in** — TS cells via Node.js's built-in type-stripping (Node 22.6+); install `tsc` for full type-check diagnostics; shares npm modules with JavaScript
- **C# and F# support** — .NET cells via `dotnet run` and `dotnet fsi`; install NuGet packages via the NuGet tab or inline `#r` directives
- **C++ native** — compile and run C++ cells with MSVC, GCC, or Clang; 26 standard headers pre-included
- **AI in the loop** — Claude, GitHub Copilot, or Gemini generates, explains, and debugs code inline — all via local CLI, no API keys
- **Offline by default** — your notebooks and code never leave your machine

---

## Features at a Glance

| Feature | Description |
|---------|-------------|
| **Seven execution modes** | JShell · Java · JavaScript (Node.js) · TypeScript · C# · F# · C++ |
| **Real-time output** | Console output streams live via WebSocket — no polling |
| **Maven Package Manager** | Install/uninstall Maven packages; auto-injected into JShell classpath |
| **npm Package Manager** | Install npm packages for JavaScript *and* TypeScript cells with one click |
| **NuGet Package Manager** | Install NuGet packages for C# and F# cells with one click |
| **C++ Built-in Headers** | 26 standard headers pre-included; MSVC, GCC, and Clang auto-detected |
| **TypeScript Type-checking** | Optional `tsc --noEmit` pass before each cell — type errors with proper line numbers |
| **Pipeline Orchestration** | Chain cells with `//@ depends:` annotations — works across all 7 languages |
| **Multi-provider AI** | Claude · GitHub Copilot · Gemini — all via local CLI, no API key needed |
| **AI Language Conversion** | Switch a cell's language and AI converts the code automatically |
| **MCP Server** | Expose Venus as an MCP tool server for Claude Code, Claude Desktop, and custom agents |
| **Built-in Data Science** | XChart · Commons Math · Tablesaw · simple-statistics · mathjs — pre-installed |
| **Tutorial Library** | 28 built-in tutorials across JShell, Java, JavaScript, TypeScript, C#, F#, and C++ |
| **Interactive Console** | Full REPL console with tab completion |
| **Dark theme** | Easy on the eyes by default |

---

## Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| **Java JDK** | 17+ (21 recommended) | Must be a JDK — not just JRE. JShell is a JDK tool. |
| **Maven** | 3.8+ | For building from source |
| **Node.js** | 18+ (22.6+ for TS) | Optional — for JavaScript / TypeScript cells and npm packages |
| **TypeScript (tsc)** | 5.0+ | Optional — `npm install -g typescript` to enable type-check diagnostics |
| **.NET SDK** | 6.0+ | Optional — for C# and F# cells (free from [dot.net](https://dot.net)) |
| **C++ compiler** | Any | Optional — MSVC (Windows), GCC or Clang (Mac/Linux); auto-detected |
| **AI CLI** | Latest | Optional — Claude CLI, GitHub Copilot CLI, or Gemini CLI for AI features |
| **Internet** | — | For Maven Central, npm registry, NuGet downloads |

> **Quick launch**: the `venus` CLI (`venus.cmd` for CMD, `venus.ps1` for PowerShell, `venus.sh` for Linux/macOS) handles everything — build, start, stop, status, and browser open in one command.

---

## Quick Start

### Step 1 — Clone the repository

```bash
git clone https://github.com/yourusername/venus-notebooks.git
cd venus-notebooks
```

### Step 2 — Start Venus

Pick the CLI for your shell — all three accept the same subcommands (`start`, `stop`, `status`, `build`, `rebuild`, `open`, `logs`, `version`, `help`):

**Windows — Command Prompt**
```cmd
venus
```

**Windows — PowerShell**
```powershell
./venus.ps1
```
> If PowerShell blocks the script with an execution-policy error, run once:
> `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`

**Linux / macOS**
```bash
./venus.sh
```

All three CLIs detect if the JAR exists, build it if needed, start the server, and open your browser automatically. Run `venus help` (or `./venus.sh help` / `./venus.ps1 help`) for the full command list.

**Maven dev mode (all platforms):**
```bash
mvn spring-boot:run
```

**JAR directly (all platforms):**
```bash
java --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED \
     -jar target/venus-notebooks-1.0.0-SNAPSHOT.jar
```

### Step 3 — Open your browser

Navigate to **[http://localhost:8585](http://localhost:8585)**

The `venus` CLI opens this automatically on every platform.

### Step 4 — (Optional) Enable AI features

Venus supports three AI providers — all run as local CLI subprocesses, no API key needed:

| Provider | Install | Auth |
|----------|---------|------|
| **Claude** (recommended) | [claude.ai/code](https://claude.ai/code) | `claude auth` |
| **GitHub Copilot** | `npm install -g @githubnext/github-copilot-cli` | `github-copilot-cli auth` |
| **Gemini** | `npm install -g @google/gemini-cli` | `gemini auth` |

Switch providers any time in **Settings → AI Provider**.

---

## Step-by-Step Usage Guide

### Creating Your First Notebook

1. Click the **folder icon** in the top toolbar to open the Notebook Browser
2. Click **+ New Notebook** under "My Notebooks"
3. Give it a name and press Enter
4. Your new notebook opens with one empty code cell

### Running Code

Each code cell has a **mode button** (top-right of the cell) that cycles between all seven languages:

| Mode | Badge | What it does |
|------|-------|-------------|
| **JShell** | `◈ JShell` | Runs as a Java snippet. Variables persist across cells in the same notebook. |
| **Java** | `◈ Java` | Compiles and runs a full Java class. Per-cell isolation — perfect for class definitions. |
| **JavaScript** | `◈ JS` | Runs via Node.js. Built-in helpers: `venus.table()`, `venus.html()`, `venus.display()`. |
| **TypeScript** | `◆ TS` | Runs via Node.js's built-in type-stripping (Node 22.6+). Optional `tsc --noEmit` type-check. Same helpers as JS, with full TypeScript type signatures. |
| **C#** | `◈ C#` | Compiled as a C# 9+ top-level program via `dotnet run`. NuGet packages auto-injected. |
| **F#** | `◈ F#` | Runs as an F# script via `dotnet fsi`. Inline `#r "nuget:"` directives supported. |
| **C++** | `◈ C++` | Compiled and run with MSVC/GCC/Clang. 26 standard headers pre-included. |

When you switch a cell's language, Venus offers to **convert the existing code** using AI.

**To run a cell:** Click **Run** or press `Ctrl+Enter`.

**To run all cells:** Click **Run All** in the top toolbar.

### JShell Mode — Shared State

JShell cells in the same notebook share a session. Variables declared in cell 1 are available in cell 2:

```java
// Cell 1 (JShell)
var name = "Venus";
var version = 1.2;

// Cell 2 (JShell) — can use name and version directly
System.out.printf("Hello from %s v%.1f%n", name, version);
```

### Java Mode — Full Classes

Switch a cell to Java mode to write complete class definitions:

```java
// Cell (Java mode) — compiles and runs as a full program
public class Fibonacci {
    public static void main(String[] args) {
        int n = 10;
        int a = 0, b = 1;
        for (int i = 0; i < n; i++) {
            System.out.print(a + " ");
            int temp = a + b;
            a = b;
            b = temp;
        }
    }
}
```

### Installing Maven Packages

1. Click the **Packages** tab
2. Enter a Maven coordinate: `groupId:artifactId:version`
   ```
   com.google.code.gson:gson:2.10.1
   ```
3. Click **Install**
4. The JAR downloads and is immediately available in your JShell session

Search for packages at [search.maven.org](https://search.maven.org/).

### Pipeline Orchestration

Chain cells so they run in dependency order using `//@ annotations`:

```java
//@ anchor: load-data
//@ description: Load CSV from disk
var data = Table.read().csv("data.csv");
```

```java
//@ anchor: clean-data
//@ depends: load-data
//@ description: Remove null rows
var clean = data.dropRowsWithMissingValues();
```

```java
//@ anchor: visualize
//@ depends: clean-data
//@ description: Plot the result
// ... chart code
```

Click **Run with Dependencies** on any cell to automatically execute its full dependency chain first.

### AI Assistant

1. Click the **AI** tab in the right panel (or press `Ctrl+\`)
2. Select your AI provider in the provider bar (Claude · Copilot · Gemini)
3. Type a question or request:
   - *"Write a C++ cell that sorts a vector using std::sort"*
   - *"Explain what this code does"* (attach a cell with the 🤖 button)
   - *"Generate a notebook showing Java streams with examples"*
4. Code blocks in the response include an **Insert into notebook** button

---

## Tutorial Library

Venus ships with 28 built-in tutorials. Open the Notebook Browser and click **Venus Tutorials**.

| ID | Title | Mode | Level |
|----|-------|------|-------|
| `jshell-101` | JShell Basics | JShell | Beginner |
| `jshell-201` | JShell Intermediate | JShell | Intermediate |
| `jshell-301` | JShell Advanced | JShell | Advanced |
| `jshell-401` | JShell Functional & Concurrency | JShell | Advanced |
| `jshell-501` | JShell Design Patterns | JShell | Advanced |
| `java-101` | Java Basics | Java | Beginner |
| `java-201` | Java Intermediate | Java | Intermediate |
| `java-301` | Java Advanced | Java | Advanced |
| `java-401` | Java Functional & Streams | Java | Advanced |
| `java-501` | Java Design Patterns | Java | Advanced |
| `java-601` | Java Data Science | Java | Advanced |
| `js-101` | JavaScript Basics | JS | Beginner |
| `js-201` | JavaScript Intermediate | JS | Intermediate |
| `js-301` | JavaScript Advanced | JS | Advanced |
| `js-401` | JavaScript Data Science | JS | Advanced |
| `js-501` | JavaScript D3 Visualization | JS | Advanced |
| `ts-101` | TypeScript Introduction | TS | Beginner |
| `ts-201` | TypeScript Intermediate (Generics, Classes, Unions) | TS | Intermediate |
| `ts-301` | TypeScript Advanced (Conditional & Mapped Types) | TS | Advanced |
| `ts-401` | TypeScript Expert (Async, Patterns, Modern Features) | TS | Advanced |
| `ts-501` | TypeScript Typed Data Analysis | TS | Advanced |
| `csharp-101` | C# Introduction | C# | Beginner |
| `csharp-201` | C# Data & Pipelines | C# | Intermediate |
| `fsharp-101` | F# Introduction | F# | Beginner |
| `fsharp-201` | F# Advanced Patterns | F# | Intermediate |
| `cpp-101` | C++ Fundamentals | C++ | Beginner |
| `cpp-201` | C++ Classes & STL | C++ | Intermediate |
| `cpp-301` | C++ Templates & Algorithms | C++ | Advanced |

Tutorials open in **read-only mode** — your personal notebooks are separate.

---

## Venus CLI

Three launchers sit in the project root — pick whichever matches your shell. They all expose the same subcommands and share the same banner:

```
        .           V E N U S   N O T E B O O K S
       /|\          ─────────────────────────────
      ( @ )         Java | JS | TS | C# | F# | C++
     /|\_/|\        Powered by JShell + Spring Boot
    / |   | \       Port: 8585
```

| Shell | Launcher | Background flag |
|---|---|---|
| Windows CMD | `venus` (`venus.cmd`) | `venus start --bg` |
| Windows PowerShell | `./venus.ps1` | `./venus.ps1 start -Bg` |
| Linux / macOS bash | `./venus.sh` | `./venus.sh start --bg` |

| Subcommand | Description |
|---|---|
| `start` | Start server, auto-build if needed, open browser |
| `start` *(background)* | Start detached; logs to `venus.log` |
| `stop` | Stop the running server |
| `status` | Show running state, PID, Java / Node.js / .NET versions, JAR state |
| `build` | Build JAR (skips if already built) |
| `rebuild` | Force clean rebuild |
| `open` | Open browser (server must be running) |
| `logs` | Tail `venus.log` (background mode only) |
| `version` | Show project, Java, Node.js, .NET, and Maven versions |
| `help` | Show help screen |

> **PowerShell note**: if `./venus.ps1` is blocked, run once: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

---

## Project Structure

```
venus/
├── src/main/java/com/venus/
│   ├── controller/          # REST + WebSocket endpoints
│   ├── service/             # Business logic (JShell, Java, Node.js, TypeScript, C#, F#, C++, AI, Maven, npm)
│   ├── shell/               # JShell session management
│   └── model/               # Data models (.vnb format, settings)
├── src/main/resources/
│   ├── static/              # Frontend — index.html + CSS + JS (no build step)
│   └── application.properties
├── notebooks/
│   ├── tutorials/           # 16 built-in tutorial notebooks
│   └── welcome.vnb          # Getting started notebook
├── scripts/
│   ├── start.sh             # Minimal Unix/Mac launcher (watchdog loop)
│   └── start.bat            # Minimal Windows launcher
├── venus.cmd                # Full CLI — Windows CMD
├── venus.ps1                # Full CLI — Windows PowerShell
├── venus.sh                 # Full CLI — Linux / macOS bash
└── docs/
    ├── API.md               # REST API reference
    ├── ARCHITECTURE.md      # System architecture
    ├── SETUP.md             # Detailed setup guide
    └── USAGE.md             # Feature documentation
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system design.

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8585` | HTTP server port |
| `venus.notebooks.dir` | `notebooks` | Where notebooks are stored |
| `venus.data.dir` | `data` | App data directory (packages, settings) |

Edit `src/main/resources/application.properties` or set environment variables before starting.

---

## Troubleshooting

**"JShell not found" or JShell errors on startup**
- You need a full **JDK**, not just a JRE
- Verify: `java -version` should show JDK 17 or higher
- Check that `JAVA_HOME` points to a JDK directory, not a JRE

**WebSocket connection errors**
- Check your browser console for STOMP errors
- Make sure nothing is blocking port 8585 (firewall, other processes)
- Try refreshing the page — the SockJS client reconnects automatically

**Maven package install fails**
- Check internet connectivity
- Verify the coordinate format: `groupId:artifactId:version`
- Search for valid coordinates at [search.maven.org](https://search.maven.org/)

**JavaScript cells not working**
- Node.js 18+ must be installed and on your `PATH`
- Verify: `node --version`

**TypeScript cells fail — "Node.js too old"**
- TypeScript cells require Node.js 22.6+ (Node 24 LTS recommended)
- Upgrade from [nodejs.org](https://nodejs.org), verify with `node --version`
- For full type-check diagnostics, also install: `npm install -g typescript`

**AI not responding**
- **Claude**: Install [Claude Code](https://claude.ai/code) and run `claude auth`
- **GitHub Copilot**: Run `github-copilot-cli auth`
- **Gemini**: Run `gemini auth`
- Check Settings → Server Status — your selected provider should show ✓ Found

**C++ cells fail — "No compiler found"**
- Windows: Install [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022) with "Desktop development with C++"
- macOS: Run `xcode-select --install`
- Linux: Run `sudo apt install g++`

---

## Contributing

Venus Notebooks is open source and contributions are welcome.

Whether you want to fix a bug, add a new tutorial, improve documentation, or build a new feature — read [CONTRIBUTING.md](CONTRIBUTING.md) for the full guide including development setup, coding standards, and the PR review process.

---

## Documentation

| Document | Description |
|----------|-------------|
| [docs/SETUP.md](docs/SETUP.md) | Detailed installation and prerequisites |
| [docs/USAGE.md](docs/USAGE.md) | Full feature documentation and tutorials |
| [docs/API.md](docs/API.md) | REST API reference |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System architecture and design |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute |
| [CHANGELOG.md](CHANGELOG.md) | Version history |

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Acknowledgments

- Inspired by [Jupyter Notebooks](https://jupyter.org/)
- Java REPL powered by [JShell](https://openjdk.org/jeps/222) (JEP 222)
- AI features via [Claude](https://www.anthropic.com/), [GitHub Copilot](https://github.com/features/copilot), and [Gemini](https://gemini.google.com/) CLI tools
- Built with [Spring Boot](https://spring.io/projects/spring-boot)
- Charts via [XChart](https://knowm.org/open-source/xchart/)
- DataFrames via [Tablesaw](https://github.com/jtablesaw/tablesaw)
