using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace DesklyPC;

public static class DisplayControlService
{
    private const int HWND_BROADCAST = 0xffff;
    private const int WM_SYSCOMMAND = 0x0112;
    private const int SC_MONITORPOWER = 0xF170;
    private const int MONITOR_ON = -1;
    private const int MONITOR_OFF = 2;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr SendMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);

    public static bool TurnOffDisplay(out string message)
        => SetMonitorPower(MONITOR_OFF, out message);

    public static bool TurnOnDisplay(out string message)
        => SetMonitorPower(MONITOR_ON, out message);

    private static bool SetMonitorPower(int powerState, out string message)
    {
        try
        {
            SendMessage(
                new IntPtr(HWND_BROADCAST),
                WM_SYSCOMMAND,
                new IntPtr(SC_MONITORPOWER),
                new IntPtr(powerState)
            );

            message = "OK";
            return true;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
    }

    public static bool TrySetProjectionMode(string mode, out string normalizedMode, out string message)
    {
        normalizedMode = NormalizeMode(mode);
        var arg = normalizedMode switch
        {
            "internal" => "/internal",
            "duplicate" => "/clone",
            "extend" => "/extend",
            "external" => "/external",
            _ => ""
        };

        if (string.IsNullOrWhiteSpace(arg))
        {
            message = "Unsupported display mode";
            return false;
        }

        try
        {
            using var p = Process.Start(new ProcessStartInfo
            {
                FileName = "DisplaySwitch.exe",
                Arguments = arg,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            });

            if (p == null)
            {
                message = "DisplaySwitch unavailable";
                return false;
            }

            if (!p.WaitForExit(7000))
            {
                try { p.Kill(entireProcessTree: true); } catch { /* ignore */ }
                message = "DisplaySwitch timeout";
                return false;
            }

            var stderr = p.StandardError.ReadToEnd().Trim();
            if (p.ExitCode != 0)
            {
                message = string.IsNullOrWhiteSpace(stderr) ? $"DisplaySwitch failed ({p.ExitCode})" : stderr;
                return false;
            }

            message = "OK";
            return true;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
    }

    private static string NormalizeMode(string? mode)
    {
        return (mode ?? "").Trim().ToLowerInvariant() switch
        {
            "pc" or "pc_only" or "primary" or "internal" => "internal",
            "clone" or "duplicate" => "duplicate",
            "extend" or "extended" => "extend",
            "second" or "second_only" or "external" => "external",
            var x => x
        };
    }
}
