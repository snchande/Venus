@echo off
REM Venus Notebooks — Windows startup script
REM Exit code 42 from the JVM means "restart requested"; this script loops automatically.

setlocal enabledelayedexpansion

cd /d "%~dp0\.."

echo =======================================
echo   Venus Notebooks v1.0
echo   Java ^| JavaScript ^| C# ^| F# ^| JShell
echo =======================================
echo.

REM ── Dependency checks ──────────────────────────────────────────────────────
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java not found. Please install JDK 17 or higher.
    pause
    exit /b 1
)
echo Java:
java -version 2>&1 | findstr /C:"version"

node --version >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo Node.js: (JavaScript cells enabled^)
    node --version
) else (
    echo Node.js: not found -- JS cells disabled
)

dotnet --version >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo .NET: (C# and F# cells enabled^)
    dotnet --version
) else (
    echo .NET: not found -- C#/F# cells disabled (install from https://dot.net^)
)
echo.

REM ── Build if JAR missing ───────────────────────────────────────────────────
set JAR=target\venus-notebooks-1.0.0-SNAPSHOT.jar

if not exist "%JAR%" (
    echo Building Venus Notebooks...
    mvn clean package -DskipTests -q
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Build failed. Make sure Maven is installed.
        pause
        exit /b 1
    )
)

echo Starting Venus Notebooks...
echo Open http://localhost:8585 in your browser
echo.
echo Press Ctrl+C to stop   ^|   Use Restart in the UI to apply code changes
echo ---------------------------------------

REM ── Watchdog loop ─────────────────────────────────────────────────────────
REM Exit code 42 = restart requested from the UI.
:LOOP
java ^
    --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED ^
    --add-opens=java.base/java.lang=ALL-UNNAMED ^
    --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED ^
    -jar "%JAR%" %*

set EXIT_CODE=!ERRORLEVEL!

if !EXIT_CODE! == 42 (
    echo.
    echo ---------------------------------------
    echo   Restarting Venus Notebooks...
    echo ---------------------------------------
    timeout /t 1 /nobreak >nul
    if "%VENUS_AUTO_BUILD%"=="1" (
        echo   Auto-building before restart...
        mvn package -DskipTests -q
    )
    goto LOOP
)

if !EXIT_CODE! neq 0 (
    echo.
    echo Venus Notebooks exited with code !EXIT_CODE!.
    pause
)

endlocal
