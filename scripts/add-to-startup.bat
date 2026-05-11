@echo off
REM ================================================================
REM  Venus Notebooks — Add to Windows Startup
REM  Creates a shortcut in the Windows startup folder so Venus
REM  starts automatically when you log in.
REM ================================================================

set STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set SCRIPT_DIR=%~dp0
set VBS_FILE=%SCRIPT_DIR%start-venus.vbs
set SHORTCUT=%STARTUP%\Venus Notebooks.lnk

echo Adding Venus Notebooks to Windows startup...

REM Use PowerShell to create a proper .lnk shortcut
powershell -Command ^
  "$ws = New-Object -ComObject WScript.Shell; ^
   $sc = $ws.CreateShortcut('%SHORTCUT%'); ^
   $sc.TargetPath = 'wscript.exe'; ^
   $sc.Arguments = '"""%VBS_FILE%""" //nologo'; ^
   $sc.WorkingDirectory = '%SCRIPT_DIR%..'; ^
   $sc.Description = 'Venus Notebooks'; ^
   $sc.Save()"

if %ERRORLEVEL% == 0 (
    echo.
    echo SUCCESS: Venus Notebooks will start automatically at login.
    echo Shortcut created at:
    echo   %SHORTCUT%
    echo.
    echo To remove from startup, delete the shortcut:
    echo   del "%SHORTCUT%"
) else (
    echo ERROR: Could not create startup shortcut.
    echo You can manually place a shortcut to start-venus.vbs in:
    echo   %STARTUP%
)

pause
