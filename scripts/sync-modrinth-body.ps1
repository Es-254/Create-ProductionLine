# Pushes docs/platform-listing.md to Modrinth as the project description ("body"),
# and sets the client/server side flags.
#
# Same reason as publish-modrinth.ps1: Minotaur's `modrinthSyncBody` has no retry and
# fails hard on flaky international routes.
#
# Usage:
#   $env:MODRINTH_TOKEN = "mrp_..."
#   .\scripts\sync-modrinth-body.ps1

[CmdletBinding()]
param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [int]    $Attempts = 6,
    [int]    $TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$token = $env:MODRINTH_TOKEN
if (-not $token) { throw "Set the MODRINTH_TOKEN environment variable first." }

$projectId = (Select-String -Path (Join-Path $ProjectRoot 'gradle.properties') `
    -Pattern '^modrinth_project_id=(.*)$' | Select-Object -First 1).Matches[0].Groups[1].Value.Trim()
if (-not $projectId) { throw "modrinth_project_id is empty in gradle.properties" }

$bodyFile = Join-Path $ProjectRoot 'docs/platform-listing.md'
if (-not (Test-Path $bodyFile)) { throw "Body file not found: $bodyFile" }
$body = [IO.File]::ReadAllText($bodyFile, [Text.Encoding]::UTF8)

$payload = @{ body = $body; client_side = 'required'; server_side = 'required' } |
    ConvertTo-Json -Depth 5 -Compress
$bytes = [Text.Encoding]::UTF8.GetBytes($payload)

Write-Host "Pushing $($body.Length) chars from docs/platform-listing.md to project $projectId"

$headers = @{ Authorization = $token; 'User-Agent' = 'cpl-release/1.0' }
for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    try {
        Write-Host "  attempt $attempt/$Attempts ..." -NoNewline
        $r = Invoke-WebRequest -Method Patch -Uri "https://api.modrinth.com/v2/project/$projectId" `
            -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes `
            -TimeoutSec $TimeoutSeconds -UseBasicParsing
        Write-Host " HTTP $([int]$r.StatusCode)"

        $p = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$projectId" `
            -Headers $headers -TimeoutSec $TimeoutSeconds
        Write-Host "`nNow live:" -ForegroundColor Green
        Write-Host "  status        $($p.status)"
        Write-Host "  body          $($p.body.Length) chars"
        Write-Host "  client/server $($p.client_side) / $($p.server_side)"
        Write-Host "  loaders       $($p.loaders -join ',')"
        Write-Host "  game versions $($p.game_versions -join ',')"
        exit 0
    }
    catch {
        $e = $_.Exception
        while ($e) { Write-Host "    $($e.GetType().Name): $($e.Message)"; $e = $e.InnerException }
        if ($attempt -lt $Attempts) { Start-Sleep -Seconds 5 }
    }
}

Write-Host "Gave up after $Attempts attempts." -ForegroundColor Red
exit 1
