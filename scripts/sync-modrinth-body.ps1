# Pushes docs/platform-listing.md to Modrinth as the project description ("body"),
# and sets the client/server side flags.
#
# Uses curl.exe (Schannel) rather than .NET's HTTP stack -- see the transport note
# in publish-modrinth.ps1.
#
# Usage:
#   $env:MODRINTH_TOKEN = "mrp_..."
#   .\scripts\sync-modrinth-body.ps1

[CmdletBinding()]
param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [int]    $Attempts = 6,
    [int]    $TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'

$token = $env:MODRINTH_TOKEN
if (-not $token) { throw "Set the MODRINTH_TOKEN environment variable first." }

$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source
if (-not $curl) { $curl = 'curl.exe' }

$projectId = (Select-String -Path (Join-Path $ProjectRoot 'gradle.properties') `
    -Pattern '^modrinth_project_id=(.*)$' | Select-Object -First 1).Matches[0].Groups[1].Value.Trim()
if (-not $projectId) { throw "modrinth_project_id is empty in gradle.properties" }

$bodyFile = Join-Path $ProjectRoot 'docs/platform-listing.md'
if (-not (Test-Path $bodyFile)) { throw "Body file not found: $bodyFile" }
$body = [IO.File]::ReadAllText($bodyFile, [Text.Encoding]::UTF8)

$payload = @{ body = $body; client_side = 'required'; server_side = 'required' } |
    ConvertTo-Json -Depth 5 -Compress
$payloadFile = Join-Path $env:TEMP 'cpl-body-payload.json'
[IO.File]::WriteAllText($payloadFile, $payload, (New-Object Text.UTF8Encoding $false))

$proxyUrl = if ($env:MODRINTH_PROXY) { $env:MODRINTH_PROXY } else { $env:HTTPS_PROXY }
$curlCommon = @('--silent', '--show-error', '--max-time', $TimeoutSeconds)
if ($proxyUrl) { $curlCommon += @('--proxy', $proxyUrl); Write-Host "Using proxy $proxyUrl" }

Write-Host "Pushing $($body.Length) chars from docs/platform-listing.md to project $projectId ($((Get-Item $payloadFile).Length) B payload)"

for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    Write-Host "  attempt $attempt/$Attempts ..." -NoNewline
    $resp = Join-Path $env:TEMP "cpl-body-resp-$attempt.txt"
    $code = & $curl @curlCommon -X PATCH `
        -H "Authorization: $token" -H 'User-Agent: cpl-release/1.0' `
        -H 'Content-Type: application/json; charset=utf-8' `
        --data-binary "@$payloadFile" -o $resp -w '%{http_code}' `
        "https://api.modrinth.com/v2/project/$projectId" 2>&1

    Write-Host " HTTP $code"

    if ($code -eq '204' -or $code -eq '200') {
        $p = & $curl @curlCommon -H "Authorization: $token" -H 'User-Agent: cpl-release/1.0' `
             "https://api.modrinth.com/v2/project/$projectId" 2>$null | ConvertFrom-Json
        Write-Host "`nNow live:" -ForegroundColor Green
        Write-Host "  status        $($p.status)"
        Write-Host "  body          $($p.body.Length) chars"
        Write-Host "  client/server $($p.client_side) / $($p.server_side)"
        Write-Host "  loaders       $($p.loaders -join ',')"
        Write-Host "  game versions $($p.game_versions -join ',')"
        exit 0
    }

    if (Test-Path $resp) {
        $t = Get-Content $resp -Raw -Encoding UTF8
        if ($t) { Write-Host "    $t" }
    }
    if ($code -match '^4\d\d$') { exit 1 }
    if ($attempt -lt $Attempts) { Start-Sleep -Seconds 4 }
}

Write-Host "Gave up after $Attempts attempts." -ForegroundColor Red
exit 1
