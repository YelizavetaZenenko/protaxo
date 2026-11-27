# Реєструє Windows Scheduled Task, що тягне нові бекапи БД з VPS на цей комп'ютер
# (scripts/pull-remote-backups.ps1) — офсайт-копія 3-2-1.
#
# Запуски: щодня з 09:00 кожні 2 години + при вході в Windows. Скрипт копіює лише те,
# чого ще немає (або що недокачане), тож зайві запуски нічого не коштують, а пропущений
# через сон/вимкнення запуск надолужується вже за кілька годин, а не наступного дня.
# -StartWhenAvailable — запускає пропущений запуск одразу, як комп'ютер прокинеться.
#
# 2026-11-27: дозволено працювати від батареї. За замовчуванням Windows не запускає
# завдання на батареї й ВБИВАЄ його, коли ноутбук перемикається з мережі на батарею —
# саме так завдання падало (код 0x8007042B), а офсайт-копії відставали на дні.
$taskName = "ProtaxoRemoteBackupPull"
$scriptPath = "D:\protaxo\scripts\pull-remote-backups.ps1"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$scriptPath`""

$daily = New-ScheduledTaskTrigger -Daily -At 9:00AM
$repeat = New-ScheduledTaskTrigger -Once -At 9:00AM -RepetitionInterval (New-TimeSpan -Hours 2) -RepetitionDuration (New-TimeSpan -Hours 22)
$daily.Repetition = $repeat.Repetition
$logon = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$logon.Delay = "PT3M"   # дати Tailscale піднятися після входу

$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1) -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($daily, $logon) `
    -Settings $settings -Description "Pull of VPS DB backups to this machine (offsite 3-2-1 copy), every 2h from 09:00 and at logon" -Force

Write-Host "Scheduled task '$taskName' registered: every 2h from 09:00 and at logon (also on battery)."
Write-Host "Log of each run: D:\protaxo\backups-offsite\pull.log"
Write-Host "Inspect/remove it with: Get-ScheduledTask -TaskName $taskName"
Write-Host "                        Unregister-ScheduledTask -TaskName $taskName -Confirm:`$false"
