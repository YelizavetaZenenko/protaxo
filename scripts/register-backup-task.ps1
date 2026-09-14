# Registers a Windows Scheduled Task that runs scripts/backup-db.ps1 every
# day at 03:00. Run this once, as the user who should own the task
# (elevation not required since it only touches the current user's tasks).
$taskName = "ProtaxoDbBackup"
$scriptPath = "D:\protaxo\scripts\backup-db.ps1"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
$trigger = New-ScheduledTaskTrigger -Daily -At 3:00AM
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
    -Settings $settings -Description "Daily pg_dump backup of the protaxo database" -Force

Write-Host "Scheduled task '$taskName' registered: runs backup-db.ps1 daily at 03:00."
Write-Host "Inspect/remove it with: Get-ScheduledTask -TaskName $taskName"
Write-Host "                        Unregister-ScheduledTask -TaskName $taskName -Confirm:`$false"
