# Backs up the protaxo PostgreSQL database to a timestamped file and
# deletes backups older than $RetentionDays. Intended to run daily via
# Windows Task Scheduler (see scripts/register-backup-task.ps1) and also
# manually right before applying new Flyway migrations.
#
# Dumps via `docker exec` straight into the protaxo_postgres container
# instead of connecting over host port 5432. This machine also runs a
# native PostgreSQL 17 Windows service that can end up bound to the same
# port 5432 as the Docker container's port-proxy (see docs/"Запуск
# проєкту".md, "Важливо: конфлікт портів Postgres") - a host-port dump
# could silently back up the wrong server. docker exec talks to the
# container directly and sidesteps that ambiguity entirely.
param(
    [string]$ContainerName = "protaxo_postgres",
    [string]$PgUser = "postgres",
    [string]$PgDatabase = "protaxo",
    [string]$BackupDir = "D:\protaxo\backups",
    [int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"

$running = docker ps --filter "name=$ContainerName" --filter "status=running" --format "{{.Names}}"
if ($running -ne $ContainerName) {
    Write-Error "Container '$ContainerName' is not running (docker ps shows: '$running'). Start it with 'docker compose up -d' before backing up."
    exit 1
}

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $BackupDir "protaxo_$timestamp.dump"
$tmpInContainer = "/tmp/protaxo_backup_$timestamp.dump"

# Dump to a file inside the container first, then `docker cp` it out.
# Piping `docker exec` binary stdout through PowerShell's text pipeline
# (Windows PowerShell 5.1 has no -AsByteStream) corrupts the dump, so
# this two-step copy is the reliable path here.
docker exec $ContainerName pg_dump -U $PgUser -d $PgDatabase -Fc -f $tmpInContainer
if ($LASTEXITCODE -ne 0) {
    Write-Error "docker exec pg_dump failed with exit code $LASTEXITCODE"
    exit 1
}

docker cp "${ContainerName}:${tmpInContainer}" $outFile
docker exec $ContainerName rm -f $tmpInContainer

if (-not (Test-Path $outFile) -or (Get-Item $outFile).Length -eq 0) {
    Write-Error "docker cp failed or produced an empty file"
    if (Test-Path $outFile) { Remove-Item $outFile -Force }
    exit 1
}

Write-Host "Backup written to $outFile"

# Rotate old backups
Get-ChildItem -Path $BackupDir -Filter "protaxo_*.dump" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
    ForEach-Object {
        Write-Host "Removing old backup $($_.Name)"
        Remove-Item $_.FullName -Force
    }
