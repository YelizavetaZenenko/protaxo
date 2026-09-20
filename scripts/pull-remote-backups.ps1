# Тягне нові дампи продакшн-БД з VPS на цей комп'ютер через Tailscale (SSH/scp) —
# офсайт-копія за принципом 3-2-1 (якщо Contabo щось втратить, лишається копія
# деінде). Копіює лише файли, яких ще немає локально (дампи іменовані з
# таймстемпом і незмінні, тому просте порівняння імен файлів достатнє).
#
# Призначено для періодичного запуску, коли цей комп'ютер увімкнений і в мережі
# Tailscale (див. scripts/register-remote-backup-pull-task.ps1) — сервер сам
# нічого не надсилає, оскільки не знає, коли комп'ютер онлайн.
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

$remoteFiles = ssh $RemoteHost "ls $RemoteDir/protaxo_*.dump 2>/dev/null"
if ($LASTEXITCODE -ne 0 -or -not $remoteFiles) {
    Write-Warning "Не вдалося отримати список файлів на $RemoteHost (сервер офлайн чи бекапів ще немає) — пропускаю цей запуск."
    exit 0
}

$copied = 0
foreach ($remotePath in $remoteFiles) {
    $remotePath = $remotePath.Trim()
    if (-not $remotePath) { continue }
    $name = Split-Path $remotePath -Leaf
    $localPath = Join-Path $LocalDir $name
    if (-not (Test-Path $localPath)) {
        Write-Host "Копіюю $name..."
        scp "${RemoteHost}:${remotePath}" $localPath
        $copied++
    }
}

Write-Host "Скопійовано нових файлів: $copied"

# Ротація старих офсайт-копій
Get-ChildItem -Path $LocalDir -Filter "protaxo_*.dump" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
    ForEach-Object {
        Write-Host "Видаляю стару офсайт-копію $($_.Name)"
        Remove-Item $_.FullName -Force
    }
