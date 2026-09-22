# Publishes the built jar to Modrinth via the REST API.
#
# TRANSPORT NOTE: this uses curl.exe (Schannel), NOT .NET/PowerShell's own HTTP
# stack. Measurements on the dev machine showed .NET's TLS handshake to
# api.modrinth.com hanging until timeout (TCP connects fine) while curl.exe
# completed the same request in under a second -- i.e. the interference is at the
# TLS/SNI layer and is fingerprint-sensitive. curl.exe ships with Windows 10+.
#
# Usage:
#   $env:MODRINTH_TOKEN = "mrp_..."
#   .\scripts\publish-modrinth.ps1                       # release: mod_version from gradle.properties
#   .\scripts\publish-modrinth.ps1 -Dev                  # dev build: newest 0.0.0-dev.N jar, channel beta
#   .\scripts\publish-modrinth.ps1 -Version 0.0.0-dev.5  # explicit version (jar must exist)
#   .\scripts\publish-modrinth.ps1 -ReleaseType beta
#   .\scripts\publish-modrinth.ps1 -Attempts 10
#
# See RELEASING.md → "Version policy": 1.0.x = release, 0.0.0-dev.N = beta.
#
# Optional proxy (a real tunnel is the only cure when even curl fails):
#   $env:MODRINTH_PROXY = "http://127.0.0.1:<port>"

[CmdletBinding()]
param(
    [string] $ProjectRoot = '',          # resolved below: $PSScriptRoot is empty inside param()
    [string] $ReleaseType = '',          # release | beta | alpha; default follows the version line
    [string] $Version = '',              # explicit override, e.g. 0.0.0-dev.5
    [switch] $Dev,                       # use the newest dev jar and default to the beta channel
    [string] $Name,
    [int]    $Attempts = 6,
    [int]    $TimeoutSeconds = 600
)

$ErrorActionPreference = 'Stop'

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

# Resolve the token WITHOUT putting it on a command line:
#   1. $env:MODRINTH_TOKEN (CI), else
#   2. modrinth_token in ~/.gradle/gradle.properties (outside any repository).
function Get-ModrinthToken {
    if ($env:MODRINTH_TOKEN) { return $env:MODRINTH_TOKEN }
    $gp = Join-Path $env:USERPROFILE '.gradle\gradle.properties'
    if (Test-Path $gp) {
        $m = Select-String -Path $gp -Pattern '^\s*modrinth_token\s*=\s*(.+)$' | Select-Object -First 1
        if ($m) { return $m.Matches[0].Groups[1].Value.Trim() }
    }
    return ''
}

# curl reads its headers from this file, so the secret never shows up in the
# process argument list (visible to other processes via Get-Process / WMI).
function New-CurlAuthConfig([string] $token, [string] $userAgent) {
    $path = Join-Path $env:TEMP ("cpl-curl-" + [guid]::NewGuid().ToString('N') + ".cfg")
    $lines = @(
        'header = "Authorization: ' + $token + '"',
        'header = "User-Agent: ' + $userAgent + '"'
    )
    Write-Utf8NoBom $path ($lines -join "`n")
    return $path
}

# --- inputs ------------------------------------------------------------------
$token = Get-ModrinthToken
if (-not $token) {
    throw "No Modrinth token. Set `$env:MODRINTH_TOKEN, or add 'modrinth_token=...' to ~/.gradle/gradle.properties."
}

$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source
if (-not $curl) { $curl = 'curl.exe' }

$propsFile = Join-Path $ProjectRoot 'gradle.properties'
$projectId = Read-Property $propsFile 'modrinth_project_id'
if (-not $projectId) { throw "modrinth_project_id is empty in $propsFile" }

# Version: explicit -Version wins; -Dev takes the newest dev jar; otherwise mod_version.
$jarVersion = $Version
if (-not $jarVersion -and $Dev) {
    # Sort by write time: dev-build.txt holds the NEXT number, so the number cannot be
    # derived from the counter file - the freshest 0.0.0-dev.* jar is the one just built.
    $devJar = Get-ChildItem (Join-Path $ProjectRoot 'build/libs/create_productionline-0.0.0-dev.*.jar') -ErrorAction SilentlyContinue |
              Sort-Object LastWriteTime | Select-Object -Last 1
    if (-not $devJar) { throw "No dev jar in build/libs - run 'gradlew build -PdevBuild' first." }
    $jarVersion = $devJar.BaseName -replace '^create_productionline-', ''
}
if (-not $jarVersion) { $jarVersion = Read-Property $propsFile 'mod_version' }
if (-not $jarVersion) { throw "mod_version is empty in $propsFile" }
$version = $jarVersion
# Channel: a dev version is always beta; a release-line version uses mod_version_type
# from gradle.properties (so `1.0.2` can ship as a beta), defaulting to release.
if (-not $ReleaseType) {
    $declaredType = Read-Property $propsFile 'mod_version_type'
    $ReleaseType = if ($version -like '*-dev.*') { 'beta' }
                   elseif ($declaredType) { $declaredType }
                   else { 'release' }
}
if ($ReleaseType -notin @('release', 'beta', 'alpha')) {
    throw "-ReleaseType must be release, beta or alpha (got '$ReleaseType')."
}
if ($version -like '*-dev.*' -and $ReleaseType -eq 'release') {
    throw "Dev version $version cannot be published as 'release' - use the beta channel."
}
if ($version -like '*-snapshot.*' -and $ReleaseType -eq 'release') {
    throw "Snapshot $version cannot be published as 'release' - use the alpha channel (or beta)."
}
if (-not $Name) { $Name = "v$version" }

$jar = Join-Path $ProjectRoot "build/libs/create_productionline-$version.jar"
if (-not (Test-Path $jar)) { throw "Jar not found: $jar - run 'gradlew build' first." }

$proxyUrl = if ($env:MODRINTH_PROXY) { $env:MODRINTH_PROXY } else { $env:HTTPS_PROXY }
$curlCommon = @('--silent', '--show-error', '--max-time', $TimeoutSeconds)
# On networks where the CRL/OCSP endpoints are unreachable (common behind
# international-route filtering) Schannel fails the handshake with
# CRYPT_E_NO_REVOCATION_CHECK. Disabling the revocation lookup keeps full
# certificate-chain verification intact; only the best-effort revocation check
# is skipped. Harmless no-op on curl builds without Schannel.
if ((& $curl --help all 2>&1 | Select-String -SimpleMatch 'ssl-no-revoke')) {
    $curlCommon += '--ssl-no-revoke'
}
if ($proxyUrl) { $curlCommon += @('--proxy', $proxyUrl); Write-Host "Using proxy $proxyUrl" }

# --- changelog: the section for this version ---------------------------------
$changelog = "Release $version"
$clFile = Join-Path $ProjectRoot 'CHANGELOG.md'
if (Test-Path $clFile) {
    $lines = Get-Content $clFile -Encoding UTF8
    # Release headings are `## [1.0.1] — date`, dev headings `## 0.0.0-dev.5 — date (beta)`.
    # The (?![\d.]) guard keeps `1.0.1` from matching a `## [1.0.10]` heading.
    $start = ($lines | Select-String -Pattern "^## \[?$([regex]::Escape($version))\]?(?![\d.])" | Select-Object -First 1).LineNumber
    if ($start) {
        # $start is 1-based, so $lines[$start] is already the line AFTER the heading.
        $rest = $lines[$start..($lines.Count - 1)]
        $endRel = ($rest | Select-String -Pattern '^## |^\[[0-9]+\.[0-9]+\.[0-9]+\]:' | Select-Object -First 1).LineNumber
        $changelog = if ($endRel) { ($rest[0..($endRel - 2)] -join "`n").Trim() } else { ($rest -join "`n").Trim() }
    }
    if (-not $changelog -or $changelog -eq "Release $version") {
        Write-Warning "No CHANGELOG.md section found for $version - the version will be published with a placeholder changelog."
    }
}

# --- Create dependency (resolved live, with a known-good fallback) -----------
$createId = 'LNytGWDc'
$probe = & $curl @curlCommon -H 'User-Agent: cpl-release/1.0' 'https://api.modrinth.com/v2/project/create' 2>$null
if ($probe) {
    try { $createId = ($probe | ConvertFrom-Json).id } catch { }
}

$payload = @{
    name           = $Name
    version_number = $version
    changelog      = $changelog
    dependencies   = @(@{ project_id = $createId; dependency_type = 'required' })
    game_versions  = @('1.21.1')
    version_type   = $ReleaseType
    loaders        = @('neoforge')
    featured       = ($ReleaseType -eq 'release')   # betas are not featured on the project page
    status         = 'listed'
    project_id     = $projectId
    file_parts     = @('file')
    primary_file   = 'file'
} | ConvertTo-Json -Depth 10 -Compress

$payloadFile = Join-Path $env:TEMP 'cpl-version-data.json'
Write-Utf8NoBom $payloadFile $payload

Write-Host "Publishing $([IO.Path]::GetFileName($jar)) ($((Get-Item $jar).Length) B) as $Name -> project $projectId"

$authCfg = New-CurlAuthConfig $token 'cpl-release/1.0 (modrinth publish)'

for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    Write-Host "  attempt $attempt/$Attempts ..." -NoNewline
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    $bodyFile = Join-Path $env:TEMP "cpl-version-resp-$attempt.json"
    $code = & $curl @curlCommon -X POST -K $authCfg `
        -F "data=@$payloadFile;type=application/json" `
        -F "file=@$jar;type=application/java-archive" `
        -o $bodyFile -w '%{http_code}' `
        'https://api.modrinth.com/v2/version' 2>&1

    Write-Host " HTTP $code in $([int]$sw.Elapsed.TotalSeconds)s"
    $text = if (Test-Path $bodyFile) { Get-Content $bodyFile -Raw -Encoding UTF8 } else { '' }

    if ($code -eq '200' -or $code -eq '201') {
        $published = $text | ConvertFrom-Json
        Write-Host "`nPublished:" -ForegroundColor Green
        Write-Host "  id             $($published.id)"
        Write-Host "  version        $($published.version_number) ($($published.version_type), $($published.status))"
        Write-Host "  loaders        $($published.loaders -join ',')"
        Write-Host "  game versions  $($published.game_versions -join ',')"
        Write-Host "  file           $($published.files[0].filename)  $($published.files[0].size) B"
        Write-Host "  url            $($published.files[0].url)"
        Remove-Item -LiteralPath $authCfg -Force -ErrorAction SilentlyContinue
        exit 0
    }

    if ($code -match '^4\d\d$') {
        Write-Host "  API rejected the request (retrying will not help):" -ForegroundColor Red
        Write-Host "  $text"
        Remove-Item -LiteralPath $authCfg -Force -ErrorAction SilentlyContinue
        exit 1
    }

    if ($attempt -lt $Attempts) { Start-Sleep -Seconds 4 }
}

Remove-Item -LiteralPath $authCfg -Force -ErrorAction SilentlyContinue
Write-Host "Gave up after $Attempts attempts. Connectivity looks down - retry later." -ForegroundColor Red
exit 1
