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
    help|-h|--help)     cmd_help ;;
    *)
        err "Unknown command: $CMD"
        dim 'Run: ./venus.sh help'
        exit 1 ;;
esac
exit $?
