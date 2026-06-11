$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

$backendEnvPath = Join-Path $PSScriptRoot "..\backend\.env"
if (Test-Path $backendEnvPath) {
    Get-Content $backendEnvPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }
        $name, $value = $line.Split("=", 2)
        if (-not [Environment]::GetEnvironmentVariable($name, "Process")) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

if (-not (Test-Path ".\.venv\Scripts\python.exe")) {
    Write-Host "Python virtual environment was not found. Creating .venv..."

    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCommand) {
        Write-Host "Python was not found. Install Python 3.11 first."
        Write-Host "Download: https://www.python.org/downloads/"
        exit 1
    }

    python -m venv .venv
}

Write-Host "Installing/updating Python dependencies..."
& ".\.venv\Scripts\python.exe" -m pip install -r ".\requirements.txt"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to install Python dependencies."
    exit $LASTEXITCODE
}

$env:REDIS_HOST = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { "localhost" }
$env:REDIS_PORT = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { "6379" }
$env:REDIS_QUEUE_NAME = if ($env:REDIS_QUEUE_NAME) { $env:REDIS_QUEUE_NAME } else { "mod:queue" }
$env:ROOM_CHANNEL_PREFIX = if ($env:ROOM_CHANNEL_PREFIX) { $env:ROOM_CHANNEL_PREFIX } else { "room:" }
$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { "jdbc:mysql://localhost:3306/chatguard_dev?useSSL=false&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true" }
$env:DB_USER = if ($env:DB_USER) { $env:DB_USER } elseif ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$env:MODERATOR_MODE = "unsmile"
$env:UNSMILE_MODEL_ID = if ($env:UNSMILE_MODEL_ID) { $env:UNSMILE_MODEL_ID } else { "smilegate-ai/kor_unsmile" }
$env:METRICS_PORT = if ($env:METRICS_PORT) { $env:METRICS_PORT } else { "8000" }

Write-Host "Starting ChatGuard moderation worker"
Write-Host "  mode: $env:MODERATOR_MODE"
Write-Host "  model: $env:UNSMILE_MODEL_ID"
Write-Host "  redis: $env:REDIS_HOST`:$env:REDIS_PORT / $env:REDIS_QUEUE_NAME"
Write-Host "  db: $env:DB_URL"
Write-Host "  metrics: http://localhost:$env:METRICS_PORT/metrics"

& ".\.venv\Scripts\python.exe" ".\worker.py"
