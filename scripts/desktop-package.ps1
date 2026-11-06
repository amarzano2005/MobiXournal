<#
.SYNOPSIS
  Builds the Windows MSI installer for the desktop window.

.DESCRIPTION
  One command for the installer the release ships (`docs/tools.md`, *Desktop installer*):

    .\scripts\desktop-package.ps1

  It sets `JAVA_HOME` to a JDK 21 when the machine's is not one, puts WiX 3.14 on `PATH` for jpackage,
  reads the version from `desktop/build.gradle.kts` (the same `packageVersion` Gradle reports) and runs
  `:desktop:packageMsi` with `-PdesktopOnly`, so the Android module — and the Android SDK — is never
  configured. The MSI lands at
  `desktop/build/compose/binaries/main/msi/MobiXournal-<version>.msi`.

  The package is **unsigned**: Windows SmartScreen shows "Unknown publisher" on first run until a code
  signing certificate signs it. That is a property of the artifact, not of this script.

.PARAMETER WixBin
  The folder holding `light.exe`/`candle.exe`. Defaults to `$env:WIX_BIN`, then `$env:WIX\bin`, then the
  standard WiX 3.14 install. Unpack `wix314-binaries.zip` and pass its folder to build without an install.
#>
param(
    [string]$WixBin = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# jpackage is the JDK's own packager; a JDK without it cannot write an installer at all.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\jpackage.exe"))) {
    $fallback = Join-Path $env:USERPROFILE ".jdks\jbr-21.0.11"
    if (-not (Test-Path (Join-Path $fallback "bin\jpackage.exe"))) {
        throw "JAVA_HOME must point at a JDK 21 with jpackage.exe (see AGENTS.md, *JDKs*)."
    }
    Write-Host "JAVA_HOME was not a JDK 21; using $fallback" -ForegroundColor Yellow
    $env:JAVA_HOME = $fallback
}

# jpackage finds WiX on PATH or through %WIX%; it does not look in Program Files by itself on every
# JDK, so the folder is resolved here and named in the failure instead of surfacing as jpackage's
# "WiX toolset is not found".
$wix = $WixBin
if (-not $wix) { $wix = $env:WIX_BIN }
if (-not $wix -and $env:WIX) { $wix = Join-Path $env:WIX "bin" }
if (-not $wix) { $wix = "C:\Program Files (x86)\WiX Toolset v3.14\bin" }
if (-not (Test-Path (Join-Path $wix "light.exe"))) {
    throw "WiX Toolset 3 is not at '$wix'. jpackage needs it to write the MSI: install WiX 3.14, or unpack wix314-binaries.zip and pass -WixBin <folder> (or set WIX_BIN)."
}
$env:PATH = "$wix;$env:PATH"

# The version the package carries is the Gradle file's own, not a copy kept here.
$gradle = Get-Content (Join-Path $root "desktop\build.gradle.kts") -Raw
$match = [regex]::Match($gradle, 'packageVersion\s*=\s*"([^"]+)"')
if (-not $match.Success) { throw "desktop/build.gradle.kts has no packageVersion, so the MSI has no version." }
$version = $match.Groups[1].Value

Write-Host "Building MobiXournal $version MSI (WiX: $wix)" -ForegroundColor Cyan
& .\gradlew.bat :desktop:packageMsi -PdesktopOnly --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE, so no MSI was written." }

$msi = Join-Path $root "desktop\build\compose\binaries\main\msi\MobiXournal-$version.msi"
if (-not (Test-Path $msi)) { throw "Gradle succeeded but the MSI is not at $msi." }
$size = [math]::Round((Get-Item $msi).Length / 1MB, 1)
Write-Host "MobiXournal $version MSI: $msi ($size MB)" -ForegroundColor Green
Write-Host "Unsigned: SmartScreen reads it as an unknown publisher until it is code-signed."
