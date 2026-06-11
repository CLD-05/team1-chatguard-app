$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

if (-not (Test-Path ".\.venv\Scripts\python.exe")) {
    Write-Host "Python virtual environment was not found. Creating .venv..."

    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCommand) {
        Write-Host "Python was not found. Install Python 3.11 first."
        Write-Host "Download: https://www.python.org/downloads/"
        exit 1
    }

    python -m venv .venv

    Write-Host "Installing Python dependencies..."
    & ".\.venv\Scripts\python.exe" -m pip install -r ".\requirements.txt"

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to install Python dependencies."
        exit $LASTEXITCODE
    }
}

$env:REDIS_HOST = if ($env:REDIS_HOST) { $env:REDIS_HOST } else { "localhost" }
$env:REDIS_PORT = if ($env:REDIS_PORT) { $env:REDIS_PORT } else { "6379" }
$env:REDIS_QUEUE_NAME = if ($env:REDIS_QUEUE_NAME) { $env:REDIS_QUEUE_NAME } else { "mod:queue" }
$env:BACKEND_BASE_URL = if ($env:BACKEND_BASE_URL) { $env:BACKEND_BASE_URL } else { "http://localhost:8080" }
$env:MODERATOR_MODE = "unsmile"
$env:UNSMILE_MODEL_ID = if ($env:UNSMILE_MODEL_ID) { $env:UNSMILE_MODEL_ID } else { "smilegate-ai/kor_unsmile" }

Write-Host "Starting ChatGuard moderation worker"
Write-Host "  mode: $env:MODERATOR_MODE"
Write-Host "  model: $env:UNSMILE_MODEL_ID"
Write-Host "  redis: $env:REDIS_HOST`:$env:REDIS_PORT / $env:REDIS_QUEUE_NAME"
Write-Host "  backend: $env:BACKEND_BASE_URL"

& ".\.venv\Scripts\python.exe" ".\worker.py"