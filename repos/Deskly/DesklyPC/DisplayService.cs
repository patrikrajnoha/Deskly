using System;
using System.Collections.Generic;
using System.Linq;
using System.Management;
using System.Security.Cryptography;
using System.Text;

namespace DesklyPC;

public sealed record DisplayDto(
    string id,
    string name,
    string type,                 // "internal"/"external"
    bool supportsBrightness
);

public sealed class DisplayEntry
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public required DisplayType Type { get; init; }
    public required bool SupportsBrightness { get; init; }

    // Externé: mapovanie na DDC/DXVA2 index (1-based, podľa MonitorBrightness.GetDisplays()).
    public int? DdcIndex1Based { get; init; }
}

public static class DisplayService
{
    private static readonly object _lock = new();
    private static DateTime _cacheAt = DateTime.MinValue;
    private static List<DisplayEntry>? _cache;

    // krátky cache, aby sa pri UI refreshi neenumerovalo 10x za sekundu
    private static readonly TimeSpan CacheTtl = TimeSpan.FromSeconds(2);

    public static List<DisplayEntry> GetEntries()
    {
        lock (_lock)
        {
            if (_cache != null && (DateTime.UtcNow - _cacheAt) < CacheTtl)
                return _cache;

            var entries = new List<DisplayEntry>();
            var usedIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            // 1) INTERNAL (WMI brightness)
            var internalSupported = WmiBrightnessSupported();

            entries.Add(new DisplayEntry
            {
                Id = "internal",
                Name = "Internal display",
                Type = DisplayType.Internal,
                SupportsBrightness = internalSupported,
                DdcIndex1Based = null
            });
            usedIds.Add("internal");

            // 2) EXTERNAL (DXVA2/MonitorBrightness) - DDC-first
            var ddcDisplays = SafeGetDdcDisplays(); // (index, name/desc)
            var wmi = SafeGetWmiMonitorIdentities();

            foreach (var d in ddcDisplays.OrderBy(x => x.index))
            {
                // ak monitor nevie GetMonitorBrightness, je to často ghost entry -> nepoužiť
                var supportsGet = MonitorBrightness.TryGet(d.index, out _, out _);
                if (!supportsGet)
                    continue;

                var w = FindBestWmiMatch(wmi, d);

                // Stabilnejšie ID
                var baseId = (w != null && !string.IsNullOrWhiteSpace(w.InstanceName))
                    ? "ext_" + ShortHash(w.InstanceName)
                    : $"ext_ddc_{d.index}";

                var id = baseId;
                if (usedIds.Contains(id))
                    id = $"{baseId}_{d.index}";
                usedIds.Add(id);

                var name = (w != null && !string.IsNullOrWhiteSpace(w.FriendlyName))
                    ? w.FriendlyName
                    : (!string.IsNullOrWhiteSpace(d.name) ? d.name : $"Monitor {d.index}");

                entries.Add(new DisplayEntry
                {
                    Id = id,
                    Name = name,
                    Type = DisplayType.External,
                    SupportsBrightness = true,
                    DdcIndex1Based = d.index
                });
            }

            _cache = entries;
            _cacheAt = DateTime.UtcNow;
            return entries;
        }
    }

    public static DisplayDto[] GetDisplays()
    {
        return GetEntries()
            .Select(e => new DisplayDto(
                id: e.Id,
                name: e.Name,
                type: e.Type == DisplayType.Internal ? "internal" : "external",
                supportsBrightness: e.SupportsBrightness
            ))
            .ToArray();
    }

    public static bool TryResolveDdcIndex(string displayId, out int index1Based)
    {
        index1Based = 0;
        if (string.IsNullOrWhiteSpace(displayId)) return false;

        var e = GetEntries().FirstOrDefault(x =>
            string.Equals(x.Id, displayId, StringComparison.OrdinalIgnoreCase));

        if (e?.DdcIndex1Based is int idx && idx > 0)
        {
            index1Based = idx;
            return true;
        }

        if (displayId.StartsWith("ddc_", StringComparison.OrdinalIgnoreCase) &&
            int.TryParse(displayId.Substring(4), out var parsed) && parsed > 0)
        {
            index1Based = parsed;
            return true;
        }

        return false;
    }

    // =========================
    // INTERNAL brightness (WMI)
    // =========================

    public static bool WmiBrightnessSupported()
    {
        try
        {
            using var searcher = new ManagementObjectSearcher("root\\WMI", "SELECT * FROM WmiMonitorBrightness");
            foreach (ManagementObject _ in searcher.Get())
                return true;
        }
        catch { }
        return false;
    }

    public static bool TryGetInternalBrightness(out int value0to100, out string message)
    {
        value0to100 = -1;
        message = "";

        try
        {
            using var searcher = new ManagementObjectSearcher("root\\WMI", "SELECT * FROM WmiMonitorBrightness");
            foreach (ManagementObject o in searcher.Get())
            {
                var cur = Convert.ToInt32(o["CurrentBrightness"] ?? 0);
                value0to100 = Math.Clamp(cur, 0, 100);
                return true;
            }

            message = "No WmiMonitorBrightness instances found";
            return false;
        }
        catch (Exception ex)
        {
            message = $"WMI get failed: {ex.Message}";
            return false;
        }
    }

    public static bool TrySetInternalBrightness(int value0to100, out string message)
    {
        message = "";
        value0to100 = Math.Clamp(value0to100, 0, 100);

        try
        {
            using var searcher = new ManagementObjectSearcher("root\\WMI", "SELECT * FROM WmiMonitorBrightnessMethods");
            foreach (ManagementObject o in searcher.Get())
            {
                o.InvokeMethod("WmiSetBrightness", new object[] { 0u, (byte)value0to100 });
                return true;
            }

            message = "No WmiMonitorBrightnessMethods instances found";
            return false;
        }
        catch (Exception ex)
        {
            message = $"WMI set failed: {ex.Message}";
            return false;
        }
    }

    // =========================
    // helpers
    // =========================

    private sealed record WmiMon(
        string InstanceName,
        string FriendlyName,
        string Manufacturer,
        string ProductCode,
        string Serial
    );

    private static List<(int index, string name)> SafeGetDdcDisplays()
    {
        try
        {
            return MonitorBrightness.GetDisplays()
                .Select(d => (d.index, d.name))
                .ToList();
        }
        catch
        {
            return new List<(int, string)>();
        }
    }

    private static List<WmiMon> SafeGetWmiMonitorIdentities()
    {
        var list = new List<WmiMon>();
        try
        {
            using var searcher = new ManagementObjectSearcher("root\\WMI", "SELECT * FROM WmiMonitorID");
            foreach (ManagementObject o in searcher.Get())
            {
                var instance = (o["InstanceName"] as string) ?? "";
                if (string.IsNullOrWhiteSpace(instance)) continue;

                var mfg = UShortArrayToString(o["ManufacturerName"] as ushort[]) ?? "";
                var prod = UShortArrayToString(o["ProductCodeID"] as ushort[]) ?? "";
                var serial = UShortArrayToString(o["SerialNumberID"] as ushort[]) ?? "";
                var friendly = UShortArrayToString(o["UserFriendlyName"] as ushort[]) ?? "";

                list.Add(new WmiMon(instance, friendly, mfg, prod, serial));
            }
        }
        catch { }

        return list;
    }

    private static WmiMon? FindBestWmiMatch(List<WmiMon> wmi, (int index, string name) d)
    {
        if (wmi.Count == 0) return null;

        if (!string.IsNullOrWhiteSpace(d.name))
        {
            var byContains = wmi.FirstOrDefault(x =>
                !string.IsNullOrWhiteSpace(x.FriendlyName) &&
                d.name.Contains(x.FriendlyName, StringComparison.OrdinalIgnoreCase));

            if (byContains != null) return byContains;
        }

        var firstNamed = wmi.FirstOrDefault(x => !string.IsNullOrWhiteSpace(x.FriendlyName));
        if (firstNamed != null) return firstNamed;

        return wmi[0];
    }

    private static string? UShortArrayToString(ushort[]? arr)
    {
        if (arr == null || arr.Length == 0) return null;
        var sb = new StringBuilder(arr.Length);
        foreach (var u in arr)
        {
            if (u == 0) break;
            sb.Append((char)u);
        }
        var s = sb.ToString().Trim();
        return string.IsNullOrWhiteSpace(s) ? null : s;
    }

    private static string ShortHash(string s)
    {
        using var sha = SHA256.Create();
        var bytes = sha.ComputeHash(Encoding.UTF8.GetBytes(s));
        return Convert.ToHexString(bytes).Substring(0, 10).ToLowerInvariant();
    }
}
