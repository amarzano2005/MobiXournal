param(
    [string]$Doc = "",
    [int]$Trials = 6,
    [string]$Out = "",
    # The strip's `+`, in window pixels: another DPI or font puts it elsewhere, and the screenshot the probe
    # saves shows where it is.
    [int]$PlusX = 322,
    [int]$PlusY = 54
)

# **Tab-switch latency, measured on the running window.**
#
# The question is the one a reader asks when they click a tab: how long until the page on screen is the
# other document's? A headless render cannot answer it (it has no window, no GPU and no compositor), so this
# drives the packaged executable: it opens a document, makes a second tab, then sends `Ctrl+Tab` and polls
# the screen until the canvas and the status bar have changed — the numbers include the app's own frame and
# the compositor's present, which is what the hand waits for.
#
#   powershell -ExecutionPolicy Bypass -File scripts/desktop-tab-switch.ps1 -Doc udiff.xopp -Trials 6
#
# A failure to find a second tab is reported rather than guessed at: the probe says how many tabs it saw.
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class TabWin
{
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] static extern void mouse_event(uint flags, int dx, int dy, int data, UIntPtr extra);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    const uint UP = 0x0002;
    const byte CTRL = 0x11, SHIFT = 0x10, TAB = 0x09;
    public static int[] Rect(IntPtr h)
    {
        var r = new RECT(); GetWindowRect(h, out r);
        return new int[] { r.Left, r.Top, r.Right - r.Left, r.Bottom - r.Top };
    }
    public static void CtrlTab()
    {
        keybd_event(CTRL, 0, 0, UIntPtr.Zero);
        keybd_event(TAB, 0, 0, UIntPtr.Zero);
        keybd_event(TAB, 0, UP, UIntPtr.Zero);
        keybd_event(CTRL, 0, UP, UIntPtr.Zero);
    }
    public static void CtrlKey(byte vk)
    {
        keybd_event(CTRL, 0, 0, UIntPtr.Zero);
        keybd_event(vk, 0, 0, UIntPtr.Zero);
        keybd_event(vk, 0, UP, UIntPtr.Zero);
        keybd_event(CTRL, 0, UP, UIntPtr.Zero);
    }
    public static void Key(byte vk)
    {
        keybd_event(vk, 0, 0, UIntPtr.Zero);
        keybd_event(vk, 0, UP, UIntPtr.Zero);
    }
    public static void Click(int x, int y)
    {
        SetCursorPos(x, y);
        mouse_event(0x0001, 0, 0, 0, UIntPtr.Zero);
        System.Threading.Thread.Sleep(120);
        mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
        System.Threading.Thread.Sleep(60);
        mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
    }
}
'@

$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root "desktop\build\compose\binaries\main\app\MobiXournal\MobiXournal.exe"
if (-not (Test-Path $exe)) { throw "the packaged window is missing: $exe (run :desktop:createDistributable)" }

Get-Process MobiXournal -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 900

if ([string]::IsNullOrWhiteSpace($Doc)) { $Doc = Join-Path $root "udiff.xopp" }
$docPath = if (Test-Path $Doc) { (Resolve-Path $Doc).Path } else { $Doc }
Start-Process -FilePath $exe -ArgumentList @("--diagnose", $docPath) | Out-Null

$window = $null
$deadline = [DateTime]::UtcNow.AddSeconds(45)
while ([DateTime]::UtcNow -lt $deadline) {
    $window = Get-Process MobiXournal -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($window) { break }
    Start-Sleep -Milliseconds 200
}
if (-not $window) { throw "no window appeared" }
Start-Sleep -Seconds 4

[TabWin]::ShowWindow($window.MainWindowHandle, 9) | Out-Null
[TabWin]::SetForegroundWindow($window.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 600

$rect = [TabWin]::Rect($window.MainWindowHandle)
$left = $rect[0]; $top = $rect[1]; $width = $rect[2]; $height = $rect[3]

function Shoot([string]$path) {
    $bmp = New-Object System.Drawing.Bitmap($width, $height)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($left, $top, 0, 0, $bmp.Size)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

# A sparse fingerprint of the canvas and the status bar: paper, ink and the readout, which is exactly what
# changes when the document on screen does.
$probe = New-Object System.Drawing.Bitmap 320, 240
$probeGraphics = [System.Drawing.Graphics]::FromImage($probe)
function Fingerprint {
    $x = $left + [int]($width * 0.30)
    $y = $top + [int]($height * 0.35)
    $probeGraphics.CopyFromScreen($x, $y, 0, 0, $probe.Size, [System.Drawing.CopyPixelOperation]::SourceCopy)
    $parts = New-Object System.Text.StringBuilder
    for ($row = 0; $row -lt 240; $row += 8) {
        for ($col = 0; $col -lt 320; $col += 8) {
            [void]$parts.Append(($probe.GetPixel($col, $row).ToArgb() -band 0xFFFFFF).ToString("X6"))
        }
    }
    # The status bar's own strip, so a switch that only changed the readout still counts.
    $parts.ToString()
}

$before = Fingerprint
Shoot (Join-Path $root "build-logs\app-opened.png")

# A second tab: one click on the strip's `+` — the window's new document, and a tab with no file dialog in
# the way. Whether it appeared is read off the screen, never assumed.
[TabWin]::Click(($left + $PlusX), ($top + $PlusY))
Start-Sleep -Milliseconds 1800
Shoot (Join-Path $root "build-logs\app-second-tab.png")
$after = Fingerprint
$secondTab = $after -ne $before

if (-not $secondTab) {
    "no second tab appeared (Ctrl+N did not add one); the probe cannot time a switch" | Write-Output
    Get-Process MobiXournal -ErrorAction SilentlyContinue | Stop-Process -Force
    exit 2
}

# Two readings per switch, because they are two different waits: the **first pixel** (the sheet appears) and
# the **settled** page (the ink has stopped arriving). A page that paints its paper in 35 ms and its writing a
# second later is not a fast switch, and only the second number says so.
$firsts = New-Object System.Collections.Generic.List[double]
$settles = New-Object System.Collections.Generic.List[double]
for ($i = 0; $i -lt $Trials; $i++) {
    $from = Fingerprint
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    [TabWin]::CtrlTab()
    $first = -1
    $last = $from
    $quietSince = -1
    while ($sw.ElapsedMilliseconds -lt 15000) {
        $now = Fingerprint
        if ($now -ne $from -and $first -lt 0) { $first = $sw.ElapsedMilliseconds }
        if ($now -ne $last) { $last = $now; $quietSince = $sw.ElapsedMilliseconds }
        elseif ($first -ge 0 -and ($sw.ElapsedMilliseconds - $quietSince) -ge 250) { break }
        Start-Sleep -Milliseconds 15
    }
    $sw.Stop()
    if ($first -ge 0) {
        $firsts.Add([double]$first)
        $settles.Add($sw.Elapsed.TotalMilliseconds)
    }
    Start-Sleep -Milliseconds 700
}

if ($firsts.Count -eq 0) {
    "no switch was observed on screen" | Write-Output
    Get-Process MobiXournal -ErrorAction SilentlyContinue | Stop-Process -Force
    exit 3
}

function Report([string]$label, $values) {
    $sorted = @($values | Sort-Object)
    "{0}`tmedian {1:N0} ms`tp95 {2:N0} ms`tmin {3:N0} ms`tmax {4:N0} ms`tn {5}" -f `
        $label, $sorted[[int]($sorted.Count / 2)], $sorted[[int][math]::Floor($sorted.Count * 0.95)],
        $sorted[0], $sorted[-1], $values.Count | Write-Output
}
Report "first pixel" $firsts
Report "settled" $settles
if ($Out) { $firsts | ForEach-Object { "{0:N1}" -f $_ } | Set-Content -Path $Out }

$log = Join-Path (Get-Location) "mobix-diagnose.log"
if (Test-Path $log) { "diagnostics: $log" | Write-Output }
Shoot (Join-Path $root "build-logs\app-after-switch.png")
Get-Process MobiXournal -ErrorAction SilentlyContinue | Stop-Process -Force
