# Тягне нові дампи продакшн-БД з VPS на цей комп'ютер через Tailscale (SSH/scp) —
# офсайт-копія за принципом 3-2-1 (якщо Contabo щось втратить, лишається копія
# деінде).
#
# Призначено для періодичного запуску, коли цей комп'ютер увімкнений і в мережі
# Tailscale (див. scripts/register-remote-backup-pull-task.ps1) — сервер сам
# нічого не надсилає, оскільки не знає, коли комп'ютер онлайн.
#
# Стійкість до ноутбука, що засинає / перемикається на батарею (2026-11-27):
# - ssh/scp з BatchMode і тайм-аутами — не висне на запиті пароля чи мертвому з'єднанні;
# - файл качається в *.part і перейменовується лише після звірки розміру з сервером, тож
#   перерваний запуск не лишає «готовий» обрізаний дамп; наявні файли теж звіряються
#   за розміром і перекачуються, якщо не збігаються;
# - кожен запуск пише в backups-offsite\pull.log — щоб наступну проблему було видно.
param(
    [string]$RemoteHost = "admin@100.96.72.33",
    [string]$RemoteDir = "/opt/protaxo/backups",
    [string]$LocalDir = "D:\protaxo\backups-offsite",
    [int]$RetentionDays = 90
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $LocalDir)) {
    New-Item -ItemType Directory -Path $LocalDir | Out-Null
}
$logFile = Join-Path $LocalDir "pull.log"

function Write-Log([string]$message) {
    $line = "{0:yyyy-MM-dd HH:mm:ss}  {1}" -f (Get-Date), $message
    Write-Host $line
    Add-Content -Path $logFile -Value $line -Encoding UTF8
}

$sshOptions = @("-o", "BatchMode=yes", "-o", "ConnectTimeout=20",
                "-o", "ServerAliveInterval=15", "-o", "ServerAliveCountMax=4")

try {
    # "розмір ім'я" для кожного дампа — розмір потрібен, щоб відрізнити повний файл від обрізаного.
    # Після пробудження ноутбука Tailscale піднімається не одразу — до 3 спроб з паузою 10 хв,
    # замість чекати наступного дня.
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $listing = & ssh @sshOptions $RemoteHost "cd $RemoteDir && stat -c '%s %n' protaxo_*.dump 2>/dev/null"
        if ($LASTEXITCODE -eq 0 -and $listing) { break }
        Write-Log "Сервер недоступний (ssh код $LASTEXITCODE), спроба $attempt з 3"
        if ($attempt -lt 3) { Start-Sleep -Seconds 600 }
    }
    if ($LASTEXITCODE -ne 0 -or -not $listing) {
        Write-Log "ПОМИЛКА: сервер так і не відповів — спробую під час наступного запуску."
        exit 1
    }

    $copied = 0
    $failed = 0
    foreach ($entry in $listing) {
        $parts = $entry.Trim() -split ' ', 2
        if ($parts.Count -ne 2) { continue }
        $remoteSize = [long]$parts[0]
        $name = $parts[1]
        $localPath = Join-Path $LocalDir $name

        if ((Test-Path $localPath) -and (Get-Item $localPath).Length -eq $remoteSize) { continue }

        $partPath = "$localPath.part"
        Remove-Item $partPath -Force -ErrorAction SilentlyContinue
        & scp @sshOptions -q "${RemoteHost}:${RemoteDir}/${name}" $partPath
        if ($LASTEXITCODE -eq 0 -and (Test-Path $partPath) -and (Get-Item $partPath).Length -eq $remoteSize) {
            Move-Item $partPath $localPath -Force
            Write-Log "Скопійовано $name ($remoteSize байт)"
            $copied++
        } else {
            Remove-Item $partPath -Force -ErrorAction SilentlyContinue
            Write-Log "ПОМИЛКА копіювання $name (scp код $LASTEXITCODE) — спробую наступного запуску"
            $failed++
        }
    }

    # Ротація старих офсайт-копій
    Get-ChildItem -Path $LocalDir -Filter "protaxo_*.dump" |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
        ForEach-Object {
            Write-Log "Видаляю стару офсайт-копію $($_.Name)"
            Remove-Item $_.FullName -Force
        }

    Write-Log "Готово: нових $copied, помилок $failed"
    if ($failed -gt 0) { exit 1 }
} catch {
    Write-Log "ПОМИЛКА: $($_.Exception.Message)"
    exit 1
}
