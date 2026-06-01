using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace DesklyPC;

public static class NightModeService
{
    // Single lock protects all shared state (enabled/intensity/original ramps/version)
    private static readonly object _lock = new();

    private static Control? _uiInvoker;

    private static bool _enabled;
    private static int _intensity; // 0..100

    // Apply coalescing: last request wins
    private static int _applyVersion;

    // Snapshot handling (avoid saving "original" after some other app changes it)
    private static bool _hasSnapshot;

    // Kelvin range tuned to feel like Windows Night light / monitor night modes
    private const int KELVIN_MAX = 6500; // neutral daylight
    private const int KELVIN_MIN = 4200; // warm but not "orange lamp"

    // Store original gamma ramps per display device name
    private static readonly Dictionary<string, ushort[]> _originalRamps =
        new(StringComparer.OrdinalIgnoreCase);

    /// <summary> Zavolaj raz z UI threadu (napr. Form1 ctor). </summary>
    public static void Init(Control uiInvoker)
    {
        _uiInvoker = uiInvoker;
    }

    public static (bool enabled, int intensity) GetState()
    {
        lock (_lock) return (_enabled, _intensity);
    }

    /// <summary>
    /// enabled=false -> restore original gamma ramps.
    /// enabled=true  -> apply Kelvin-based gamma ramp.
    /// </summary>
    public static bool TrySet(bool enabled, int intensity0to100, out string message)
    {
        message = "";
        intensity0to100 = Math.Clamp(intensity0to100, 0, 100);
        if (!enabled) intensity0to100 = 0;

        bool shouldSnapshot = false;

        lock (_lock)
        {
            bool wasEnabled = _enabled;

            _enabled = enabled && intensity0to100 > 0;
            _intensity = intensity0to100;

            // Snapshot originals only on first transition from OFF -> ON
            if (_enabled && !wasEnabled && !_hasSnapshot)
            {
                shouldSnapshot = true;
                _hasSnapshot = true;
            }

            // Bump apply version so older queued Apply() calls become no-ops
            _applyVersion++;
        }

        try
        {
            var inv = _uiInvoker;
            if (inv != null && !inv.IsDisposed)
            {
                if (inv.InvokeRequired)
                {
                    inv.BeginInvoke(new Action(() => Apply(shouldSnapshot)));
                }
                else
                {
                    Apply(shouldSnapshot);
                }
            }
            else
            {
                // No UI invoker - still try apply directly (server can run headless)
                Apply(shouldSnapshot);
            }

            return true;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
    }

    public static void Shutdown()
    {
        try
        {
            // Restore on shutdown (best effort)
            TrySet(false, 0, out _);
        }
        catch
        {
            /* ignore */
        }
    }

    private static void Apply(bool snapshotNow)
    {
        int myVersion;
        bool enabled;
        int intensity;

        lock (_lock)
        {
            myVersion = _applyVersion;
            enabled = _enabled;
            intensity = _intensity;
        }

        var displays = EnumerateDisplayDevices();

        // If asked to snapshot now, do it before any changes (best effort)
        if (snapshotNow)
        {
            foreach (var d in displays)
            {
                // If a newer request arrived, stop
                lock (_lock)
                {
                    if (myVersion != _applyVersion) return;
                }

                SnapshotOriginalRamp(d.DeviceName);
            }
        }

        // If disabled -> restore originals
        if (!enabled || intensity <= 0)
        {
            foreach (var d in displays)
            {
                lock (_lock)
                {
                    if (myVersion != _applyVersion) return;
                }

                RestoreOriginalRamp(d.DeviceName);
            }

            return;
        }

        // intensity 0..100 -> Kelvin 6500..4200 (linear)
        var t = intensity / 100.0;
        var targetKelvin = (int)Math.Round(KELVIN_MAX - t * (KELVIN_MAX - KELVIN_MIN));

        // convert Kelvin -> RGB whitepoint weights (0..1)
        var (wr, wg, wb) = KelvinToRgbWeights(targetKelvin);

        // Build gamma ramp (3x256)
        var ramp = BuildWarmRamp(wr, wg, wb, intensity);

        // Apply to all active display devices (best effort)
        foreach (var d in displays)
        {
            lock (_lock)
            {
                if (myVersion != _applyVersion) return;
            }

            ApplyRamp(d.DeviceName, ramp, out _);
        }
    }

    // ===== Gamma ramp building =====

    /// <summary>
    /// Builds a ramp that warms whitepoint but preserves blacks/grays to avoid "orange overlay" look.
    /// </summary>
    private static ushort[] BuildWarmRamp(double wr, double wg, double wb, int intensity0to100)
    {
        // Keep blacks more neutral: blend weights towards 1.0 in dark tones
        // Stronger intensity -> a bit stronger warmth, but still safe.
        var intensity = Math.Clamp(intensity0to100, 0, 100) / 100.0;

        // Weight strength scaling (avoid extreme orange)
        // e.g., at full intensity we still don't fully apply wb reduction in shadows
        var strength = 0.65 + 0.35 * intensity; // 0.65..1.0

        // Apply a very gentle curve: keeps highlights smooth
        var curve = 1.0; // 1.0 = linear

        var ramp = new ushort[3 * 256];
        for (int i = 0; i < 256; i++)
        {
            double x = i / 255.0;

            // smoothstep for dark protection (0..1)
            // shadows: s~0 => weights ~1 (neutral)
            // highlights: s~1 => weights approach warm target
            double s = SmoothStep(0.0, 1.0, x);
            // protect shadows more: raise s slightly
            s = Math.Pow(s, 0.85);

            double rW = Lerp(1.0, wr, s * strength);
            double gW = Lerp(1.0, wg, s * strength);
            double bW = Lerp(1.0, wb, s * strength);

            double r = Clamp01(Math.Pow(x, curve) * rW);
            double g = Clamp01(Math.Pow(x, curve) * gW);
            double b = Clamp01(Math.Pow(x, curve) * bW);

            ramp[0 * 256 + i] = (ushort)Math.Round(r * 65535.0);
            ramp[1 * 256 + i] = (ushort)Math.Round(g * 65535.0);
            ramp[2 * 256 + i] = (ushort)Math.Round(b * 65535.0);
        }

        return ramp;
    }

    private static (double r, double g, double b) KelvinToRgbWeights(int kelvin)
    {
        // Approximation for color temperature -> RGB (normalized)
        double temp = kelvin / 100.0;

        double r, g, b;

        if (temp <= 66.0)
        {
            r = 1.0;
            g = Clamp01((99.4708025861 * Math.Log(temp) - 161.1195681661) / 255.0);
            b = temp <= 19.0
                ? 0.0
                : Clamp01((138.5177312231 * Math.Log(temp - 10.0) - 305.0447927307) / 255.0);
        }
        else
        {
            r = Clamp01((329.698727446 * Math.Pow(temp - 60.0, -0.1332047592)) / 255.0);
            g = Clamp01((288.1221695283 * Math.Pow(temp - 60.0, -0.0755148492)) / 255.0);
            b = 1.0;
        }

        // Normalize so that max channel is 1.0 (keeps brightness reasonable)
        double max = Math.Max(r, Math.Max(g, b));
        if (max <= 0.0001) return (1.0, 1.0, 1.0);

        r /= max; g /= max; b /= max;

        // Keep green close to 1.0 to avoid “orange/sepia” look (small corrective bias)
        g = Lerp(g, 1.0, 0.08);

        return (Clamp01(r), Clamp01(g), Clamp01(b));
    }

    // ===== Apply / Restore gamma ramps =====

    private static void SnapshotOriginalRamp(string deviceName)
    {
        // If already snapped for this device, skip
        lock (_lock)
        {
            if (_originalRamps.ContainsKey(deviceName)) return;
        }

        var hdc = Native.CreateDC("DISPLAY", deviceName, null, IntPtr.Zero);
        if (hdc == IntPtr.Zero) return;

        try
        {
            var original = new ushort[3 * 256];
            bool got = Native.GetDeviceGammaRamp(hdc, original);
            if (got)
            {
                lock (_lock)
                {
                    if (!_originalRamps.ContainsKey(deviceName))
                        _originalRamps[deviceName] = original;
                }
            }
        }
        finally
        {
            Native.DeleteDC(hdc);
        }
    }

    private static void ApplyRamp(string deviceName, ushort[] ramp, out string? error)
    {
        error = null;

        var hdc = Native.CreateDC("DISPLAY", deviceName, null, IntPtr.Zero);
        if (hdc == IntPtr.Zero)
        {
            error = $"CreateDC failed for {deviceName}, err={Marshal.GetLastWin32Error()}";
            return;
        }

        try
        {
            // If no snapshot exists (e.g., service started enabled), try to save once
            lock (_lock)
            {
                if (!_originalRamps.ContainsKey(deviceName))
                {
                    var original = new ushort[3 * 256];
                    bool got = Native.GetDeviceGammaRamp(hdc, original);
                    if (got) _originalRamps[deviceName] = original;
                }
            }

            bool ok = Native.SetDeviceGammaRamp(hdc, ramp);
            if (!ok)
            {
                error = $"SetDeviceGammaRamp failed for {deviceName}, err={Marshal.GetLastWin32Error()}";
            }
        }
        finally
        {
            Native.DeleteDC(hdc);
        }
    }

    private static void RestoreOriginalRamp(string deviceName)
    {
        ushort[]? original;
        lock (_lock)
        {
            if (!_originalRamps.TryGetValue(deviceName, out original))
                return;
        }

        var hdc = Native.CreateDC("DISPLAY", deviceName, null, IntPtr.Zero);
        if (hdc == IntPtr.Zero) return;

        try
        {
            Native.SetDeviceGammaRamp(hdc, original);
        }
        finally
        {
            Native.DeleteDC(hdc);
        }
    }

    // ===== Display enumeration =====

    private sealed class DisplayDeviceInfo
    {
        public string DeviceName { get; init; } = "";
    }

    private static List<DisplayDeviceInfo> EnumerateDisplayDevices()
    {
        var list = new List<DisplayDeviceInfo>();

        // Use Screen list because it matches user-visible monitors and gives device names like \\.\DISPLAY1
        foreach (var s in Screen.AllScreens)
        {
            var name = s.DeviceName?.Trim();
            if (string.IsNullOrWhiteSpace(name)) continue;
            list.Add(new DisplayDeviceInfo { DeviceName = name });
        }

        // Fallback: if Screen.AllScreens returns nothing (rare), use DISPLAY1
        if (list.Count == 0)
            list.Add(new DisplayDeviceInfo { DeviceName = @"\\.\DISPLAY1" });

        return list;
    }

    // ===== Helpers =====

    private static double Clamp01(double v) => v < 0 ? 0 : (v > 1 ? 1 : v);
    private static double Lerp(double a, double b, double t) => a + (b - a) * t;

    private static double SmoothStep(double edge0, double edge1, double x)
    {
        x = (x - edge0) / (edge1 - edge0);
        x = Clamp01(x);
        return x * x * (3 - 2 * x);
    }

    // ===== Native =====

    private static class Native
    {
        [DllImport("gdi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr CreateDC(string lpszDriver, string lpszDevice, string? lpszOutput, IntPtr lpInitData);

        [DllImport("gdi32.dll", SetLastError = true)]
        public static extern bool DeleteDC(IntPtr hdc);

        // IMPORTANT: buffer must be 3*256 ushorts
        [DllImport("gdi32.dll", SetLastError = true)]
        public static extern bool GetDeviceGammaRamp(IntPtr hDC, [Out] ushort[] lpRamp);

        [DllImport("gdi32.dll", SetLastError = true)]
        public static extern bool SetDeviceGammaRamp(IntPtr hDC, [In] ushort[] lpRamp);
    }
}
