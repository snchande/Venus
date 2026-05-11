' ================================================================
' Venus Notebooks — Silent VBScript Launcher
' Starts the server without showing a console window,
' then opens the browser.
' ================================================================
Option Explicit

Dim shell, fso, scriptDir, projectDir, jarPath, javaCmd

Set shell = CreateObject("WScript.Shell")
Set fso   = CreateObject("Scripting.FileSystemObject")

' Navigate to project root (scripts\.. = project root)
scriptDir  = fso.GetParentFolderName(WScript.ScriptFullName)
projectDir = fso.GetParentFolderName(scriptDir)

jarPath = projectDir & "\target\venus-notebooks-1.0.0-SNAPSHOT.jar"

' Check if JAR exists
If Not fso.FileExists(jarPath) Then
    MsgBox "Venus Notebooks JAR not found." & vbCrLf & _
           "Please build first:" & vbCrLf & _
           "  mvn clean package -DskipTests" & vbCrLf & vbCrLf & _
           "Or use scripts\start-venus.bat instead.", _
           vbExclamation, "Venus Notebooks"
    WScript.Quit 1
End If

' Check if already running
Dim http
Set http = CreateObject("MSXML2.ServerXMLHTTP")
On Error Resume Next
http.open "GET", "http://localhost:8585", False
http.send
If Err.Number = 0 And http.status = 200 Then
    shell.Run "http://localhost:8585", 1, False
    WScript.Quit 0
End If
On Error GoTo 0

' Build the java command
javaCmd = "java" & _
    " --add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED" & _
    " --add-opens=java.base/java.lang=ALL-UNNAMED" & _
    " --add-exports=jdk.jshell/jdk.jshell=ALL-UNNAMED" & _
    " -jar """ & jarPath & """"

' Start server silently (0 = hidden window, False = don't wait)
shell.CurrentDirectory = projectDir
shell.Run javaCmd, 0, False

' Wait 5 seconds then open browser
WScript.Sleep 5000
shell.Run "http://localhost:8585", 1, False
