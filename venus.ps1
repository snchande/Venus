#Requires -Version 5.1
<#
.SYNOPSIS
  Venus Notebooks PowerShell CLI.

.DESCRIPTION
  Cross-platform PowerShell launcher for Venus Notebooks. Mirrors venus.cmd
  (Windows CMD) and venus.sh (Linux/macOS). Subcommands:
    start [-Bg]   stop   status   build   rebuild   open   logs   version   help

.EXAMPLE
  ./venus.ps1                     # same as: ./venus.ps1 start
  ./venus.ps1 start -Bg           # background, writes venus.log
  ./venus.ps1 stop
  ./venus.ps1 status
  ./venus.ps1 help
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string] $Command = 'start',

    [switch] $Bg
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Resolve repo root (directory holding this script)
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $RepoRoot

$JarPath = Join-Path 'target' 'venus-notebooks-1.0.0-SNAPSHOT.jar'
$Port    = 8585
$Url     = "http://localhost:$Port"

# ── Colour helpers ──────────────────────────────────────────────────────────
function W-Title  ($t) { Write-Host $t -ForegroundColor White }
function W-Info   ($t) { Write-Host $t -ForegroundColor Cyan }
function W-Ok     ($t) { Write-Host $t -ForegroundColor Green }
function W-Warn   ($t) { Write-Host $t -ForegroundColor Yellow }
function W-Err    ($t) { Write-Host $t -ForegroundColor Red }
function W-Dim    ($t) { Write-Host $t -ForegroundColor DarkGray }

function Show-Banner {
    Write-Host ''
    Write-Host '        .         ' -ForegroundColor Yellow -NoNewline; W-Title  '  V E N U S   N O T E B O O K S'
    Write-Host '       /|\        ' -ForegroundColor Yellow -NoNewline; W-Dim    '  -----------------------------'
    Write-Host '      ( @ )       ' -ForegroundColor Yellow -NoNewline; W-Info   '  Java | JS | TS | C# | F# | C++'
    Write-Host '     /|\_/|\      ' -ForegroundColor Yellow -NoNewline; W-Dim    '  Powered by JShell + Spring Boot'
    Write-Host '    / |   | \     ' -ForegroundColor Yellow -NoNewline; W-Dim    "  Port: $Port"
    Write-Host '   /__|   |__\    ' -ForegroundColor Yellow
    W-Dim '  -----------------------------'
    Write-Host ''
}

# ── Probes ──────────────────────────────────────────────────────────────────
function Test-Java {
    try {
        $null = Get-Command java -ErrorAction Stop
        return $true
    } catch {
        W-Err  '  ERROR: Java not found.'
        W-Dim  '  Install JDK 17+ (21 recommended) from https://adoptium.net/'
        return $false
    }
}

function Test-Maven {
    try {
        $null = Get-Command mvn -ErrorAction Stop
        return $true
    } catch {
        W-Err  '  ERROR: Maven not found.'
        W-Dim  '  Install from https://maven.apache.org/'
        return $false
    }
}

function Test-ServerUp {
    try {
        $r = Invoke-WebRequest -Uri $Url -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

function Get-ListeningPid {
    try {
        $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction Stop |
                Select-Object -First 1
        return $conn.OwningProcess
    } catch {
        return $null
    }
}

function Ensure-Jar {
    if (-not (Test-Path $JarPath)) {
        W-Warn '  JAR not found -- building first...'
        if (-not (Test-Maven)) { return $false }
        mvn clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            W-Err '  Build failed.'
            return $false
        }
        W-Ok  '  Build complete.'
    }
    return $true
}

function Wait-Server {
    param([int] $TimeoutSec = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-ServerUp) { return $true }
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

# ── Subcommands ─────────────────────────────────────────────────────────────
function Cmd-Start {
    Show-Banner

    if (Test-ServerUp) {
        W-Ok   '  Venus Notebooks is already running.'
        W-Info "  URL: $Url"
        $ans = Read-Host '  Open in browser? [Y/n]'
        if ($ans -notmatch '^[nN]') { Start-Process $Url }
        return 0
    }

    if (-not (Test-Java)) { return 1 }
    $jv = (& java -version 2>&1 | Select-Object -First 1)
    W-Dim "  Java:    $jv"

    if (Get-Command node -ErrorAction SilentlyContinue) {
        W-Dim "  Node.js: $(node --version)  (JavaScript/TypeScript cells enabled)"
    } else {
        W-Warn '  Node.js: not found  (JS/TS cells disabled -- install from nodejs.org)'
    }

    if (Get-Command dotnet -ErrorAction SilentlyContinue) {
        W-Dim  "  .NET:    $(dotnet --version)  (C# and F# cells enabled)"
    } else {
        W-Warn '  .NET:    not found  (C#/F# cells disabled -- install from https://dot.net)'
    }

    if (-not (Ensure-Jar)) { return 1 }

    $jvmArgs = @(
        '--add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED',
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED',
        '-jar', $JarPath
    )

    if ($Bg) {
        W-Info '  Starting Venus Notebooks in background...'
        $logPath = Join-Path $RepoRoot 'venus.log'
        $errPath = Join-Path $RepoRoot 'venus-err.log'
        $proc = Start-Process -FilePath java -ArgumentList $jvmArgs `
                -RedirectStandardOutput $logPath -RedirectStandardError $errPath `
                -WindowStyle Hidden -PassThru
        W-Ok   "  Started. PID: $($proc.Id)   Logs: venus.log"
        W-Dim  '  Waiting for server...'
        if (Wait-Server -TimeoutSec 30) {
            W-Ok  '  Ready! Opening browser...'
            Start-Process $Url
            return 0
        } else {
            W-Err '  Server did not respond after 30s -- check venus.log / venus-err.log'
            return 1
        }
    } else {
        W-Ok  '  Launching Venus Notebooks...'
        W-Dim '  Press Ctrl+C to stop'
        W-Dim '  --------------------------------------'
        Write-Host ''
        # Open browser after a short delay so the server has time to bind
        Start-Job -ScriptBlock {
            param($url)
            Start-Sleep -Seconds 5
            Start-Process $url
        } -ArgumentList $Url | Out-Null

        & java @jvmArgs
        return $LASTEXITCODE
    }
}

function Cmd-Stop {
    Write-Host ''
    W-Warn '  Stopping Venus Notebooks...'
    $procId = Get-ListeningPid
    if (-not $procId) {
        W-Err '  Venus Notebooks is not running.'
        return 1
    }
    W-Dim  "  Killing PID $procId"
    try {
        Stop-Process -Id $procId -Force -ErrorAction Stop
        W-Ok '  Stopped.'
        return 0
    } catch {
        W-Err "  Failed to stop PID $procId : $_"
        return 1
    }
}

function Cmd-Status {
    Write-Host ''
    if (Test-ServerUp) {
        W-Ok  "  [RUNNING] Venus Notebooks is up at $Url"
        $procId = Get-ListeningPid
        if ($procId) { W-Dim "  PID: $procId" }
    } else {
        W-Err '  [STOPPED] Venus Notebooks is not running.'
    }
    Write-Host ''

    if (Test-Path $JarPath) {
        W-Dim "  JAR: $JarPath (exists)"
    } else {
        W-Warn "  JAR: not built yet -- run: ./venus.ps1 build"
    }

    if (Get-Command java -ErrorAction SilentlyContinue) {
        $jv = (& java -version 2>&1 | Select-Object -First 1)
        W-Dim  "  Java:     $jv"
    } else {
        W-Err  '  Java:     NOT FOUND'
    }

    if (Get-Command node -ErrorAction SilentlyContinue) {
        W-Dim "  Node.js:  $(node --version)"
    } else {
        W-Warn '  Node.js:  not found (JavaScript/TypeScript cells disabled)'
    }

    if (Get-Command dotnet -ErrorAction SilentlyContinue) {
        W-Dim "  .NET:     $(dotnet --version)"
    } else {
        W-Warn '  .NET:     not found (C# / F# cells disabled)'
    }
    Write-Host ''
    return 0
}

function Cmd-Build {
    Show-Banner
    W-Info '  Building Venus Notebooks...'
    W-Dim  '  --------------------------------------'
    if (-not (Test-Maven)) { return 1 }
    mvn clean package -DskipTests
    if ($LASTEXITCODE -eq 0) {
        Write-Host ''
        W-Ok   '  Build successful!'
        W-Info '  Run: ./venus.ps1 start'
    } else {
        Write-Host ''
        W-Err '  Build failed. Check output above.'
    }
    return $LASTEXITCODE
}

function Cmd-Rebuild {
    Show-Banner
    W-Warn '  Rebuilding Venus Notebooks (force)...'
    W-Dim  '  --------------------------------------'
    if (-not (Test-Maven)) { return 1 }
    mvn clean package -DskipTests
    if ($LASTEXITCODE -eq 0) {
        Write-Host ''
        W-Ok '  Rebuild successful!'
    } else {
        Write-Host ''
        W-Err '  Rebuild failed. Check output above.'
    }
    return $LASTEXITCODE
}

function Cmd-Open {
    if (Test-ServerUp) {
        W-Ok '  Opening Venus Notebooks...'
        Start-Process $Url
        return 0
    } else {
        W-Err '  Venus Notebooks is not running. Start it first: ./venus.ps1 start'
        return 1
    }
}

function Cmd-Logs {
    $logPath = Join-Path $RepoRoot 'venus.log'
    if (-not (Test-Path $logPath)) {
        W-Err '  No log file found (venus.log).'
        W-Dim '  Logs are only written in background mode: ./venus.ps1 start -Bg'
        return 1
    }
    W-Info '  Tailing venus.log (Ctrl+C to stop)...'
    W-Dim  '  --------------------------------------'
    Get-Content -Path $logPath -Wait -Tail 40
    return 0
}

function Cmd-Version {
    Write-Host ''
    W-Title '  Venus Notebooks'
    if (Test-Path 'pom.xml') {
        $line = Select-String -Path 'pom.xml' -Pattern '<version>' |
                Where-Object { $_.Line -notmatch '(parent|spring|junit|jackson|boot)' } |
                Select-Object -First 1
        if ($line) { W-Dim  "  Version:  $($line.Line.Trim())" }
    }
    if (Get-Command java   -ErrorAction SilentlyContinue) { W-Dim "  Java:     $((& java -version 2>&1 | Select-Object -First 1))" }
    if (Get-Command node   -ErrorAction SilentlyContinue) { W-Dim "  Node.js:  $(node --version)" }
    else { W-Warn '  Node.js:  not installed' }
    if (Get-Command dotnet -ErrorAction SilentlyContinue) { W-Dim "  .NET:     $(dotnet --version)" }
    if (Get-Command mvn    -ErrorAction SilentlyContinue) {
        $m = (mvn --version 2>&1 | Select-String 'Apache Maven' | Select-Object -First 1)
        if ($m) { W-Dim "  Maven:    $($m.Line.Trim())" }
    }
    Write-Host ''
    return 0
}

function Cmd-Help {
    Write-Host ''
    Write-Host '        .         ' -ForegroundColor Yellow -NoNewline; W-Title '  Venus Notebooks CLI (PowerShell)'
    Write-Host '       /|\        ' -ForegroundColor Yellow -NoNewline; W-Dim   '  --------------------------------------'
    Write-Host '      ( @ )       ' -ForegroundColor Yellow -NoNewline; W-Info  '  Java | JS | TS | C# | F# | C++'
    Write-Host '     /|\_/|\      ' -ForegroundColor Yellow
    Write-Host '    / |   | \     ' -ForegroundColor Yellow -NoNewline; W-Info  '  USAGE'
    Write-Host '   /__|   |__\    ' -ForegroundColor Yellow -NoNewline; W-Dim   '    ./venus.ps1 [command] [-Bg]'
    W-Dim   '  ---------------'
    Write-Host ''
    W-Info '  COMMANDS'
    W-Dim  '  --------------------------------------'
    Write-Host '    start            Start server (auto-build, auto-open browser)' -ForegroundColor White
    Write-Host '    start -Bg        Start in background, write logs to venus.log' -ForegroundColor White
    Write-Host '    stop             Stop a running server' -ForegroundColor White
    Write-Host '    status           Show server state, PID, Java, Node.js, .NET, JAR' -ForegroundColor White
    Write-Host '    build            Compile and package (skips if JAR exists)' -ForegroundColor White
    Write-Host '    rebuild          Force clean build' -ForegroundColor White
    Write-Host '    open             Open browser (server must already be running)' -ForegroundColor White
    Write-Host '    logs             Tail venus.log (background mode only)' -ForegroundColor White
    Write-Host '    version          Show Java, Node.js, .NET, Maven, project version' -ForegroundColor White
    Write-Host '    help             Show this help' -ForegroundColor White
    Write-Host ''
    W-Info '  EXAMPLES'
    W-Dim  '  --------------------------------------'
    Write-Host '    ./venus.ps1                  (same as: ./venus.ps1 start)' -ForegroundColor White
    Write-Host '    ./venus.ps1 start -Bg' -ForegroundColor White
    Write-Host '    ./venus.ps1 stop' -ForegroundColor White
    Write-Host '    ./venus.ps1 status' -ForegroundColor White
    Write-Host ''
    W-Info "  URL: $Url"
    Write-Host ''
    return 0
}

# ── Dispatch ────────────────────────────────────────────────────────────────
switch ($Command.ToLower()) {
    'start'    { exit (Cmd-Start) }
    'stop'     { exit (Cmd-Stop) }
    'status'   { exit (Cmd-Status) }
    'build'    { exit (Cmd-Build) }
    'rebuild'  { exit (Cmd-Rebuild) }
    'open'     { exit (Cmd-Open) }
    'logs'     { exit (Cmd-Logs) }
    'version'  { exit (Cmd-Version) }
    'help'     { exit (Cmd-Help) }
    '-h'       { exit (Cmd-Help) }
    '--help'   { exit (Cmd-Help) }
    default {
        W-Err "Unknown command: $Command"
        W-Dim 'Run: ./venus.ps1 help'
        exit 1
    }
}
