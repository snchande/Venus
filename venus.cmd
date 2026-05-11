@echo off
setlocal enabledelayedexpansion
title Venus Notebooks CLI

REM ================================================================
REM  Venus Notebooks - Command Line Interface
REM  Usage: venus [command] [options]
REM ================================================================

cd /d "%~dp0"

REM Set ESC character for ANSI colors (Windows 10 1511+)
for /f %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

REM Parse command
set "CMD=%~1"
if "%CMD%"=="" set "CMD=start"

REM Dispatch commands
if /i "%CMD%"=="start"   goto :cmd_start
if /i "%CMD%"=="stop"    goto :cmd_stop
if /i "%CMD%"=="status"  goto :cmd_status
if /i "%CMD%"=="build"   goto :cmd_build
if /i "%CMD%"=="rebuild" goto :cmd_rebuild
if /i "%CMD%"=="open"    goto :cmd_open
if /i "%CMD%"=="logs"    goto :cmd_logs
if /i "%CMD%"=="help"    goto :cmd_help
if /i "%CMD%"=="-h"      goto :cmd_help
if /i "%CMD%"=="--help"  goto :cmd_help
if /i "%CMD%"=="version" goto :cmd_version

echo Unknown command: %CMD%
echo Run: venus help
exit /b 1


REM ================================================================
REM  BANNER  (Venus character + title)
REM  NOTE: pipe chars ^ escaped as ^| to prevent CMD treating as pipe
REM ================================================================
:banner
echo.
echo !ESC![93m        .        !ESC![0m!ESC![97m  V E N U S   N O T E B O O K S!ESC![0m
echo !ESC![93m       /^|\       !ESC![0m!ESC![90m  ─────────────────────────────!ESC![0m
echo !ESC![93m      ( @ )      !ESC![0m!ESC![96m  Java  ^|  JavaScript  ^|  JShell!ESC![0m
echo !ESC![93m     /^|\_/^|\     !ESC![0m!ESC![90m  Powered by JShell + Node.js + Spring Boot!ESC![0m
echo !ESC![93m    / ^|   ^| \    !ESC![0m!ESC![90m  Port: 8585!ESC![0m
echo !ESC![93m   /__^|   ^|__\   !ESC![0m
echo !ESC![90m  ───────────────!ESC![0m
echo.
goto :eof


REM ================================================================
REM  START
REM ================================================================
:cmd_start
call :banner

REM Check if already running
curl -s -o nul http://localhost:8585 >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo !ESC![92m  Venus Notebooks is already running!!ESC![0m
    echo !ESC![96m  URL: http://localhost:8585!ESC![0m
    echo.
    set /p "OPEN=  Open in browser? [Y/n]: "
    if /i not "!OPEN!"=="n" start "" "http://localhost:8585"
    exit /b 0
)

REM Check Java
call :check_java
if %ERRORLEVEL% neq 0 exit /b 1

REM Check Node.js (optional — JS cells only)
node --version >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('node --version') do echo !ESC![90m  Node.js:  %%v (JavaScript cells enabled)!ESC![0m
) else (
    echo !ESC![93m  Node.js:  not found -- JS cells disabled (install from nodejs.org to enable)!ESC![0m
)

REM Check/build JAR
call :ensure_jar
if %ERRORLEVEL% neq 0 exit /b 1

REM Launch mode: --bg runs detached, default is foreground
if /i "%~2"=="--bg" goto :start_bg

echo !ESC![92m  Launching Venus Notebooks...!ESC![0m
echo !ESC![90m  Press Ctrl+C to stop!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
echo.

REM Open browser after short delay (background ping trick)
start "" cmd /c "ping -n 5 127.0.0.1 >nul && start http://localhost:8585"

java --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED -jar "target\venus-notebooks-1.0.0-SNAPSHOT.jar"
exit /b %ERRORLEVEL%

:start_bg
echo !ESC![96m  Starting Venus Notebooks in background...!ESC![0m
start /b "" java --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED -jar "target\venus-notebooks-1.0.0-SNAPSHOT.jar" > venus.log 2>&1
echo !ESC![92m  Started. Logs: venus.log!ESC![0m
echo !ESC![90m  Waiting for server...!ESC![0m
call :wait_for_server
echo !ESC![92m  Ready! Opening browser...!ESC![0m
start "" "http://localhost:8585"
exit /b 0


REM ================================================================
REM  STOP
REM ================================================================
:cmd_stop
echo.
echo !ESC![93m  Stopping Venus Notebooks...!ESC![0m
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8585 " ^| findstr "LISTENING"') do (
    echo !ESC![90m  Killing PID %%p!ESC![0m
    taskkill /PID %%p /F >nul 2>&1
    echo !ESC![92m  Stopped.!ESC![0m
    exit /b 0
)
echo !ESC![91m  Venus Notebooks is not running.!ESC![0m
exit /b 1


REM ================================================================
REM  STATUS
REM ================================================================
:cmd_status
echo.
curl -s -o nul http://localhost:8585 >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo !ESC![92m  [RUNNING]!ESC![0m Venus Notebooks is up at http://localhost:8585
    for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8585 " ^| findstr "LISTENING"') do (
        echo !ESC![90m  PID: %%p!ESC![0m
    )
) else (
    echo !ESC![91m  [STOPPED]!ESC![0m Venus Notebooks is not running.
)
echo.

set "JAR=target\venus-notebooks-1.0.0-SNAPSHOT.jar"
if exist "%JAR%" (
    echo !ESC![90m  JAR: %JAR% (exists)!ESC![0m
) else (
    echo !ESC![93m  JAR: not built yet -- run: venus build!ESC![0m
)

java -version >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr "version"') do echo !ESC![90m  Java:     %%v!ESC![0m
) else (
    echo !ESC![91m  Java:     NOT FOUND!ESC![0m
)
node --version >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('node --version') do echo !ESC![90m  Node.js:  %%v (JavaScript cells enabled)!ESC![0m
) else (
    echo !ESC![93m  Node.js:  not found (JavaScript cells disabled -- install from nodejs.org)!ESC![0m
)
echo.
exit /b 0


REM ================================================================
REM  BUILD
REM ================================================================
:cmd_build
call :banner
echo !ESC![96m  Building Venus Notebooks...!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
call :check_mvn
if %ERRORLEVEL% neq 0 exit /b 1
mvn clean package -DskipTests
if %ERRORLEVEL% == 0 (
    echo.
    echo !ESC![92m  Build successful!!ESC![0m
    echo !ESC![96m  Run: venus start!ESC![0m
) else (
    echo.
    echo !ESC![91m  Build failed. Check output above.!ESC![0m
)
exit /b %ERRORLEVEL%


REM ================================================================
REM  REBUILD  (force rebuild even if JAR exists)
REM ================================================================
:cmd_rebuild
call :banner
echo !ESC![93m  Rebuilding Venus Notebooks (force)...!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
call :check_mvn
if %ERRORLEVEL% neq 0 exit /b 1
mvn clean package -DskipTests
if %ERRORLEVEL% == 0 (
    echo.
    echo !ESC![92m  Rebuild successful!!ESC![0m
) else (
    echo.
    echo !ESC![91m  Rebuild failed. Check output above.!ESC![0m
)
exit /b %ERRORLEVEL%


REM ================================================================
REM  OPEN  (open browser without starting server)
REM ================================================================
:cmd_open
curl -s -o nul http://localhost:8585 >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo !ESC![92m  Opening Venus Notebooks...!ESC![0m
    start "" "http://localhost:8585"
) else (
    echo !ESC![91m  Venus Notebooks is not running. Start it first: venus start!ESC![0m
    exit /b 1
)
exit /b 0


REM ================================================================
REM  LOGS  (tail the venus.log if running in bg mode)
REM ================================================================
:cmd_logs
if not exist "venus.log" (
    echo !ESC![91m  No log file found (venus.log).!ESC![0m
    echo !ESC![90m  Logs are only written in background mode: venus start --bg!ESC![0m
    exit /b 1
)
echo !ESC![96m  Tailing venus.log (Ctrl+C to stop)...!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
powershell -Command "Get-Content -Path 'venus.log' -Wait -Tail 40"
exit /b 0


REM ================================================================
REM  VERSION
REM ================================================================
:cmd_version
echo.
echo !ESC![97m  Venus Notebooks!ESC![0m
if exist "pom.xml" (
    for /f "tokens=*" %%a in ('findstr "<version>" pom.xml ^| findstr /v "parent spring junit jackson boot"') do (
        echo !ESC![90m  Version:  %%a!ESC![0m
        goto :after_ver
    )
    :after_ver
)
for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr "version"') do echo !ESC![90m  Java:     %%v!ESC![0m
node --version >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('node --version') do echo !ESC![90m  Node.js:  %%v!ESC![0m
) else (
    echo !ESC![93m  Node.js:  not installed!ESC![0m
)
where mvn >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('mvn --version 2^>^&1 ^| findstr "Apache Maven"') do echo !ESC![90m  Maven:    %%v!ESC![0m
)
echo.
exit /b 0


REM ================================================================
REM  HELP
REM ================================================================
:cmd_help
echo.
echo !ESC![93m        .        !ESC![0m!ESC![97m  Venus Notebooks CLI!ESC![0m
echo !ESC![93m       /^|\       !ESC![0m!ESC![90m  ──────────────────────────────────────!ESC![0m
echo !ESC![93m      ( @ )      !ESC![0m!ESC![96m  Java  ^|  JavaScript  ^|  JShell!ESC![0m
echo !ESC![93m     /^|\_/^|\     !ESC![0m
echo !ESC![93m    / ^|   ^| \    !ESC![0m!ESC![96m  USAGE!ESC![0m
echo !ESC![93m   /__^|   ^|__\   !ESC![0m!ESC![90m    venus [command] [options]!ESC![0m
echo !ESC![90m  ───────────────!ESC![0m
echo.
echo !ESC![96m  COMMANDS!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
echo    !ESC![97mstart!ESC![0m             Start server (auto-build, auto-open browser)
echo    !ESC![97mstart --bg!ESC![0m        Start in background, write logs to venus.log
echo    !ESC![97mstop!ESC![0m              Stop a running server
echo    !ESC![97mstatus!ESC![0m            Show server state, PID, Java, Node.js, JAR
echo    !ESC![97mbuild!ESC![0m             Compile and package (skips if JAR exists)
echo    !ESC![97mrebuild!ESC![0m           Force clean build
echo    !ESC![97mopen!ESC![0m              Open browser (server must already be running)
echo    !ESC![97mlogs!ESC![0m              Tail venus.log (background mode only)
echo    !ESC![97mversion!ESC![0m           Show Java, Node.js, Maven, and project version
echo    !ESC![97mhelp!ESC![0m              Show this help
echo.
echo !ESC![96m  CELL MODES!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
echo    !ESC![36m◈ JShell!ESC![0m    Java snippets, shared session state (default)
echo    !ESC![36m◈ Java!ESC![0m      Full compile-and-run, isolated per cell
echo    !ESC![32m◈ JS!ESC![0m        Node.js JavaScript, isolated per cell
echo    Click the mode button on any cell to cycle: JShell -^> Java -^> JS
echo.
echo !ESC![96m  NPM PACKAGES (for JS cells)!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
echo    Install via Packages tab -^> npm section in the browser UI
echo    Popular: simple-statistics, mathjs, d3-array, lodash, axios
echo.
echo !ESC![96m  EXAMPLES!ESC![0m
echo !ESC![90m  ──────────────────────────────────────!ESC![0m
echo    venus                   (same as: venus start)
echo    venus start             Start foreground, auto-open browser
echo    venus start --bg        Start in background
echo    venus stop              Kill the server
echo    venus status            Check server + Java + Node.js
echo    venus rebuild           Force rebuild then: venus start
echo    venus logs              Stream background logs
echo    venus version           Show all tool versions
echo.
echo !ESC![96m  URL!ESC![0m
echo    http://localhost:8585
echo.
exit /b 0


REM ================================================================
REM  HELPERS
REM ================================================================

:check_java
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo !ESC![91m  ERROR: Java not found.!ESC![0m
    echo !ESC![90m  Install JDK 21+ from: https://adoptium.net/!ESC![0m
    exit /b 1
)
for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr "version"') do echo !ESC![90m  Java: %%v!ESC![0m
exit /b 0

:check_mvn
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo !ESC![91m  ERROR: Maven not found.!ESC![0m
    echo !ESC![90m  Install from: https://maven.apache.org/!ESC![0m
    exit /b 1
)
exit /b 0

:ensure_jar
set "JAR=target\venus-notebooks-1.0.0-SNAPSHOT.jar"
if not exist "%JAR%" (
    echo !ESC![93m  JAR not found -- building first...!ESC![0m
    call :check_mvn
    if %ERRORLEVEL% neq 0 exit /b 1
    mvn clean package -DskipTests -q
    if %ERRORLEVEL% neq 0 (
        echo !ESC![91m  Build failed.!ESC![0m
        exit /b 1
    )
    echo !ESC![92m  Build complete.!ESC![0m
)
exit /b 0

:wait_for_server
set /a TRIES=0
:wait_loop
ping -n 2 127.0.0.1 >nul
curl -s -o nul http://localhost:8585 >nul 2>&1
if %ERRORLEVEL% == 0 exit /b 0
set /a TRIES+=1
if %TRIES% lss 15 goto :wait_loop
echo !ESC![91m  Server did not respond after 30s -- check venus.log!ESC![0m
exit /b 1
