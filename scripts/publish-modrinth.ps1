# Publishes the built jar to Modrinth via the REST API.
#
# Why this exists next to Minotaur (`gradlew -PpublishMods modrinth`):
# on networks where international routes are flaky (e.g. behind Watt Toolkit),
# Minotaur's upload dies with "Connect timed out" and gives no retry. This script
# does the same upload with a long timeout and a retry loop, and reports the real
# API error body instead of a wrapped exception.
#
# Usage:
#   $env:MODRINTH_TOKEN = "mrp_..."
#   .\scripts\publish-modrinth.ps1                 # publishes mod_version from gradle.properties
#   .\scripts\publish-modrinth.ps1 -ReleaseType beta -Name "v1.0.1-beta"
#   .\scripts\publish-modrinth.ps1 -Attempts 10

[CmdletBinding()]
param(
    [string] $ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string] $ReleaseType = 'release',   # release | beta | alpha
    [string] $Name,
    [int]    $Attempts = 6,
    [int]    $TimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'

function Read-Property([string] $file, [string] $key) {
    $line = Select-String -Path $file -Pattern "^$([regex]::Escape($key))=(.*)$" |
            Select-Object -First 1
    if (-not $line) { return '' }
    return $line.Matches[0].Groups[1].Value.Trim()
}

# --- inputs ------------------------------------------------------------------
$token = $env:MODRINTH_TOKEN
if (-not $token) { throw "Set the MODRINTH_TOKEN environment variable first." }

$propsFile = Join-Path $ProjectRoot 'gradle.properties'
$projectId = Read-Property $propsFile 'modrinth_project_id'
$version   = Read-Property $propsFile 'mod_version'
if (-not $projectId) { throw "modrinth_project_id is empty in $propsFile" }
if (-not $version)   { throw "mod_version is empty in $propsFile" }
if (-not $Name)      { $Name = "v$version" }

$jar = Join-Path $ProjectRoot "build/libs/create_productionline-$version.jar"
if (-not (Test-Path $jar)) { throw "Jar not found: $jar — run 'gradlew build' first." }

# --- changelog: the section for this version ---------------------------------
$changelog = "Release $version"
$clFile = Join-Path $ProjectRoot 'CHANGELOG.md'
if (Test-Path $clFile) {
    $lines = Get-Content $clFile -Encoding UTF8
    $start = ($lines | Select-String -Pattern "^## \[$([regex]::Escape($version))\]" | Select-Object -First 1).LineNumber
    if ($start) {
        $rest = $lines[$start..($lines.Count - 1)]
        $endRel = ($rest | Select-String -Pattern '^## \[|^\[[0-9]+\.[0-9]+\.[0-9]+\]:' | Select-Object -Skip 1 -First 1).LineNumber
        $changelog = if ($endRel) { ($rest[0..($endRel - 2)] -join "`n").Trim() }
                     else { ($rest -join "`n").Trim() }
    }
}

# --- Create dependency (optional, resolved live with a known-good fallback) --
$createId = 'LNytGWDc'
try {
    $resolved = Invoke-RestMethod -Uri 'https://api.modrinth.com/v2/project/create' `
        -Headers @{ 'User-Agent' = 'cpl-release/1.0' } -TimeoutSec 30
    if ($resolved.id) { $createId = $resolved.id }
} catch {
    Write-Host "  (could not resolve the Create project id, using $createId)" -ForegroundColor DarkGray
}

$payload = @{
    name           = $Name
    version_number = $version
    changelog      = $changelog
    dependencies   = @(@{ project_id = $createId; dependency_type = 'required' })
    game_versions  = @('1.21.1')
    version_type   = $ReleaseType
    loaders        = @('neoforge')
    featured       = $true
    status         = 'listed'
    project_id     = $projectId
    file_parts     = @('file')
    primary_file   = 'file'
} | ConvertTo-Json -Depth 10 -Compress

Write-Host "Publishing $([IO.Path]::GetFileName($jar)) ($((Get-Item $jar).Length) B) as $Name -> project $projectId"

Add-Type -AssemblyName System.Net.Http

for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    $client.DefaultRequestHeaders.Add('Authorization', $token)
    $client.DefaultRequestHeaders.Add('User-Agent', 'cpl-release/1.0 (modrinth publish)')

    $content = [System.Net.Http.MultipartFormDataContent]::new()
    $content.Add([System.Net.Http.StringContent]::new($payload, [System.Text.Encoding]::UTF8, 'application/json'), 'data')
    $stream = [IO.File]::OpenRead($jar)
    $fileContent = [System.Net.Http.StreamContent]::new($stream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/java-archive')
    $content.Add($fileContent, 'file', [IO.Path]::GetFileName($jar))

    try {
        Write-Host "  attempt $attempt/$Attempts ..." -NoNewline
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $response = $client.PostAsync('https://api.modrinth.com/v2/version', $content).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        Write-Host " HTTP $([int]$response.StatusCode) in $([int]$sw.Elapsed.TotalSeconds)s"

        if ([int]$response.StatusCode -lt 300) {
            $published = $body | ConvertFrom-Json
            Write-Host "`nPublished:" -ForegroundColor Green
            Write-Host "  id             $($published.id)"
            Write-Host "  version        $($published.version_number) ($($published.version_type), $($published.status))"
            Write-Host "  loaders        $($published.loaders -join ',')"
            Write-Host "  game versions  $($published.game_versions -join ',')"
            Write-Host "  file           $($published.files[0].filename)  $($published.files[0].size) B"
            Write-Host "  url            $($published.files[0].url)"
            exit 0
        }

        # A real API error (bad input, duplicate version, ...) — retrying will not help.
        Write-Host "  API rejected the request: $body" -ForegroundColor Red
        exit 1
    }
    catch {
        $e = $_.Exception
        while ($e) { Write-Host "    $($e.GetType().Name): $($e.Message)"; $e = $e.InnerException }
        if ($attempt -lt $Attempts) { Start-Sleep -Seconds 5 }
    }
    finally {
        $stream.Dispose()
        $client.Dispose()
    }
}

Write-Host "Gave up after $Attempts attempts. International connectivity looks down — retry when it recovers." -ForegroundColor Red
exit 1
