using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;

namespace DesklyPC;

public static class MonitorBrightness
{
    private delegate bool MonitorEnumProc(IntPtr hMonitor, IntPtr hdcMonitor, ref RECT lprcMonitor, IntPtr dwData);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT { public int left, top, right, bottom; }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct PHYSICAL_MONITOR
    {
        public IntPtr hPhysicalMonitor;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string szPhysicalMonitorDescription;
    }

    [DllImport("user32.dll")]
    private static extern bool EnumDisplayMonitors(IntPtr hdc, IntPtr lprcClip, MonitorEnumProc lpfnEnum, IntPtr dwData);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool GetNumberOfPhysicalMonitorsFromHMONITOR(IntPtr hMonitor, out uint pdwNumberOfPhysicalMonitors);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool GetPhysicalMonitorsFromHMONITOR(
        IntPtr hMonitor,
        uint dwPhysicalMonitorArraySize,
        [Out] PHYSICAL_MONITOR[] pPhysicalMonitorArray);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool DestroyPhysicalMonitors(uint dwPhysicalMonitorArraySize, PHYSICAL_MONITOR[] pPhysicalMonitorArray);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool GetMonitorBrightness(
        IntPtr hMonitor,
        out uint pdwMinimumBrightness,
        out uint pdwCurrentBrightness,
        out uint pdwMaximumBrightness);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool SetMonitorBrightness(IntPtr hMonitor, uint dwNewBrightness);

    public record DisplayInfo(int index, string name);

    public static List<DisplayInfo> GetDisplays()
    {
        var list = EnumeratePhysicalMonitors();
        try
        {
            var result = new List<DisplayInfo>();
            for (int i = 0; i < list.Count; i++)
            {
                var name = (list[i].szPhysicalMonitorDescription ?? "").Trim();
                if (string.IsNullOrWhiteSpace(name)) name = $"Display {i + 1}";
                result.Add(new DisplayInfo(i + 1, name));
            }
            return result;
        }
        finally
        {
            if (list.Count > 0) DestroyPhysicalMonitors((uint)list.Count, list.ToArray());
        }
    }

    public static bool TryGet(int index1Based, out int value0to100, out string message)
    {
        value0to100 = 0;
        message = "";

        if (index1Based < 1)
        {
            message = "Invalid display index";
            return false;
        }

        var mons = EnumeratePhysicalMonitors();
        if (mons.Count == 0)
        {
            message = "No DDC/CI monitors found";
            return false;
        }
        if (index1Based > mons.Count)
        {
            message = $"Display {index1Based} not available (found {mons.Count})";
            DestroyPhysicalMonitors((uint)mons.Count, mons.ToArray());
            return false;
        }

        var pm = mons[index1Based - 1];
        try
        {
            if (!GetMonitorBrightness(pm.hPhysicalMonitor, out var min, out var cur, out var max))
            {
                message = "GetMonitorBrightness failed";
                return false;
            }

            if (max <= min)
            {
                message = "Invalid brightness range";
                return false;
            }

            var pct = (int)Math.Round((cur - min) * 100.0 / (max - min));
            value0to100 = Math.Clamp(pct, 0, 100);
            return true;
        }
        finally
        {
            DestroyPhysicalMonitors((uint)mons.Count, mons.ToArray());
        }
    }

    public static bool TrySet(int index1Based, int value0to100, out string message)
    {
        message = "";
        value0to100 = Math.Clamp(value0to100, 0, 100);

        if (index1Based < 1)
        {
            message = "Invalid display index";
            return false;
        }

        var mons = EnumeratePhysicalMonitors();
        if (mons.Count == 0)
        {
            message = "No DDC/CI monitors found";
            return false;
        }
        if (index1Based > mons.Count)
        {
            message = $"Display {index1Based} not available (found {mons.Count})";
            DestroyPhysicalMonitors((uint)mons.Count, mons.ToArray());
            return false;
        }

        var pm = mons[index1Based - 1];
        try
        {
            if (!GetMonitorBrightness(pm.hPhysicalMonitor, out var min, out var cur, out var max))
            {
                message = "GetMonitorBrightness failed";
                return false;
            }

            if (max <= min)
            {
                message = "Invalid brightness range";
                return false;
            }

            var newRaw = (uint)Math.Round(min + (max - min) * (value0to100 / 100.0));
            newRaw = Math.Clamp(newRaw, min, max);

            if (!SetMonitorBrightness(pm.hPhysicalMonitor, newRaw))
            {
                message = "SetMonitorBrightness failed";
                return false;
            }

            return true;
        }
        finally
        {
            DestroyPhysicalMonitors((uint)mons.Count, mons.ToArray());
        }
    }

    private static List<PHYSICAL_MONITOR> EnumeratePhysicalMonitors()
    {
        var result = new List<PHYSICAL_MONITOR>();

        EnumDisplayMonitors(IntPtr.Zero, IntPtr.Zero, (IntPtr hMon, IntPtr hdc, ref RECT rc, IntPtr data) =>
        {
            try
            {
                if (!GetNumberOfPhysicalMonitorsFromHMONITOR(hMon, out var count) || count == 0)
                    return true;

                var arr = new PHYSICAL_MONITOR[count];
                if (!GetPhysicalMonitorsFromHMONITOR(hMon, count, arr))
                    return true;

                result.AddRange(arr);
            }
            catch { /* ignore */ }

            return true;
        }, IntPtr.Zero);

        return result;
    }
}
