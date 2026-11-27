# Реєструє Windows Scheduled Task, що тягне нові бекапи БД з VPS на цей комп'ютер
# (scripts/pull-remote-backups.ps1) — офсайт-копія 3-2-1.
#
# Запуски: раз на день о 09:00 + при вході в Windows (через 3 хв, щоб піднявся Tailscale).
# Сервер робить один бекап на добу (03:00), тож частіше немає сенсу. -StartWhenAvailable
# запускає пропущений запуск одразу, як ноутбук прокинеться; якщо сервер ще недосяжний,
# скрипт сам пробує ще двічі з паузою 10 хв.
#
# Без вікна консолі: запуск через wscript + run-hidden.vbs (powershell -WindowStyle Hidden
# все одно блимає чорним вікном).
#
# 2026-11-27: дозволено працювати від батареї. За замовчуванням Windows не запускає
# завдання на батареї й ВБИВАЄ його, коли ноутбук перемикається з мережі на батарею —
# саме так завдання падало (код 0x8007042B), а офсайт-копії відставали на дні.
$taskName = "ProtaxoRemoteBackupPull"
$scriptPath = "D:\protaxo\scripts\pull-remote-backups.ps1"
$launcher = "D:\protaxo\scripts\run-hidden.vbs"

$action = New-ScheduledTaskAction -Execute "wscript.exe" -Argument "`"$launcher`" `"$scriptPath`""

$daily = New-ScheduledTaskTrigger -Daily -At 9:00AM
$logon = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$logon.Delay = "PT3M"

$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1) -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($daily, $logon) `
    -Settings $settings -Description "Pull of VPS DB backups to this machine (offsite 3-2-1 copy), daily at 09:00 and at logon, no console window" -Force

Write-Host "Scheduled task '$taskName' registered: daily at 09:00 and at logon (also on battery), no console window."
Write-Host "Log of each run: D:\protaxo\backups-offsite\pull.log"
Write-Host "Inspect/remove it with: Get-ScheduledTask -TaskName $taskName"
Write-Host "                        Unregister-ScheduledTask -TaskName $taskName -Confirm:`$false"
