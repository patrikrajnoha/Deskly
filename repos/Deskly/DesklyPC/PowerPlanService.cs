using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Text.RegularExpressions;

namespace DesklyPC;

public sealed record PowerPlanDto(string id, string name, bool supported, bool active);

public static class PowerPlanService
{
    private static readonly Dictionary<string, (string guid, string name, string alias)> KnownPlans =
        new(StringComparer.OrdinalIgnoreCase)
        {
            ["power_saver"] = ("a1841308-3541-4fab-bc81-f71556f20b4a", "Power Saver", "SCHEME_MIN"),
            ["balanced"] = ("381b4222-f694-41f0-9685-ff5bb260df2e", "Balanced", "SCHEME_BALANCED"),
            ["high_performance"] = ("8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c", "High Performance", "SCHEME_MAX")
        };

    public static string GetPlanName(string planId)
    {
        var normalized = NormalizePlan(planId);
        return KnownPlans.TryGetValue(normalized, out var plan) ? plan.name : "Custom";
    }

    public static (string id, string name, bool supported, PowerPlanDto[] plans, string message) GetCurrent()
    {
        var activeGuid = GetActiveGuid(out var msg);
        var schemes = GetSchemeGuids();
        var plans = BuildPlanList(activeGuid, schemes);

        foreach (var kv in KnownPlans)
        {
            if (string.Equals(kv.Value.guid, activeGuid, StringComparison.OrdinalIgnoreCase))
                return (kv.Key, kv.Value.name, true, plans, "OK");
        }

        return ("custom", string.IsNullOrWhiteSpace(activeGuid) ? "Unsupported" : "Custom", !string.IsNullOrWhiteSpace(activeGuid), plans, msg);
    }

    public static bool TrySet(string planId, out string normalizedPlan, out string message, out PowerPlanDto[] plans)
    {
        normalizedPlan = NormalizePlan(planId);
        if (!KnownPlans.TryGetValue(normalizedPlan, out var plan))
        {
            message = "Unsupported power plan";
            plans = GetCurrent().plans;
            return false;
        }

        var schemes = GetSchemeGuids();
        var target = schemes.Contains(plan.guid) ? plan.guid : plan.alias;
        var (code, output) = Run($"/setactive {target}", 5000);
        if (code != 0)
        {
            message = string.IsNullOrWhiteSpace(output) ? "powercfg failed" : output;
            plans = BuildPlanList(GetActiveGuid(out _), schemes);
            return false;
        }

        message = "OK";
        plans = BuildPlanList(plan.guid, GetSchemeGuids());
        return true;
    }

    private static PowerPlanDto[] BuildPlanList(string activeGuid, HashSet<string> schemes)
    {
        var list = new List<PowerPlanDto>();
        foreach (var kv in KnownPlans)
        {
            var supported = schemes.Contains(kv.Value.guid);
            var active = supported && string.Equals(kv.Value.guid, activeGuid, StringComparison.OrdinalIgnoreCase);
            list.Add(new PowerPlanDto(kv.Key, kv.Value.name, supported, active));
        }

        return list.ToArray();
    }

    private static string NormalizePlan(string? plan)
    {
        return (plan ?? "").Trim().ToLowerInvariant().Replace("-", "_").Replace(" ", "_") switch
        {
            "saver" or "powersaver" or "power_saver" => "power_saver",
            "balanced" or "balance" => "balanced",
            "performance" or "high" or "high_performance" => "high_performance",
            var x => x
        };
    }

    private static string GetActiveGuid(out string message)
    {
        var (code, output) = Run("/getactivescheme", 5000);
        if (code != 0)
        {
            message = string.IsNullOrWhiteSpace(output) ? "powercfg unavailable" : output;
            return "";
        }

        var m = Regex.Match(output, @"([0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12})");
        message = m.Success ? "OK" : "Power plan unsupported";
        return m.Success ? m.Groups[1].Value : "";
    }

    private static HashSet<string> GetSchemeGuids()
    {
        var schemes = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var (code, output) = Run("/list", 5000);
        if (code != 0) return schemes;

        foreach (Match m in Regex.Matches(output, @"([0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12})"))
            schemes.Add(m.Groups[1].Value);

        return schemes;
    }

    private static (int exitCode, string output) Run(string args, int timeoutMs)
    {
        try
        {
            using var p = Process.Start(new ProcessStartInfo
            {
                FileName = "powercfg.exe",
                Arguments = args,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            });

            if (p == null) return (-1, "Process.Start(powercfg) failed.");
            var stdout = p.StandardOutput.ReadToEnd();
            var stderr = p.StandardError.ReadToEnd();

            if (!p.WaitForExit(timeoutMs))
            {
                try { p.Kill(entireProcessTree: true); } catch { /* ignore */ }
                return (-2, "powercfg timeout.");
            }

            return (p.ExitCode, (stdout + "\n" + stderr).Trim());
        }
        catch (Exception ex)
        {
            return (-1, ex.Message);
        }
    }
}
