# Deploy BizLink API to Google Cloud Run (free tier)
# Prerequisites: gcloud CLI installed + logged in (gcloud auth login)
# Usage: .\scripts\deploy-cloudrun.ps1

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $ProjectRoot

$ServiceName = "bizlink-api"
$Region = "asia-south1"   # Mumbai — closest to India
$Memory = "1Gi"
$Cpu = "1"
$Timeout = "300"
$FrontendUrl = "https://bizlink-web-cyan.vercel.app"

# --- Check gcloud ---
$gcloud = Get-Command gcloud -ErrorAction SilentlyContinue
if (-not $gcloud) {
    $defaultPath = "$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
    if (Test-Path $defaultPath) { $gcloud = $defaultPath } else {
        Write-Host "gcloud CLI not found. Install: winget install Google.CloudSDK" -ForegroundColor Red
        exit 1
    }
} else {
    $gcloud = $gcloud.Source
}

Write-Host "Using gcloud: $gcloud" -ForegroundColor Cyan

# --- Load .env ---
$envFile = Join-Path $ProjectRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host ".env not found. Copy .env.example and fill DATABASE_URL + JWT_SECRET." -ForegroundColor Red
    exit 1
}

$vars = @{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        $vars[$matches[1].Trim()] = $matches[2].Trim()
    }
}

if (-not $vars["DATABASE_URL"]) {
    Write-Host "DATABASE_URL missing in .env" -ForegroundColor Red
    exit 1
}

# Production overrides
$vars["DEV_MODE"] = "false"
$vars["PUBLIC_BASE_URL"] = $FrontendUrl
$vars["UPLOAD_DIR"] = "/app/uploads"
if (-not $vars["CORS_ALLOWED_ORIGINS"] -or $vars["CORS_ALLOWED_ORIGINS"] -notmatch "vercel") {
    $vars["CORS_ALLOWED_ORIGINS"] = "$FrontendUrl,http://localhost:5173"
}

# --- Write env YAML for Cloud Run ---
$envYamlPath = Join-Path $ProjectRoot "env.cloudrun.yaml"
$yamlLines = @()
foreach ($key in $vars.Keys | Sort-Object) {
  $val = $vars[$key] -replace '"', '\"'
  $yamlLines += "${key}: `"$val`""
}
$yamlLines | Set-Content $envYamlPath -Encoding UTF8
Write-Host "Wrote $envYamlPath (gitignored)" -ForegroundColor Gray

# --- Project ---
$project = & $gcloud config get-value project 2>$null
if (-not $project) {
    Write-Host "No GCP project set. Run: gcloud config set project YOUR_PROJECT_ID" -ForegroundColor Red
    exit 1
}
Write-Host "GCP Project: $project" -ForegroundColor Cyan

# --- Enable APIs ---
Write-Host "Enabling Cloud Run + Cloud Build APIs..." -ForegroundColor Yellow
& $gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com --quiet

# --- Deploy ---
Write-Host "Deploying $ServiceName to $Region (this takes 5-10 min)..." -ForegroundColor Yellow
& $gcloud run deploy $ServiceName `
    --source . `
    --region $Region `
    --platform managed `
    --allow-unauthenticated `
    --memory $Memory `
    --cpu $Cpu `
    --timeout $Timeout `
    --max-instances 3 `
    --min-instances 0 `
    --port 8080 `
    --env-vars-file $envYamlPath

$url = & $gcloud run services describe $ServiceName --region $Region --format "value(status.url)"
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  DEPLOYED: $url" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next: Update Vercel env var:" -ForegroundColor Yellow
Write-Host "  VITE_API_URL = $url" -ForegroundColor White
Write-Host "  Then: cd bizlink-web && vercel deploy --prod --yes" -ForegroundColor White
Write-Host ""
Write-Host "Test: $url/api/subscription/plans" -ForegroundColor Cyan
