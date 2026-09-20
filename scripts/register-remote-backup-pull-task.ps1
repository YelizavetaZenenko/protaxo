# Реєструє Windows Scheduled Task, що щодня о 09:00 тягне нові бекапи БД з VPS
# на цей комп'ютер (scripts/pull-remote-backups.ps1) — офсайт-копія 3-2-1.
# -StartWhenAvailable надолужує пропущений запуск, якщо комп'ютер був
# вимкнений о 09:00 — спрацює одразу, як тільки увімкнеться й буде онлайн.
$taskName = "ProtaxoRemoteBackupPull"
$scriptPath = "D:\protaxo\scripts\pull-remote-backups.ps1"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`""
$trigger = New-ScheduledTaskTrigger -Daily -At 9:00AM
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger `
    -Settings $settings -Description "Daily pull of VPS DB backups to this machine (offsite 3-2-1 copy)" -Force

Write-Host "Scheduled task '$taskName' registered: runs pull-remote-backups.ps1 daily at 09:00 (or on next boot/connect if missed)."
Write-Host "Inspect/remove it with: Get-ScheduledTask -TaskName $taskName"
Write-Host "                        Unregister-ScheduledTask -TaskName $taskName -Confirm:`$false"
