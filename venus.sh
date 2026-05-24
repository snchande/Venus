#!/usr/bin/env bash
# ============================================================================
#  Venus Notebooks - Linux / macOS CLI
#
#  Mirrors venus.cmd (Windows) and venus.ps1 (PowerShell).
#  Subcommands:
#    start [--bg]  stop  status  build  rebuild  open  logs  version  help
# ============================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR="target/venus-notebooks-1.0.0-SNAPSHOT.jar"
PORT=8585
URL="http://localhost:${PORT}"

# ── AI co-pilot context ─────────────────────────────────────────────────────
# These files turn any AI CLI invoked inside this repo (the in-UI AI panel or a
# terminal session) into a Venus-aware co-pilot that follows the architecture
# guardrails and can use the registered skills + subagents.
AGENTS_GUIDE="$SCRIPT_DIR/AGENTS.md"
SKILLS_DIR="$SCRIPT_DIR/.claude/skills"
AGENTS_DIR="$SCRIPT_DIR/.claude/agents"

# ── Colours (skip if NO_COLOR or not a TTY) ─────────────────────────────────
if [ -t 1 ] && [ -z "${NO_COLOR-}" ]; then
    C_RESET=$'\033[0m'
    C_DIM=$'\033[90m'
    C_RED=$'\033[91m'
    C_GREEN=$'\033[92m'
    C_YELLOW=$'\033[93m'
    C_BLUE=$'\033[94m'
    C_MAGENTA=$'\033[95m'
    C_CYAN=$'\033[96m'
    C_WHITE=$'\033[97m'
else
    C_RESET=''; C_DIM=''; C_RED=''; C_GREEN=''; C_YELLOW=''
    C_BLUE=''; C_MAGENTA=''; C_CYAN=''; C_WHITE=''
fi

say()    { printf '%b\n' "$*"; }
title()  { say "${C_WHITE}$*${C_RESET}"; }
info()   { say "${C_CYAN}$*${C_RESET}"; }
ok()     { say "${C_GREEN}$*${C_RESET}"; }
warn()   { say "${C_YELLOW}$*${C_RESET}"; }
err()    { say "${C_RED}$*${C_RESET}"; }
dim()    { say "${C_DIM}$*${C_RESET}"; }

banner() {
    echo
    printf '%s        .         %s%s  V E N U S   N O T E B O O K S%s\n' "$C_YELLOW" "$C_RESET" "$C_WHITE" "$C_RESET"
    printf '%s       /|\\        %s%s  ─────────────────────────────%s\n'  "$C_YELLOW" "$C_RESET" "$C_DIM"   "$C_RESET"
    printf '%s      ( @ )       %s%s  Java | JS | TS | C# | F# | C++%s\n' "$C_YELLOW" "$C_RESET" "$C_CYAN"  "$C_RESET"
    printf '%s     /|\\_/|\\      %s%s  Powered by JShell + Spring Boot%s\n' "$C_YELLOW" "$C_RESET" "$C_DIM" "$C_RESET"
    printf '%s    / |   | \\     %s%s  Port: %d%s\n' "$C_YELLOW" "$C_RESET" "$C_DIM" "$PORT" "$C_RESET"
    printf '%s   /__|   |__\\    %s\n' "$C_YELLOW" "$C_RESET"
    dim   '  ─────────────────────────────'
    echo
}

# ── Probes ──────────────────────────────────────────────────────────────────
have()       { command -v "$1" >/dev/null 2>&1; }

# ── AI co-pilot wiring ──────────────────────────────────────────────────────
# Echoes detected co-pilots as "Name=binary" lines (one per line).
detect_copilots() {
    local first
    for first in claude;                          do have "$first" && { echo "Claude=$first";  break; }; done
    for first in copilot github-copilot-cli gh;   do have "$first" && { echo "Copilot=$first"; break; }; done
    for first in gemini;                          do have "$first" && { echo "Gemini=$first";  break; }; done
}

# Export the context so the Venus JVM (and any CLI it spawns for the in-UI AI
# panel) resolves the guardrails + skills + agents regardless of launch dir.
set_ai_context() {
    export VENUS_HOME="$SCRIPT_DIR"
    [ -f "$AGENTS_GUIDE" ] && export VENUS_AGENTS_GUIDE="$AGENTS_GUIDE"
    [ -d "$SKILLS_DIR" ]   && export VENUS_SKILLS_DIR="$SKILLS_DIR"
    [ -d "$AGENTS_DIR" ]   && export VENUS_AGENTS_DIR="$AGENTS_DIR"
    local names
    names=$(detect_copilots | cut -d= -f1 | tr '\n' ',' | sed 's/,$//' | tr 'A-Z' 'a-z')
    [ -n "$names" ] && export VENUS_AI_COPILOTS="$names"
}

show_copilots() {
    local names
    names=$(detect_copilots | cut -d= -f1 | paste -sd' · ' - 2>/dev/null)
    [ -z "$names" ] && names=$(detect_copilots | cut -d= -f1 | tr '\n' ' ')
    if [ -n "$names" ]; then
        dim  "  AI:      ${names}  (co-pilot ready)"
    else
        warn '  AI:      no CLI found  (install Claude, Copilot, or Gemini CLI for AI features)'
    fi
    if [ -f "$AGENTS_GUIDE" ] && [ -d "$SKILLS_DIR" ] && [ -d "$AGENTS_DIR" ]; then
        dim  '           guardrails AGENTS.md + skills/ + agents/ loaded -> run: ./venus.sh agents'
    else
        warn '           AGENTS.md / .claude skills+agents missing -- AI guardrails not wired'
    fi
}

check_java() {
    if ! have java; then
        err  '  ERROR: Java not found.'
        dim  '  Install JDK 17+ (21 recommended) from https://adoptium.net/'
        return 1
    fi
    return 0
}

check_mvn() {
    if ! have mvn; then
        err  '  ERROR: Maven not found.'
        dim  '  Install from https://maven.apache.org/'
        return 1
    fi
    return 0
}

server_up() {
    if have curl; then
        curl -s -o /dev/null -m 2 "$URL" >/dev/null 2>&1
    elif have wget; then
        wget -q -T 2 -O /dev/null "$URL" >/dev/null 2>&1
    else
        # Fallback: try /dev/tcp (bash builtin)
        (echo >"/dev/tcp/127.0.0.1/$PORT") >/dev/null 2>&1
    fi
}

listening_pid() {
    if have lsof; then
        lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null | head -n1
    elif have ss; then
        ss -ltnp 2>/dev/null | awk -v p=":$PORT" '$4 ~ p { print $0 }' \
            | grep -oE 'pid=[0-9]+' | head -n1 | cut -d= -f2
    elif have netstat; then
        # macOS / older Linux
        netstat -anv 2>/dev/null | awk -v p=".$PORT" '$4 ~ p && /LISTEN/ {print $9; exit}'
    fi
}

open_browser() {
    if   have xdg-open;     then xdg-open "$URL" >/dev/null 2>&1 &
    elif have open;         then open "$URL"  >/dev/null 2>&1 &
    elif have powershell.exe; then powershell.exe -NoProfile -Command "Start-Process '$URL'" >/dev/null 2>&1 &
    else dim "  Open $URL manually."
    fi
}

ensure_jar() {
    if [ ! -f "$JAR" ]; then
        warn '  JAR not found -- building first...'
        check_mvn || return 1
        mvn clean package -DskipTests -q || { err '  Build failed.'; return 1; }
        ok '  Build complete.'
    fi
    return 0
}

wait_for_server() {
    local timeout="${1:-30}"
    local elapsed=0
    while [ "$elapsed" -lt "$timeout" ]; do
        if server_up; then return 0; fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}

# ── Subcommands ─────────────────────────────────────────────────────────────
cmd_start() {
    banner

    if server_up; then
        ok   '  Venus Notebooks is already running.'
        info "  URL: $URL"
        printf '  Open in browser? [Y/n]: '
        read -r ans
        if [[ ! "$ans" =~ ^[nN]$ ]]; then open_browser; fi
        return 0
    fi

    check_java || return 1
    dim  "  Java:    $(java -version 2>&1 | head -n1)"

    if have node; then
        dim  "  Node.js: $(node --version)  (JavaScript/TypeScript cells enabled)"
    else
        warn '  Node.js: not found  (JS/TS cells disabled -- install from nodejs.org)'
    fi

    if have dotnet; then
        dim  "  .NET:    $(dotnet --version)  (C# and F# cells enabled)"
    else
        warn '  .NET:    not found  (C#/F# cells disabled -- install from https://dot.net)'
    fi

    # Wire the AI co-pilot context before launching the JVM so the in-UI AI
    # panel (and any CLI it spawns) inherits the guardrails + skills + agents.
    set_ai_context
    show_copilots

    ensure_jar || return 1

    local jvm_args=(
        --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED
        --add-opens=java.base/java.lang=ALL-UNNAMED
        --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED
        -jar "$JAR"
    )

    if [ "${BG:-0}" = "1" ]; then
        info '  Starting Venus Notebooks in background...'
        nohup java "${jvm_args[@]}" >venus.log 2>venus-err.log &
        local pid=$!
        ok   "  Started. PID: $pid   Logs: venus.log"
        dim  '  Waiting for server...'
        if wait_for_server 30; then
            ok '  Ready! Opening browser...'
            open_browser
            return 0
        else
            err '  Server did not respond after 30s -- check venus.log / venus-err.log'
            return 1
        fi
    fi

    ok  '  Launching Venus Notebooks...'
    dim '  Press Ctrl+C to stop'
    dim '  ──────────────────────────────────────'
    echo
    ( sleep 5 && server_up && open_browser ) &

    # Exit code 42 = restart requested by the UI (matches scripts/start.sh)
    while true; do
        java "${jvm_args[@]}"
        local code=$?
        if [ "$code" -eq 42 ]; then
            echo
            dim '  ──────────────────────────────────────'
            info '  Restarting Venus Notebooks...'
            dim '  ──────────────────────────────────────'
            sleep 1
            continue
        fi
        return "$code"
    done
}

cmd_stop() {
    echo
    warn '  Stopping Venus Notebooks...'
    local pid
    pid=$(listening_pid)
    if [ -z "$pid" ]; then
        err '  Venus Notebooks is not running.'
        return 1
    fi
    dim "  Killing PID $pid"
    if kill "$pid" 2>/dev/null; then
        sleep 1
        if server_up; then
            warn '  Process did not exit cleanly -- sending SIGKILL'
            kill -9 "$pid" 2>/dev/null
        fi
        ok '  Stopped.'
        return 0
    else
        err "  Failed to stop PID $pid"
        return 1
    fi
}

cmd_status() {
    echo
    if server_up; then
        ok  "  [RUNNING] Venus Notebooks is up at $URL"
        local pid; pid=$(listening_pid)
        [ -n "$pid" ] && dim "  PID: $pid"
    else
        err '  [STOPPED] Venus Notebooks is not running.'
    fi
    echo

    if [ -f "$JAR" ]; then
        dim "  JAR: $JAR (exists)"
    else
        warn "  JAR: not built yet -- run: ./venus.sh build"
    fi

    if have java; then
        dim  "  Java:     $(java -version 2>&1 | head -n1)"
    else
        err  '  Java:     NOT FOUND'
    fi

    if have node;   then dim  "  Node.js:  $(node --version)"
    else warn '  Node.js:  not found (JavaScript/TypeScript cells disabled)'; fi

    if have dotnet; then dim  "  .NET:     $(dotnet --version)"
    else warn '  .NET:     not found (C# / F# cells disabled)'; fi

    local ai_names
    ai_names=$(detect_copilots | cut -d= -f1 | tr '\n' ' ')
    if [ -n "${ai_names// }" ]; then
        dim  "  AI:       ${ai_names} (co-pilot ready -- run: ./venus.sh agents)"
    else
        warn '  AI:       no CLI found (Claude / Copilot / Gemini -- AI features limited)'
    fi
    echo
    return 0
}

cmd_welcome() {
    set_ai_context
    banner
    title '  Welcome to Venus Notebooks'
    dim   '  A local notebook for Java | JS | TS | C# | F# | C++ — with AI co-pilots and MCP.'
    echo
    info  '  PICK HOW YOU WANT TO WORK'
    dim   '  ──────────────────────────────────────'
    printf '    %s1) Open the UI%s            %sfull notebook experience in your browser%s\n' "$C_WHITE" "$C_RESET" "$C_DIM" "$C_RESET"
    dim   "         ./venus.sh start        ->  $URL"
    printf '    %s2) Drive Venus over MCP%s   %soperate & automate from any MCP client%s\n' "$C_WHITE" "$C_RESET" "$C_DIM" "$C_RESET"
    dim   "         SSE  $URL/api/mcp/sse"
    dim   "         POST $URL/api/mcp/messages"
    printf '    %s3) Personalize & extend%s   %sadd features — needs an agentic CLI%s\n' "$C_WHITE" "$C_RESET" "$C_DIM" "$C_RESET"
    dim   '         run  claude  /  copilot  /  gemini   in this folder, then ask the venus agent'
    echo
    local ai_names
    ai_names=$(detect_copilots | cut -d= -f1 | tr '\n' ' ')
    if [ -n "${ai_names// }" ]; then
        ok   "  AI co-pilots ready: ${ai_names}  (run: ./venus.sh agents)"
    else
        warn '  No AI CLI found — install Claude, Copilot, or Gemini to personalize Venus.'
    fi
    echo
    info  '  THE ONE DIFFERENCE'
    dim   '  ──────────────────────────────────────'
    dim   '    This venus CLI operates & automates Venus (incl. MCP) but cannot change its code.'
    dim   '    An agentic CLI (claude / copilot / gemini) can ALSO personalize and extend Venus.'
    echo
    info  '  NEXT'
    dim   '  ──────────────────────────────────────'
    echo  '    ./venus.sh start      Start the server and open the UI'
    echo  '    ./venus.sh docs       Open the brochure and list the docs'
    echo  '    ./venus.sh agents     AI co-pilots, skills & the venus agent'
    echo
    dim   '  Full welcome: docs/WELCOME.md'
    echo
    return 0
}

cmd_docs() {
    echo
    title '  Venus Notebooks — Documentation'
    dim   '  ──────────────────────────────────────'
    echo  '    Brochure (PDF) docs/brochure/venus-brochure.pdf'
    echo  '    Welcome        docs/WELCOME.md'
    echo  '    Getting started README.md'
    echo  '    Architecture   docs/ARCHITECTURE.md'
    echo  '    API + MCP      docs/API.md'
    echo  '    Contributor    CONTRIBUTING.md  +  AGENTS.md'
    echo  '    Cheat sheet    docs/cheatsheet.html'
    echo
    if server_up; then dim "    In the running app: open the in-UI docs overlay at $URL"; fi
    local pdf="$SCRIPT_DIR/docs/brochure/venus-brochure.pdf"
    if [ -f "$pdf" ]; then
        ok '    Opening the brochure...'
        if   have xdg-open; then xdg-open "$pdf" >/dev/null 2>&1 &
        elif have open;     then open "$pdf"     >/dev/null 2>&1 &
        else dim "    Open manually: $pdf"; fi
    else
        warn '    Brochure PDF not found — open docs/brochure/venus-brochure.html in a browser.'
    fi
    echo
    return 0
}

cmd_agents() {
    set_ai_context
    echo
    printf '%s        .         %s%s  Venus AI Co-pilots, Skills & Agents%s\n' "$C_YELLOW" "$C_RESET" "$C_WHITE" "$C_RESET"
    printf '%s       /|\\        %s%s  ──────────────────────────────────────%s\n' "$C_YELLOW" "$C_RESET" "$C_DIM" "$C_RESET"
    printf '%s      ( @ )       %s\n' "$C_YELLOW" "$C_RESET"
    echo

    info '  DETECTED AI CLIs'
    dim  '  ──────────────────────────────────────'
    local found
    found=$(detect_copilots)
    if [ -n "$found" ]; then
        while IFS='=' read -r name bin; do
            [ -n "$name" ] && ok "    [ok] $name  (binary: $bin)"
        done <<< "$found"
    else
        warn '    none found -- install one:'
        dim  '      Claude :  https://claude.ai/code           then  claude auth'
        dim  '      Copilot:  npm i -g @githubnext/github-copilot-cli'
        dim  '      Gemini :  npm i -g @google/gemini-cli       then  gemini auth'
    fi
    echo

    info '  GUARDRAILS (read automatically by every AI CLI in this repo)'
    dim  '  ──────────────────────────────────────'
    for pair in "AGENTS.md|$AGENTS_GUIDE" ".claude/skills|$SKILLS_DIR" ".claude/agents|$AGENTS_DIR" \
                "CLAUDE.md|$SCRIPT_DIR/CLAUDE.md" ".github/copilot-instructions.md|$SCRIPT_DIR/.github/copilot-instructions.md" \
                "GEMINI.md|$SCRIPT_DIR/GEMINI.md"; do
        label="${pair%%|*}"; path="${pair#*|}"
        if [ -e "$path" ]; then ok "    [ok] $label"; else warn "    [--] $label  (missing)"; fi
    done
    echo

    info '  SKILLS (auto-invoke when your request matches)'
    dim  '  ──────────────────────────────────────'
    if [ -d "$SKILLS_DIR" ]; then
        for d in "$SKILLS_DIR"/*/; do [ -d "$d" ] && dim "    - $(basename "$d")"; done
    fi
    echo
    info '  SUBAGENTS (spawn explicitly for a focused review)'
    dim  '  ──────────────────────────────────────'
    if [ -d "$AGENTS_DIR" ]; then
        for f in "$AGENTS_DIR"/*.md; do [ -f "$f" ] && dim "    - $(basename "$f" .md)"; done
    fi
    echo

    info '  HOW TO USE'
    dim  '  ──────────────────────────────────────'
    echo "    In the Venus UI : open the AI panel (Ctrl+\\), pick a provider, ask away."
    echo "    In a terminal   : run your CLI from this folder so it loads AGENTS.md:"
    dim  "                        claude        (or)   copilot        (or)   gemini"
    echo '    Example prompt  : "Add a Kotlin execution mode following CppExecutionService,'
    dim  '                       then run ./scripts/security-check.sh and open a PR."'
    echo
    dim  '    Env exported for this session:'
    dim  "      VENUS_HOME=${VENUS_HOME:-}"
    dim  "      VENUS_AGENTS_GUIDE=${VENUS_AGENTS_GUIDE:-}"
    dim  "      VENUS_SKILLS_DIR=${VENUS_SKILLS_DIR:-}"
    dim  "      VENUS_AGENTS_DIR=${VENUS_AGENTS_DIR:-}"
    echo
    return 0
}

cmd_build() {
    banner
    info '  Building Venus Notebooks...'
    dim  '  ──────────────────────────────────────'
    check_mvn || return 1
    if mvn clean package -DskipTests; then
        echo
        ok   '  Build successful!'
        info '  Run: ./venus.sh start'
        return 0
    else
        echo
        err  '  Build failed. Check output above.'
        return 1
    fi
}

cmd_rebuild() {
    banner
    warn '  Rebuilding Venus Notebooks (force)...'
    dim  '  ──────────────────────────────────────'
    check_mvn || return 1
    if mvn clean package -DskipTests; then
        echo
        ok '  Rebuild successful!'
        return 0
    else
        echo
        err '  Rebuild failed. Check output above.'
        return 1
    fi
}

cmd_open() {
    if server_up; then
        ok '  Opening Venus Notebooks...'
        open_browser
        return 0
    else
        err '  Venus Notebooks is not running. Start it first: ./venus.sh start'
        return 1
    fi
}

cmd_logs() {
    if [ ! -f venus.log ]; then
        err '  No log file found (venus.log).'
        dim '  Logs are only written in background mode: ./venus.sh start --bg'
        return 1
    fi
    info '  Tailing venus.log (Ctrl+C to stop)...'
    dim  '  ──────────────────────────────────────'
    tail -n 40 -f venus.log
}

cmd_version() {
    echo
    title '  Venus Notebooks'
    if [ -f pom.xml ]; then
        local v
        v=$(grep -E '<version>' pom.xml \
            | grep -vE '(parent|spring|junit|jackson|boot)' \
            | head -n1 | sed -e 's/^[[:space:]]*//')
        [ -n "$v" ] && dim "  Version:  $v"
    fi
    have java   && dim "  Java:     $(java -version 2>&1 | head -n1)"
    if have node;   then dim "  Node.js:  $(node --version)"
    else warn '  Node.js:  not installed'; fi
    have dotnet && dim "  .NET:     $(dotnet --version)"
    if have mvn; then
        local m
        m=$(mvn --version 2>&1 | grep 'Apache Maven' | head -n1)
        [ -n "$m" ] && dim "  Maven:    $m"
    fi
    echo
}

cmd_help() {
    echo
    printf '%s        .         %s%s  Venus Notebooks CLI (bash)%s\n' "$C_YELLOW" "$C_RESET" "$C_WHITE" "$C_RESET"
    printf '%s       /|\\        %s%s  ──────────────────────────────────────%s\n' "$C_YELLOW" "$C_RESET" "$C_DIM" "$C_RESET"
    printf '%s      ( @ )       %s%s  Java | JS | TS | C# | F# | C++%s\n' "$C_YELLOW" "$C_RESET" "$C_CYAN" "$C_RESET"
    printf '%s     /|\\_/|\\      %s\n' "$C_YELLOW" "$C_RESET"
    printf '%s    / |   | \\     %s%s  USAGE%s\n' "$C_YELLOW" "$C_RESET" "$C_CYAN" "$C_RESET"
    printf '%s   /__|   |__\\    %s%s    ./venus.sh [command] [--bg]%s\n' "$C_YELLOW" "$C_RESET" "$C_DIM" "$C_RESET"
    dim '  ───────────────'
    echo
    info '  COMMANDS'
    dim  '  ──────────────────────────────────────'
    echo "    start            Start server (auto-build, auto-open browser)"
    echo "    start --bg       Start in background, write logs to venus.log"
    echo "    stop             Stop a running server"
    echo "    status           Show server state, PID, Java, Node.js, .NET, JAR"
    echo "    build            Compile and package (skips if JAR exists)"
    echo "    rebuild          Force clean build"
    echo "    open             Open browser (server must already be running)"
    echo "    logs             Tail venus.log (background mode only)"
    echo "    version          Show Java, Node.js, .NET, Maven, project version"
    echo "    welcome          Common welcome — open the UI, use MCP, or personalize Venus"
    echo "    docs             Open the brochure and list the documentation"
    echo "    agents           Show AI co-pilots, skills & the venus agent wired into this repo"
    echo "    help             Show this help"
    echo
    info '  EXAMPLES'
    dim  '  ──────────────────────────────────────'
    echo "    ./venus.sh                   (same as: ./venus.sh start)"
    echo "    ./venus.sh start --bg"
    echo "    ./venus.sh stop"
    echo "    ./venus.sh status"
    echo
    info "  URL: $URL"
    echo
}

# ── Dispatch ────────────────────────────────────────────────────────────────
CMD="${1:-start}"
shift || true

# Honour --bg / -b on any subcommand
BG=0
for arg in "$@"; do
    case "$arg" in
        --bg|-b) BG=1 ;;
    esac
done
export BG

case "${CMD,,}" in
    start)              cmd_start ;;
    stop)               cmd_stop ;;
    status)             cmd_status ;;
    build)              cmd_build ;;
    rebuild)            cmd_rebuild ;;
    open)               cmd_open ;;
    logs)               cmd_logs ;;
    version|--version)  cmd_version ;;
    agents|ai)          cmd_agents ;;
    welcome)            cmd_welcome ;;
    docs)               cmd_docs ;;
    help|-h|--help)     cmd_help ;;
    *)
        err "Unknown command: $CMD"
        dim 'Run: ./venus.sh help'
        exit 1 ;;
esac
exit $?
