#!/bin/bash
# Venus Notebooks — Unix/Mac startup script
# Exit code 42 from the JVM means "restart requested" — loop handles that automatically.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR="$PROJECT_DIR/target/venus-notebooks-1.0.0-SNAPSHOT.jar"

cd "$PROJECT_DIR"

echo "======================================="
echo "  Venus Notebooks v1.0"
echo "  Java | JavaScript | C# | F# | JShell"
echo "======================================="
echo ""

# ── Dependency checks ──────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
    echo "ERROR: Java not found. Please install JDK 17+."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | grep -oP '(?<=version ")[0-9]+' | head -1)
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -lt "17" ] 2>/dev/null; then
    echo "ERROR: Java $JAVA_VER found, but JDK 17+ is required."
    exit 1
fi
echo "Java:    $(java -version 2>&1 | head -1)"

if command -v node &>/dev/null; then
    echo "Node.js: $(node --version)  (JavaScript cells enabled)"
else
    echo "Node.js: not found  (JS cells disabled — install from nodejs.org)"
fi

if command -v dotnet &>/dev/null; then
    echo ".NET:    $(dotnet --version)  (C# and F# cells enabled)"
else
    echo ".NET:    not found  (C#/F# cells disabled — install from https://dot.net)"
fi
echo ""

# ── Build if JAR is missing ────────────────────────────────────────────────────
if [ ! -f "$JAR" ]; then
    if ! command -v mvn &>/dev/null; then
        echo "ERROR: Maven not found. Please install Maven 3.8+."
        exit 1
    fi
    echo "Building Venus Notebooks..."
    mvn clean package -DskipTests -q
fi

echo "Starting Venus Notebooks..."
echo "Open http://localhost:8585 in your browser"
echo ""
echo "Press Ctrl+C to stop   |   Use Restart in the UI to apply code changes"
echo "---------------------------------------"

# ── Watchdog loop ──────────────────────────────────────────────────────────────
# Exit code 42 = restart requested (from UI Restart button).
# Any other exit code breaks the loop and terminates normally.
RESTART_CODE=42

while true; do
    java \
        --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED \
        -jar "$JAR" "$@"

    EXIT_CODE=$?

    if [ "$EXIT_CODE" -eq "$RESTART_CODE" ]; then
        echo ""
        echo "---------------------------------------"
        echo "  Restarting Venus Notebooks..."
        echo "---------------------------------------"
        sleep 1
        # Re-run any pending Maven build if sources changed
        if [ "$VENUS_AUTO_BUILD" = "1" ] && command -v mvn &>/dev/null; then
            echo "  Auto-building before restart..."
            mvn package -DskipTests -q
        fi
        continue
    fi

    # Normal exit (0) or error — stop looping
    if [ "$EXIT_CODE" -ne 0 ]; then
        echo ""
        echo "Venus Notebooks exited with code $EXIT_CODE."
    fi
    break
done
