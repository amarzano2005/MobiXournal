<#
.SYNOPSIS
  Builds the Windows MSI installer for the desktop window.

.DESCRIPTION
  One command for the installer the release ships (`docs/tools.md`, *Desktop installer*):

    .\scripts\desktop-package.ps1

  It builds the app image with `:desktop:createDistributable -PdesktopOnly` (so the Android module and the
  Android SDK are never configured), harvests that image with WiX's `heat`, compiles
  `desktop/installer/main.wxs` with `candle` and links
  `desktop/build/installer/MobiXournal-<version>.msi` with `light`. The version is the one in
  `desktop/build.gradle.kts` (`packageVersion`), the same number `scripts/todo.sh versions` prints.

  The installer's feature tree carries the option this project wants: `.mobix` and `.xopp` associations,
  installed by default, switchable off.

  **Signing is automatic when signing material is present** — any one of:
    * `MOBIX_SIGN_PFX_BASE64` + `MOBIX_SIGN_PFX_PASSWORD` (a .pfx delivered as a CI secret),
    * `MOBIX_SIGN_PFX` (+ the password) — a .pfx on this machine,
    * `MOBIX_SIGN_THUMBPRINT` — a certificate already in the reader's store.
  `MOBIX_SIGN_TIMESTAMP` overrides the timestamp server. With none of them the MSI is left **unsigned** and
  the script says so: SmartScreen then reads it as an unknown publisher, which is a certificate this
  project does not have yet.

.PARAMETER WixBin
  The folder holding candle.exe, light.exe, heat.exe and WixUIExtension.dll. Defaults to `$env:WIX_BIN`,
  then `$env:WIX\bin`, then the standard WiX 3.14 install. Unpack `wix314-binaries.zip` and pass its folder
  to build without an install.
#>
param(
    [string]$WixBin = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# jpackage is the JDK's own packager; a JDK without it cannot write an app image at all.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\jpackage.exe"))) {
    $fallback = Join-Path $env:USERPROFILE ".jdks\jbr-21.0.11"
    if (-not (Test-Path (Join-Path $fallback "bin\jpackage.exe"))) {
        throw "JAVA_HOME must point at a JDK 21 with jpackage.exe (see AGENTS.md, *JDKs*)."
    }
    Write-Host "JAVA_HOME was not a JDK 21; using $fallback" -ForegroundColor Yellow
    $env:JAVA_HOME = $fallback
}

# heat/candle/light plus the UI extension are the whole toolchain the MSI needs. They are resolved here so
# a missing WiX is one sentence instead of light.exe's own path error.
$wix = $WixBin
if (-not $wix) { $wix = $env:WIX_BIN }
if (-not $wix -and $env:WIX) { $wix = Join-Path $env:WIX "bin" }
if (-not $wix) { $wix = "C:\Program Files (x86)\WiX Toolset v3.14\bin" }
foreach ($tool in @("candle.exe", "light.exe", "heat.exe", "WixUIExtension.dll")) {
    if (-not (Test-Path (Join-Path $wix $tool))) {
        throw "WiX Toolset 3 is not at '$wix' ($tool missing). Install WiX 3.14, or unpack wix314-binaries.zip and pass -WixBin <folder> (or set WIX_BIN)."
    }
}
$env:PATH = "$wix;$env:PATH"

# The version the package carries is the Gradle file's own, not a copy kept here.
$gradle = Get-Content (Join-Path $root "desktop\build.gradle.kts") -Raw
$match = [regex]::Match($gradle, 'packageVersion\s*=\s*"([^"]+)"')
if (-not $match.Success) { throw "desktop/build.gradle.kts has no packageVersion, so the MSI has no version." }
$version = $match.Groups[1].Value

Write-Host "Building the MobiXournal $version app image" -ForegroundColor Cyan
& .\gradlew.bat :desktop:createDistributable -PdesktopOnly --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE, so the app image was not built." }

$image = Join-Path $root "desktop\build\compose\binaries\main\app\MobiXournal"
if (-not (Test-Path (Join-Path $image "MobiXournal.exe"))) { throw "the app image is missing: $image" }

$out = Join-Path $root "desktop\build\installer"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out | Out-Null

$harvest = Join-Path $out "appfiles.wxs"
$main = Join-Path $root "desktop\installer\main.wxs"
$license = Join-Path $root "desktop\installer\license.rtf"
$msi = Join-Path $out "MobiXournal-$version.msi"

Write-Host "Harvesting the app image with heat (WiX: $wix)" -ForegroundColor Cyan
& (Join-Path $wix "heat.exe") dir $image -cg AppFiles -gg -g1 -sreg -scom -srd -dr INSTALLDIR `
    -var var.AppImage -out $harvest
if ($LASTEXITCODE -ne 0) { throw "heat failed with exit code $LASTEXITCODE, so no file list was written." }

Write-Host "Compiling the installer with candle" -ForegroundColor Cyan
& (Join-Path $wix "candle.exe") -nologo -arch x64 -ext WixUIExtension `
    "-dAppImage=$image" "-dVersion=$version" "-dLicenseFile=$license" -out "$out\" $main $harvest
if ($LASTEXITCODE -ne 0) { throw "candle failed with exit code $LASTEXITCODE, so no MSI source was compiled." }

Write-Host "Linking the MSI with light" -ForegroundColor Cyan
# ICE38/ICE64/ICE91 are the per-user install's own shape, not a defect: the app image lands in the
# reader's %LOCALAPPDATA% and every harvested component is keyed on its file, which is exactly what a
# per-user package does (those three ICEs assume a per-machine layout, and ICE64 cannot prove that the
# directories it created are removed on uninstall). Everything else in the ICE suite stays on.
& (Join-Path $wix "light.exe") -nologo -ext WixUIExtension -cultures:en-us `
    -sice:ICE38 -sice:ICE64 -sice:ICE91 -out $msi `
    (Join-Path $out "main.wixobj") (Join-Path $out "appfiles.wixobj")
if ($LASTEXITCODE -ne 0) { throw "light failed with exit code $LASTEXITCODE, so no MSI was written." }
if (-not (Test-Path $msi)) { throw "light succeeded but the MSI is not at $msi." }

# --- Signing: automatic when a certificate is configured, skipped (and said so) when it is not. ---------

function Get-SignTool {
    $found = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($found) { return $found.Source }
    $kits = Join-Path ${env:ProgramFiles(x86)} "Windows Kits\10\bin"
    if (Test-Path $kits) {
        $candidate = Get-ChildItem $kits -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -match '\\x64\\' } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    return $null
}

$timestamp = if ($env:MOBIX_SIGN_TIMESTAMP) { $env:MOBIX_SIGN_TIMESTAMP } else { "http://timestamp.digicert.com" }
$pfxFromSecret = $null
if ($env:MOBIX_SIGN_PFX_BASE64 -or $env:MOBIX_SIGN_PFX -or $env:MOBIX_SIGN_THUMBPRINT) {
    $signtool = Get-SignTool
    if (-not $signtool) {
        throw "Signing material is configured but signtool.exe was not found. Install the Windows SDK (or clear the MOBIX_SIGN_* variables to build an unsigned MSI)."
    }
    $signArgs = @("sign", "/fd", "SHA256", "/td", "SHA256", "/tr", $timestamp)
    if ($env:MOBIX_SIGN_THUMBPRINT) {
        $signArgs += @("/sha1", $env:MOBIX_SIGN_THUMBPRINT)
    } else {
        $pfxPath = $env:MOBIX_SIGN_PFX
        if ($env:MOBIX_SIGN_PFX_BASE64) {
            $pfxFromSecret = Join-Path $env:TEMP "mobixournal-sign-$([guid]::NewGuid().ToString('n')).pfx"
            [IO.File]::WriteAllBytes($pfxFromSecret, [Convert]::FromBase64String($env:MOBIX_SIGN_PFX_BASE64))
            $pfxPath = $pfxFromSecret
        }
        $signArgs += @("/f", $pfxPath)
        if ($env:MOBIX_SIGN_PFX_PASSWORD) { $signArgs += @("/p", $env:MOBIX_SIGN_PFX_PASSWORD) }
    }
    try {
        Write-Host "Signing $msi" -ForegroundColor Cyan
        & $signtool @signArgs $msi
        if ($LASTEXITCODE -ne 0) { throw "signtool failed with exit code $LASTEXITCODE, so the MSI is not signed." }
        & $signtool verify /pa $msi
        if ($LASTEXITCODE -ne 0) { throw "the signature on $msi did not verify (signtool verify exit $LASTEXITCODE)." }
    } finally {
        if ($pfxFromSecret -and (Test-Path $pfxFromSecret)) { Remove-Item $pfxFromSecret -Force }
    }
} else {
    Write-Host "Unsigned: no signing material is configured (MOBIX_SIGN_PFX_BASE64, MOBIX_SIGN_PFX or MOBIX_SIGN_THUMBPRINT), so SmartScreen reads an unknown publisher." -ForegroundColor Yellow
}

$size = [math]::Round((Get-Item $msi).Length / 1MB, 1)
Write-Host "MobiXournal $version MSI: $msi ($size MB)" -ForegroundColor Green
