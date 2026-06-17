using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;

namespace DesklyPC;

public static class WindowSwitcherService
{
    public sealed record WindowItem(string Id, string Title, string AppName);

    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    private const int SW_RESTORE = 9;

    public static IReadOnlyList<WindowItem> GetOpenWindows()
    {
        var currentProcessId = Environment.ProcessId;
        var shellWindow = GetShellWindow();
        var windows = new List<(IntPtr handle, WindowItem item)>();

        EnumWindows((hWnd, _) =>
        {
            if (hWnd == IntPtr.Zero || hWnd == shellWindow) return true;
            if (!IsWindowVisible(hWnd)) return true;

            var length = GetWindowTextLength(hWnd);
            if (length <= 0) return true;

            GetWindowThreadProcessId(hWnd, out var processId);
            if (processId == currentProcessId) return true;

            var title = GetWindowTitle(hWnd, length).Trim();
            if (string.IsNullOrWhiteSpace(title)) return true;

            var appName = GetProcessName(processId);
            windows.Add((
                hWnd,
                new WindowItem(
                    Id: hWnd.ToInt64().ToString("X"),
                    Title: title.Length > 160 ? title[..160] : title,
                    AppName: appName.Length > 80 ? appName[..80] : appName
                )
            ));
            return true;
        }, IntPtr.Zero);

        return windows
            .GroupBy(x => x.item.Id, StringComparer.Ordinal)
            .Select(g => g.First().item)
            .Take(100)
            .ToArray();
    }

    public static bool TrySwitchTo(string? windowId, out string message, out WindowItem? window)
    {
        window = null;
        if (string.IsNullOrWhiteSpace(windowId) || !long.TryParse(windowId.Trim(), System.Globalization.NumberStyles.HexNumber, null, out var handleValue))
        {
            message = "Invalid window";
            return false;
        }

        var hWnd = new IntPtr(handleValue);
        var current = GetOpenWindows().FirstOrDefault(x => x.Id.Equals(windowId.Trim(), StringComparison.OrdinalIgnoreCase));
        if (current == null || !IsWindow(hWnd) || !IsWindowVisible(hWnd))
        {
            message = "Window unavailable";
            return false;
        }

        ShowWindow(hWnd, SW_RESTORE);
        var ok = SetForegroundWindow(hWnd);
        window = current;
        message = ok ? "OK" : "Unable to activate window";
        return ok;
    }

    private static string GetWindowTitle(IntPtr hWnd, int length)
    {
        var builder = new StringBuilder(length + 1);
        GetWindowText(hWnd, builder, builder.Capacity);
        return builder.ToString();
    }

    private static string GetProcessName(uint processId)
    {
        try
        {
            using var process = Process.GetProcessById((int)processId);
            return string.IsNullOrWhiteSpace(process.MainWindowTitle)
                ? process.ProcessName
                : process.ProcessName;
        }
        catch
        {
            return "";
        }
    }

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool IsWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll")]
    private static extern int GetWindowTextLength(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern IntPtr GetShellWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
