' Runs a PowerShell script with no console window at all (powershell -WindowStyle Hidden
' still flashes a black window for a moment). Used from Task Scheduler:
'   wscript.exe "D:\protaxo\scripts\run-hidden.vbs" "D:\protaxo\scripts\<script>.ps1"
' Waits for the script and returns its exit code, so Task Scheduler sees the real result.
' ASCII only on purpose: wscript reads .vbs files in the ANSI code page.
If WScript.Arguments.Count < 1 Then WScript.Quit 2
Set shell = CreateObject("WScript.Shell")
WScript.Quit shell.Run("powershell.exe -NoProfile -ExecutionPolicy Bypass -File """ & WScript.Arguments(0) & """", 0, True)
