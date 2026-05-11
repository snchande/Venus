@echo off
REM ================================================================
REM  Venus Notebooks — Double-click Launcher
REM  Opens http://localhost:8585 and starts the server
REM ================================================================
title Venus Notebooks

cd /d "%~dp0\.."

REM Check if server is already running
curl -s -o nul http://localhost:8585 >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo Venus Notebooks is already running.
    start "" "http://localhost:8585"
    exit /b 0
)

REM Check Java
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java not found. Please install JDK 17 or higher.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check Node.js (optional — JS cells only)
node --version >nul 2>&1
if %ERRORLEVEL% == 0 (
    for /f "tokens=*" %%v in ('node --version') do echo Node.js %%v detected -- JavaScript cells enabled.
) else (
    echo Note: Node.js not found. JavaScript cells will be disabled.
    echo       Install from: https://nodejs.org/
)

set JAR=target\venus-notebooks-1.0.0-SNAPSHOT.jar

REM Build if needed
if not exist "%JAR%" (
    echo Building Venus Notebooks (first time only)...
    where mvn >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo ERROR: Maven not found. Please install Apache Maven.
        echo Download from: https://maven.apache.org/
        pause
        exit /b 1
    )
    mvn clean package -DskipTests -q
    if %ERRORLEVEL% neq 0 (
        echo ERROR: Build failed.
        pause
        exit /b 1
    )
)

REM Open browser (slight delay so server can start first)
ping -n 4 127.0.0.1 >nul
start "" "http://localhost:8585"

echo Venus Notebooks is starting...
echo URL: http://localhost:8585
echo.
echo Close this window or press Ctrl+C to stop the server.
echo ----------------------------------------------------------------

java ^
    --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED ^
    --add-opens=java.base/java.lang=ALL-UNNAMED ^
    --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED ^
    -jar "%JAR%"
