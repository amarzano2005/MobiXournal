param(
    [Parameter(Mandatory = $true)][ValidateSet("mobixournal", "xournalpp")][string]$App,
    [int]$Trials = 14,
    [string]$Out = "",
    [string]$Shot = ""
)

# **Ink latency on screen, measured the same way for this window and for Xournal++.**
#
# The question it answers is the one a hand asks: a pointer moves, how long until ink appears where it
# landed? The pointer is driven with synthetic mouse messages (a tablet cannot be automated), and the
# answer is read off the screen by copying a small box around the target and looking for a dark pixel —
# so the number includes the app's own processing, its frame, the desktop compositor's present and a
# fraction of a millisecond of probing. Both programs are measured through the same loop, on the same
# machine, with the same page and the same tool, which is what makes the two numbers comparable.
#
# One short stroke per trial, each on its own horizontal band, so no trial can see the previous one's
# ink. The pen slot of each app is clicked first: Xournal++ ignores presses on its canvas until its
# toolbar has been used once, and picking the pen in both programs also removes the tool as a variable.
#
#   powershell -ExecutionPolicy Bypass -File scripts/desktop-latency.ps1 -App mobixournal -Trials 14
#   powershell -ExecutionPolicy Bypass -File scripts/desktop-latency.ps1 -App xournalpp -Trials 14
#
# The numbers this produced on the owner's PC (Profile D, 3440x1440 at 120 Hz) are recorded in
# docs/desktop-tablet.md; re-run it there to check a change to the ink path.
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$apps = @{
    mobixournal = @{
        Exe  = Join-Path $PSScriptRoot "..\desktop\build\compose\binaries\main\app\MobiXournal\MobiXournal.exe"
        Slot = @(279, 104)   # the rail's first slot, the pen
        Match = "*MobiXournal*"
    }
    xournalpp = @{
        Exe  = "C:\Program Files\Xournal++\bin\xournalpp.exe"
        Slot = @(32, 105)    # the toolbar's pen
        Match = "*xournalpp*"
    }
}
$target = $apps[$App]
$exe = (Resolve-Path $target.Exe).Path
$slot = $target.Slot
$match = $target.Match

Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class InkLatency
{
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] static extern void mouse_event(uint f, int dx, int dy, int data, UIntPtr extra);

    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }

    const uint DOWN = 0x0002;
    const uint UP = 0x0004;
    const uint MOVE = 0x0001;
    const int HALF = 20;    // half the box searched around the target, in pixels
    const int DARK = 150;   // a pixel this dark is ink on paper

    static Bitmap _bmp;
    static Graphics _g;
    static byte[] _buf;

    public static void Prepare()
    {
        _bmp = new Bitmap(HALF * 2 + 1, HALF * 2 + 1, PixelFormat.Format24bppRgb);
        _g = Graphics.FromImage(_bmp);
        _buf = new byte[Math.Abs(_bmp.Width * 3) * _bmp.Height + 64];
    }

    public static void Down(int x, int y)
    {
        // The pointer arrives, and the window is given a moment to see it: a press issued in the same
        // instant as the move races the motion event.
        SetCursorPos(x, y);
        mouse_event(MOVE, 0, 0, 0, UIntPtr.Zero);
        System.Threading.Thread.Sleep(140);
        mouse_event(DOWN, 0, 0, 0, UIntPtr.Zero);
    }

    public static void Up()
    {
        mouse_event(UP, 0, 0, 0, UIntPtr.Zero);
    }

    /// Move to (x, y) and return microseconds until ink shows within HALF of it, or -1 on timeout.
    public static double Move(int x, int y, int timeoutMs)
    {
        var stop = Stopwatch.StartNew();
        SetCursorPos(x, y);
        mouse_event(MOVE, 0, 0, 0, UIntPtr.Zero);
        while (stop.ElapsedMilliseconds < timeoutMs)
        {
            if (Ink(x, y)) return stop.Elapsed.TotalMilliseconds * 1000.0;
        }
        return -1;
    }

    static bool Ink(int x, int y)
    {
        _g.CopyFromScreen(x - HALF, y - HALF, 0, 0, _bmp.Size, CopyPixelOperation.SourceCopy);
        var data = _bmp.LockBits(new Rectangle(0, 0, _bmp.Width, _bmp.Height),
            ImageLockMode.ReadOnly, PixelFormat.Format24bppRgb);
        int stride;
        try
        {
            stride = Math.Abs(data.Stride);
            int len = stride * data.Height;
            if (_buf.Length < len) _buf = new byte[len];
            Marshal.Copy(data.Scan0, _buf, 0, len);
        }
        finally { _bmp.UnlockBits(data); }

        for (int i = 0; i < _bmp.Height; i++)
        {
            int row = i * stride;
            for (int j = 0; j < _bmp.Width; j++)
            {
                int p = row + j * 3;
                if ((_buf[p] + _buf[p + 1] + _buf[p + 2]) / 3 < DARK) return true;
            }
        }
        return false;
    }

    public static long Area(IntPtr h)
    {
        var r = new RECT();
        GetWindowRect(h, out r);
        return (long)(r.Right - r.Left) * (r.Bottom - r.Top);
    }

    public static int[] WindowRect(IntPtr h)
    {
        var r = new RECT();
        GetWindowRect(h, out r);
        return new int[] { r.Left, r.Top, r.Right - r.Left, r.Bottom - r.Top };
    }

    public static int[] ClientOrigin(IntPtr h)
    {
        var p = new POINT { X = 0, Y = 0 };
        ClientToScreen(h, ref p);
        return new int[] { p.X, p.Y };
    }
}
'@ -ReferencedAssemblies System.Drawing

Get-Process -Name MobiXournal, xournalpp -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 900
Start-Process -FilePath $exe | Out-Null

$window = $null
$deadline = [DateTime]::UtcNow.AddSeconds(40)
while ([DateTime]::UtcNow -lt $deadline) {
    # The **largest** window: Xournal++ starts a wrapper process beside the editor, whose own window
    # put every probe off the page.
    $window = Get-Process |
        Where-Object { $_.MainWindowHandle -ne 0 -and $_.ProcessName -like $match } |
        Sort-Object { [InkLatency]::Area($_.MainWindowHandle) } -Descending |
        Select-Object -First 1
    if ($window) { break }
    Start-Sleep -Milliseconds 100
}
if (-not $window) { throw "no window for $App" }
Start-Sleep -Seconds 2

[InkLatency]::ShowWindow($window.MainWindowHandle, 9) | Out-Null
[InkLatency]::SetForegroundWindow($window.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 700

$bounds = [InkLatency]::WindowRect($window.MainWindowHandle)
$client = [InkLatency]::ClientOrigin($window.MainWindowHandle)
$centreX = $client[0] + $bounds[2] / 2
$centreY = $client[1] + $bounds[3] / 2
[InkLatency]::Prepare()

# The pen, picked the way a hand would: one click on the slot.
[InkLatency]::Down(($bounds[0] + $slot[0]), ($bounds[1] + $slot[1]))
Start-Sleep -Milliseconds 60
[InkLatency]::Up()
Start-Sleep -Milliseconds 600

$samples = New-Object System.Collections.Generic.List[double]
for ($i = 0; $i -lt $Trials; $i++) {
    $y = $centreY - 260 + $i * 28
    $left = ($i % 2) -eq 0
    $ax = if ($left) { $centreX - 140 } else { $centreX + 20 }
    $bx = if ($left) { $centreX - 20 } else { $centreX + 140 }
    [InkLatency]::Down($ax, $y)
    Start-Sleep -Milliseconds 200
    $us = [InkLatency]::Move($bx, $y, 500)
    Start-Sleep -Milliseconds 120
    [InkLatency]::Up()
    Start-Sleep -Milliseconds 260
    if ($us -ge 0) { $samples.Add($us / 1000.0) }
}

if ($Shot) {
    $bmp = New-Object System.Drawing.Bitmap ($bounds[2]), ($bounds[3])
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($bounds[0], $bounds[1], 0, 0, $bmp.Size)
    $bmp.Save($Shot, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

$sorted = @($samples | Sort-Object)
function Percentile($values, $p) {
    if ($values.Count -eq 0) { return 0 }
    return $values[[math]::Min($values.Count - 1, [math]::Floor($p * $values.Count))]
}

"{0}`tmedian {1:N2} ms`tp95 {2:N2} ms`tmin {3:N2} ms`tmax {4:N2} ms`tsamples {5}/{6}" -f `
    $App, (Percentile $sorted 0.5), (Percentile $sorted 0.95), $sorted[0], $sorted[-1], $samples.Count, $Trials
if ($Out) { $samples | ForEach-Object { "{0:N3}" -f $_ } | Set-Content -Path $Out }

Get-Process -Name MobiXournal, xournalpp -ErrorAction SilentlyContinue | Stop-Process -Force
