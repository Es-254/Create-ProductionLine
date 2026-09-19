# Publishes the built jar to CurseForge through the current Upload API.
#
# WHY THIS EXISTS: the Gradle plugin we declare (`net.darkhax.curseforgegradle`) still
# POSTs to `/api/projects/{id}/upload`, an endpoint CurseForge replaced with
# `/api/projects/{id}/upload-file` (see the official "CurseForge Upload API" article,
# revised 2026-05-27). The old path answers 302 -> /error, which the plugin reports as
# `403 Forbidden`, so `gradlew -PpublishMods publishCurseForge` cannot work any more.
#
# Two more things the plugin never sent, both required by the current API:
#   * `metadata` must be a PLAIN form field (not a file part) - `curl -F "metadata=<file"`
#     reads the field value from a file, which also keeps the changelog off the command line;
#   * the version list must contain at least one ENVIRONMENT version (`Client` / `Server`),
#     otherwise the upload is refused with errorCode 1021.
#
# Usage:
#   $env:CURSEFORGE_TOKEN = "<token>"                    # or curseforge_token in ~/.gradle/gradle.properties
#   .\scripts\publish-curseforge.ps1                     # release: mod_version from gradle.properties
#   .\scripts\publish-curseforge.ps1 -Dev                # dev build: newest 0.0.0-dev.N jar, release type beta
#   .\scripts\publish-curseforge.ps1 -Version 0.0.0-dev.5
#   .\scripts\publish-curseforge.ps1 -FileId 8905098     # re-send metadata (changelog/name/versions) for an existing file
#
# Tokens live at https://authors-old.curseforge.com/account/api-tokens and are sent in the
# `X-Api-Token` header. GET endpoints on minecraft.curseforge.com sit behind Cloudflare's
# JS challenge (a browser gets in, curl gets "Just a moment..."), which is why this script
# never tries to read anything back - the upload response carries the file id.

[CmdletBinding()]
param(
    [string] $ProjectRoot = '',      # resolved below: $PSScriptRoot is empty inside param()
    [string] $Version = '',
    [switch] $Dev,
    [string] $ReleaseType = '',      # release | beta | alpha; default follows the version line
    [int]    $FileId = 0,            # >0: update that file instead of uploading a new one
    [string] $Name = '',
    [int]    $Attempts = 3,
    [int]    $TimeoutSeconds = 420
)

$ErrorActionPreference = 'Stop'
$ApiBase = 'https://minecraft.curseforge.com/api'

# Windows PowerShell 5.1 evaluates param() defaults before $PSScriptRoot exists, so the default
# has to be resolved here - otherwise every invocation dies on "Split-Path ... empty string".
if (-not $ProjectRoot) {
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $PSCommandPath }
    $ProjectRoot = Split-Path -Parent $scriptDir
}

function Read-Property([string] $file, [string] $key) {
    $line = Select-String -Path $file -Pattern "^$([regex]::Escape($key))=(.*)$" | Select-Object -First 1
    if (-not $line) { return '' }
    return $line.Matches[0].Groups[1].Value.Trim()
}

function Write-Utf8NoBom([string] $path, [string] $text) {
    [IO.File]::WriteAllText($path, $text, (New-Object Text.UTF8Encoding $false))
}

# The token is kept out of the command line: curl reads it from a throwaway config file.
function New-CurlAuthConfig([string] $token) {
    $path = Join-Path $env:TEMP ("cpl-cf-auth-{0}.cfg" -f ([guid]::NewGuid().ToString('N')))
    Write-Utf8NoBom $path ("header = `"X-Api-Token: $token`"`n")
    return $path
}

function Get-CurseForgeToken {
    if ($env:CURSEFORGE_TOKEN) { return $env:CURSEFORGE_TOKEN }
    $gp = Join-Path $env:USERPROFILE '.gradle\gradle.properties'
    if (Test-Path $gp) { return (Read-Property $gp 'curseforge_token') }
    return ''
}

# --- inputs ------------------------------------------------------------------
$propsFile = Join-Path $ProjectRoot 'gradle.properties'
$projectId = Read-Property $propsFile 'curseforge_project_id'
if (-not $projectId) { throw "curseforge_project_id is empty in $propsFile (the number in the authors-dashboard URL)." }

$token = Get-CurseForgeToken
if (-not $token) {
    throw "No CurseForge token. Set `$env:CURSEFORGE_TOKEN, or add 'curseforge_token=...' to ~/.gradle/gradle.properties."
}

$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source
if (-not $curl) { $curl = 'curl.exe' }

# Version: explicit -Version wins; -Dev takes the newest dev jar; otherwise mod_version.
$jarVersion = $Version
if (-not $jarVersion -and $Dev) {
    $devJar = Get-ChildItem (Join-Path $ProjectRoot 'build/libs/create_productionline-0.0.0-dev.*.jar') -ErrorAction SilentlyContinue |
              Sort-Object LastWriteTime | Select-Object -Last 1
    if (-not $devJar) { throw "No dev jar in build/libs - run 'gradlew build -PdevBuild' first." }
    $jarVersion = $devJar.BaseName -replace '^create_productionline-', ''
}
if (-not $jarVersion) { $jarVersion = Read-Property $propsFile 'mod_version' }
if (-not $jarVersion) { throw "mod_version is empty in $propsFile" }
# Channel: a dev version is always beta; a release-line version uses mod_version_type
# from gradle.properties (so `1.0.2` can ship as a beta), defaulting to release.
if (-not $ReleaseType) {
    $declaredType = Read-Property $propsFile 'mod_version_type'
    $ReleaseType = if ($jarVersion -like '*-dev.*') { 'beta' }
                   elseif ($declaredType) { $declaredType }
                   else { 'release' }
}
if ($ReleaseType -notin @('release', 'beta', 'alpha')) {
    throw "-ReleaseType must be release, beta or alpha (got '$ReleaseType')."
}
if (-not $Name) { $Name = "Create: Production Line $jarVersion" }

$jar = Join-Path $ProjectRoot "build/libs/create_productionline-$jarVersion.jar"
if ($FileId -le 0 -and -not (Test-Path $jar)) { throw "Jar not found: $jar - run 'gradlew build' first." }

$proxyUrl = if ($env:CURSEFORGE_PROXY) { $env:CURSEFORGE_PROXY } else { $env:HTTPS_PROXY }
# minecraft.curseforge.com sits behind Cloudflare's managed challenge, which answers the default
# `curl/x.y` User-Agent with a 403 "Just a moment..." HTML page. A browser UA gets through, so
# every request carries one; the API itself still authenticates with X-Api-Token.
$userAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'
$curlCommon = @('--silent', '--show-error', '--max-time', $TimeoutSeconds, '-A', $userAgent)
if ((& $curl --help all 2>&1 | Select-String -SimpleMatch 'ssl-no-revoke')) { $curlCommon += '--ssl-no-revoke' }
if ($proxyUrl) { $curlCommon += @('--proxy', $proxyUrl); Write-Host "Using proxy $proxyUrl" }

# --- changelog: the section for this version ---------------------------------
# Release headings look like `## [1.0.1] - date`, dev headings like `## 0.0.0-dev.5 - date (beta)`.
$changelog = "Release $jarVersion"
$clFile = Join-Path $ProjectRoot 'CHANGELOG.md'
if (Test-Path $clFile) {
    $lines = Get-Content $clFile -Encoding UTF8
    $start = ($lines | Select-String -Pattern "^## \[?$([regex]::Escape($jarVersion))\]?(?![\d.])" | Select-Object -First 1).LineNumber
    if ($start) {
        # $start is 1-based, so $lines[$start] is already the line after the heading.
        $rest = $lines[$start..($lines.Count - 1)]
        $endRel = ($rest | Select-String -Pattern '^## |^\[[0-9]+\.[0-9]+\.[0-9]+\]:' | Select-Object -First 1).LineNumber
        $changelog = if ($endRel) { ($rest[0..($endRel - 2)] -join "`n").Trim() } else { ($rest -join "`n").Trim() }
    }
    if (-not $changelog -or $changelog -eq "Release $jarVersion") {
        Write-Warning "No CHANGELOG.md section found for $jarVersion - uploading with a placeholder changelog."
    }
}

# --- metadata ----------------------------------------------------------------
# gameVersionNames carries names, not ids, so no game-version lookup (a GET that Cloudflare
# would challenge) is needed. Client/Server are mandatory: they are the "environment" group.
#
# Measured against the live API on 2026-09-17: `upload-file` accepts the whole object below,
# while `update-file` answers HTTP 500 for `changelog`, `relations` and `gameVersionNames`
# ("An unhandled exception occurred"), and HTTP 200 for `displayName` / `releaseType`. Update
# mode therefore sends only the fields that work - edit the changelog and the Create dependency
# in the dashboard.
$metadata = if ($FileId -gt 0) {
    @{ fileID = $FileId; displayName = $Name; releaseType = $ReleaseType }
} else {
    @{
        changelog        = $changelog
        changelogType    = 'markdown'
        displayName      = $Name
        releaseType      = $ReleaseType
        gameVersionNames = @('1.21.1', 'NeoForge', 'Java 21', 'Client', 'Server')
        relations        = @{ projects = @(@{ slug = 'create'; type = 'requiredDependency' }) }
    }
}
if ($FileId -gt 0) {
    Write-Host "Note: CurseForge's update-file endpoint 500s on changelog/relations/gameVersionNames - those keep" -ForegroundColor Yellow
    Write-Host "      their previous values; edit them in the dashboard if they need to change." -ForegroundColor Yellow
}
$metadataFile = Join-Path $env:TEMP 'cpl-curseforge-metadata.json'
Write-Utf8NoBom $metadataFile ($metadata | ConvertTo-Json -Depth 6)

$endpoint = if ($FileId -gt 0) { "$ApiBase/projects/$projectId/update-file" } else { "$ApiBase/projects/$projectId/upload-file" }
Write-Host ("{0} {1} ({2} B) to project {3} as '{4}' [{5}]" -f `
    $(if ($FileId -gt 0) { 'Updating file' } else { 'Uploading' }), `
    [IO.Path]::GetFileName($jar), (Get-Item $jar).Length, $projectId, $Name, $ReleaseType)

$authCfg = New-CurlAuthConfig $token
try {
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Write-Host "  attempt $attempt/$Attempts ..." -NoNewline
        $bodyFile = Join-Path $env:TEMP "cpl-curseforge-resp-$attempt.json"
        $sw = [System.Diagnostics.Stopwatch]::StartNew()

        # `metadata=<file` sends the JSON as a plain form field read from disk.
        # `update-file` takes metadata only - attaching a file there is rejected.
        $formArgs = @('-F', "metadata=<$metadataFile")
        if ($FileId -le 0) { $formArgs += @('-F', "file=@$jar;type=application/java-archive") }
        $code = & $curl @curlCommon -K $authCfg -X POST @formArgs `
            -o $bodyFile -w '%{http_code}' `
            $endpoint 2>&1
        Write-Host " HTTP $code in $([int]$sw.Elapsed.TotalSeconds)s"

        $text = if (Test-Path $bodyFile) { Get-Content $bodyFile -Raw -Encoding UTF8 } else { '' }
        if ($code -eq '200') {
            $result = $text | ConvertFrom-Json
            Write-Host "`nDone:" -ForegroundColor Green
            Write-Host "  file id   $($result.id)"
            Write-Host "  project   https://www.curseforge.com/minecraft/mc-mods (id $projectId)"
            Write-Host "  note      a new project/file stays invisible until CurseForge approves it" -ForegroundColor Yellow
            exit 0
        }
        if ($code -match '^4\d\d$') {
            Write-Host "  API rejected the request (retrying will not help):" -ForegroundColor Red
            Write-Host "  $text"
            exit 1
        }
        if ($attempt -lt $Attempts) { Start-Sleep -Seconds 6 }
    }
    Write-Host "Gave up after $Attempts attempts. Connectivity to minecraft.curseforge.com looks down - retry later." -ForegroundColor Red
    exit 1
}
finally {
    Remove-Item -LiteralPath $authCfg -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $metadataFile -Force -ErrorAction SilentlyContinue
}
