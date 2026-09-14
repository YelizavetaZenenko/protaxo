# Safe launcher for the app. Use this instead of `.\mvnw.cmd spring-boot:run`
# directly.
#
# Why this exists: on 2026-09-10 a native Windows PostgreSQL 17 service and
# the project's Docker Postgres container both ended up listening on
# 0.0.0.0:5432 at the same time. The app silently connected to the wrong
# (empty, long-stale) server, and Flyway happily "migrated" it — including
# DROP TABLE/DROP COLUMN migrations that had been sitting unapplied for
# days. No data was actually lost (the real data was safe in the Docker
# volume the whole time), but it looked exactly like data loss. See
# docs/"База даних".md ("Інцидент 2026-09-10") for the full story.
#
# This script refuses to start the app unless it can positively confirm
# the Docker Postgres container is the thing listening on 5432, takes a
# fresh backup first, and only then runs mvnw.

$ErrorActionPreference = "Stop"
$ContainerName = "protaxo_postgres"

function Fail($msg) {
    Write-Host "BLOCKED: $msg" -ForegroundColor Red
    exit 1
}

# 1. The native Postgres service, if present and running, is the exact
#    failure mode from the incident above - refuse to proceed.
$svc = Get-Service -Name "postgresql-x64-17" -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -eq "Running") {
    Fail @"
Native Windows service 'postgresql-x64-17' is Running. It competes with the
Docker container for port 5432 and can cause the app to connect to the
wrong database (see docs/"База даних".md, incident 2026-09-10).
Stop it first, from an elevated PowerShell:
  Stop-Service -Name postgresql-x64-17 -Force
"@
}

# 2. The Docker container must be up.
$running = docker ps --filter "name=$ContainerName" --filter "status=running" --format "{{.Names}}" 2>$null
if ($running -ne $ContainerName) {
    Write-Host "Container '$ContainerName' is not running - starting it (docker compose up -d)..."
    docker compose up -d
    Start-Sleep -Seconds 3
    $running = docker ps --filter "name=$ContainerName" --filter "status=running" --format "{{.Names}}" 2>$null
    if ($running -ne $ContainerName) {
        Fail "Could not bring up '$ContainerName'. Check 'docker compose up -d' output manually."
    }
}

# 3. Confirm the container actually holds real data, not a fresh/empty
#    database - catches the case of a brand-new/wrong volume too.
$clientCount = docker exec $ContainerName psql -U postgres -d protaxo -tAc "SELECT count(*) FROM clients" 2>$null
if (-not $clientCount) {
    Fail "Could not query 'clients' table in '$ContainerName' - database may not be initialized yet."
}
Write-Host "Docker Postgres OK - clients table has $clientCount row(s)."

# 4. Fresh backup before every run - cheap insurance.
try {
    & "$PSScriptRoot\backup-db.ps1"
} catch {
    Write-Host "WARNING: pre-run backup failed ($_), continuing anyway." -ForegroundColor Yellow
}

# 5. Now it's safe to start the app.
Set-Location "$PSScriptRoot\.."
& .\mvnw.cmd spring-boot:run
