using System;
using System.Diagnostics;
using System.Globalization;
using System.Security.Principal;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace DesklyPC;

public static class QuietModeService
{
    private static readonly object _lock = new();
    private static Control? _uiInvoker;

    // Our intended state (in-memory). After app restart this is false by default,
    // so GetState() also tries to infer state from the current system plan.
    private static bool _enabled;

    // Original active plan (before Quiet ON) – captured ONLY on the transition OFF -> ON
    private static string? _origPowerSchemeGuid;

    // Silent scheme GUID (from your powercfg /list)
    private const string SILENT_SCHEME_GUID = "64a64f24-65b9-4b56-befd-5ec1eaced9b3";

    // Snapshot: ORIGINAL scheme
    private static bool _hasOrigSnapshot;
    private static uint _origMaxCpuAc;
    private static uint _origMaxCpuDc;
    private static uint _origCoolingAc;
    private static uint _origCoolingDc;
    private static bool _origHasCoolingSnapshot;

    // Snapshot: SILENT scheme (best-effort)
    private static bool _hasSilentSnapshot;
    private static uint _silentMaxCpuAc;
    private static uint _silentMaxCpuDc;
    private static uint _silentCoolingAc;
    private static uint _silentCoolingDc;
    private static bool _silentHasCoolingSnapshot;

    // Recommended quiet defaults (safe)
    private const uint QUIET_MAX_CPU_AC = 85;
    private const uint QUIET_MAX_CPU_DC = 75;

    // Cooling policy values in Windows power settings:
    // 0 = Active (increase fan first), 1 = Passive (throttle first)
    private const uint COOLING_PASSIVE = 1;

    // PowerCfg GUIDs (stable across Windows versions)
    private static readonly Guid SUB_PROCESSOR = new("54533251-82be-4824-96c1-47b60b740d00");
    private static readonly Guid SETTING_MAX_CPU = new("bc5038f7-23e0-4960-96da-33abaf5935ec");
    private static readonly Guid SETTING_COOLING_POLICY = new("94d3a615-a899-4ac5-ae2b-e4d8f634367f");

    /// <summary> Zavolaj raz z UI threadu (napr. Form ctor). </summary>
    public static void Init(Control uiInvoker) => _uiInvoker = uiInvoker;

    /// <summary>
    /// Stav pre UI:
    /// - ak sme už v tomto behu aplikácie zapli tichý režim, vráti in-memory stav
    /// - inak sa ho pokúsi odhadnúť zo systému (Silent plán + nastavené limity CPU)
    /// </summary>
    public static bool GetState()
    {
        lock (_lock)
        {
            if (_enabled) return true;
        }

        // Best-effort inference (napr. po reštarte appky)
        try
        {
            var active = PowerCfg.GetActiveSchemeGuid();
            if (!string.Equals(active, SILENT_SCHEME_GUID, StringComparison.OrdinalIgnoreCase))
                return false;

            // Ak je Silent aktívny a CPU max je nastavené na naše QUIET hodnoty, považuj to za ON.
            var ac = PowerCfg.GetValueIndex(active, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: true);
            var dc = PowerCfg.GetValueIndex(active, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: false);

            return ac == QUIET_MAX_CPU_AC && dc == QUIET_MAX_CPU_DC;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// enabled=false -> restore original power settings + switch back to original power plan.
    /// enabled=true  -> apply quiet CPU max + passive cooling + switch to Silent plan.
    /// </summary>
    public static bool TrySet(bool enabled, out string message)
    {
        message = "";

        if (!IsAdministrator())
        {
            message = "Vyžaduje spustenie aplikácie ako správca (Administrator).";
            return false;
        }

        // Prevent double-ON / double-OFF from breaking snapshots/original scheme.
        var current = GetState();
        if (enabled == current)
        {
            // No-op, but treat as success
            return true;
        }

        try
        {
            void DoApply()
            {
                Apply(enabled);

                lock (_lock)
                {
                    _enabled = enabled;
                }
            }

            var inv = _uiInvoker;
            if (inv != null && !inv.IsDisposed)
            {
                if (inv.InvokeRequired) inv.Invoke((Action)DoApply);
                else DoApply();
            }
            else
            {
                DoApply();
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
        try { TrySet(false, out _); }
        catch { /* ignore */ }
    }

    private static void Apply(bool targetEnabled)
    {
        var active = PowerCfg.GetActiveSchemeGuid();
        if (string.IsNullOrWhiteSpace(active))
            throw new InvalidOperationException("Nepodarilo sa zistiť aktívny napájací plán (powercfg /getactivescheme).");

        var silentExists = PowerCfg.SchemeExists(SILENT_SCHEME_GUID);

        if (targetEnabled)
        {
            // Capture ORIGINAL plan ONLY on OFF -> ON transition.
            // If user calls ON twice, we must not overwrite original with Silent.
            if (string.IsNullOrWhiteSpace(_origPowerSchemeGuid))
                _origPowerSchemeGuid = active;

            if (!_hasOrigSnapshot && !string.IsNullOrWhiteSpace(_origPowerSchemeGuid))
            {
                TakeSnapshotForScheme(
                    schemeGuid: _origPowerSchemeGuid,
                    out _origMaxCpuAc, out _origMaxCpuDc,
                    out _origHasCoolingSnapshot, out _origCoolingAc, out _origCoolingDc,
                    out _hasOrigSnapshot
                );
            }

            // Snapshot silent scheme (best-effort) so we can restore it too
            if (silentExists && !_hasSilentSnapshot)
            {
                TakeSnapshotForScheme(
                    schemeGuid: SILENT_SCHEME_GUID,
                    out _silentMaxCpuAc, out _silentMaxCpuDc,
                    out _silentHasCoolingSnapshot, out _silentCoolingAc, out _silentCoolingDc,
                    out _hasSilentSnapshot
                );
            }

            // Apply quiet to whichever scheme will be active
            if (silentExists)
            {
                ApplyQuietToScheme(SILENT_SCHEME_GUID);
                PowerCfg.ReactivateScheme(SILENT_SCHEME_GUID);
            }
            else
            {
                ApplyQuietToScheme(active);
                PowerCfg.ReactivateScheme(active);
            }

            return;
        }

        // targetEnabled == false (restore)

        // 1) Restore Silent scheme if we changed it and have snapshot (best-effort)
        if (silentExists && _hasSilentSnapshot)
        {
            RestoreSnapshotForScheme(
                schemeGuid: SILENT_SCHEME_GUID,
                maxAc: _silentMaxCpuAc, maxDc: _silentMaxCpuDc,
                hasCooling: _silentHasCoolingSnapshot, coolAc: _silentCoolingAc, coolDc: _silentCoolingDc
            );
        }

        // 2) Restore ORIGINAL scheme values (even if it's not active right now)
        if (!string.IsNullOrWhiteSpace(_origPowerSchemeGuid) && _hasOrigSnapshot)
        {
            RestoreSnapshotForScheme(
                schemeGuid: _origPowerSchemeGuid,
                maxAc: _origMaxCpuAc, maxDc: _origMaxCpuDc,
                hasCooling: _origHasCoolingSnapshot, coolAc: _origCoolingAc, coolDc: _origCoolingDc
            );
        }

        // 3) Switch back to original power plan (if known)
        if (!string.IsNullOrWhiteSpace(_origPowerSchemeGuid))
        {
            try { PowerCfg.ReactivateScheme(_origPowerSchemeGuid); }
            catch { /* ignore - if plan no longer exists */ }
        }

        // 4) Clear runtime state so next ON captures original again
        _origPowerSchemeGuid = null;
        _hasOrigSnapshot = false;

        // (Silent snapshot can stay; no harm keeping it)
    }

    private static void TakeSnapshotForScheme(
        string? schemeGuid,
        out uint maxAc, out uint maxDc,
        out bool hasCooling, out uint coolAc, out uint coolDc,
        out bool hasSnapshot)
    {
        maxAc = maxDc = 0;
        coolAc = coolDc = 0;
        hasCooling = false;
        hasSnapshot = false;

        if (string.IsNullOrWhiteSpace(schemeGuid))
            return;

        // CPU max snapshot
        maxAc = PowerCfg.GetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: true);
        maxDc = PowerCfg.GetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: false);

        // Cooling policy snapshot (best-effort)
        try
        {
            coolAc = PowerCfg.GetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_COOLING_POLICY, isAc: true);
            coolDc = PowerCfg.GetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_COOLING_POLICY, isAc: false);
            hasCooling = true;
        }
        catch
        {
            hasCooling = false;
            coolAc = 0;
            coolDc = 0;
        }

        hasSnapshot = true;
    }

    private static void RestoreSnapshotForScheme(
        string schemeGuid,
        uint maxAc, uint maxDc,
        bool hasCooling, uint coolAc, uint coolDc)
    {
        // Restore CPU max (core)
        PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: true, maxAc);
        PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: false, maxDc);

        // Restore cooling (best-effort)
        if (hasCooling)
        {
            try
            {
                PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_COOLING_POLICY, isAc: true, coolAc);
                PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_COOLING_POLICY, isAc: false, coolDc);
            }
            catch
            {
                // ignore
            }
        }
    }

    private static void ApplyQuietToScheme(string schemeGuid)
    {
        // CPU max is the core behavior
        PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: true, QUIET_MAX_CPU_AC);
        PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_MAX_CPU, isAc: false, QUIET_MAX_CPU_DC);

        // Cooling policy (best-effort)
        try
        {
            PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_COOLING_POLICY, isAc: true, COOLING_PASSIVE);
            PowerCfg.SetValueIndex(schemeGuid, SUB_PROCESSOR, SETTING_COOLING_POLICY, isAc: false, COOLING_PASSIVE);
        }
        catch
        {
            // ignore
        }
    }

    private static bool IsAdministrator()
    {
        try
        {
            var identity = WindowsIdentity.GetCurrent();
            var principal = new WindowsPrincipal(identity);
            return principal.IsInRole(WindowsBuiltInRole.Administrator);
        }
        catch
        {
            return false;
        }
    }

    // ============================
    // PowerCfg helper (process runner + parsing)
    // ============================
    private static class PowerCfg
    {
        public static string GetActiveSchemeGuid()
        {
            var (code, output) = Run("/getactivescheme");
            if (code != 0) return "";

            var m = Regex.Match(output, @"([0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12})");
            return m.Success ? m.Groups[1].Value : "";
        }

        public static bool SchemeExists(string schemeGuid)
        {
            if (string.IsNullOrWhiteSpace(schemeGuid)) return false;

            var (code, output) = Run("/list");
            if (code != 0) return false;

            return output.IndexOf(schemeGuid, StringComparison.OrdinalIgnoreCase) >= 0;
        }

        /// <summary>
        /// IMPORTANT: Na niektorých OEM plánoch (napr. "Turbo") /getacvalueindex vracia Invalid Parameters,
        /// ale /query funguje. Preto čítame hodnoty vždy cez /query.
        /// </summary>
        public static uint GetValueIndex(string schemeGuid, Guid subgroup, Guid setting, bool isAc)
        {
            var (code, output) = Run($"/query {schemeGuid} {subgroup} {setting}");
            if (code != 0)
                throw BuildPowerCfgException(
                    $"powercfg zlyhal pri čítaní hodnoty ({(isAc ? "AC" : "DC")}) cez /query.",
                    output
                );

            var acMatch = Regex.Match(output, @"Current\s+AC\s+Power\s+Setting\s+Index:\s*0x([0-9a-fA-F]+)", RegexOptions.IgnoreCase);
            var dcMatch = Regex.Match(output, @"Current\s+DC\s+Power\s+Setting\s+Index:\s*0x([0-9a-fA-F]+)", RegexOptions.IgnoreCase);

            if (isAc)
            {
                if (!acMatch.Success)
                    throw new InvalidOperationException($"Nepodarilo sa nájsť AC index v powercfg /query výstupe:\n{output}".Trim());

                return uint.Parse(acMatch.Groups[1].Value, NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            }
            else
            {
                if (!dcMatch.Success)
                    throw new InvalidOperationException($"Nepodarilo sa nájsť DC index v powercfg /query výstupe:\n{output}".Trim());

                return uint.Parse(dcMatch.Groups[1].Value, NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            }
        }

        public static void SetValueIndex(string schemeGuid, Guid subgroup, Guid setting, bool isAc, uint value)
        {
            var cmd = isAc
                ? $"/setacvalueindex {schemeGuid} {subgroup} {setting} {value}"
                : $"/setdcvalueindex {schemeGuid} {subgroup} {setting} {value}";

            var (code, output) = Run(cmd);
            if (code != 0)
                throw BuildPowerCfgException(
                    $"powercfg zlyhal pri nastavovaní hodnoty ({(isAc ? "AC" : "DC")}).",
                    output
                );
        }

        public static void ReactivateScheme(string schemeGuid)
        {
            var (code, output) = Run($"/setactive {schemeGuid}");
            if (code != 0)
                throw BuildPowerCfgException("powercfg /setactive zlyhal.", output);
        }

        private static Exception BuildPowerCfgException(string prefix, string output)
        {
            var o = (output ?? "").Trim();

            if (o.IndexOf("Invalid Parameters", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                return new InvalidOperationException(
                    $"{prefix}\n" +
                    "powercfg hlási 'Invalid Parameters'. To typicky znamená, že dané nastavenie nie je dostupné pre aktuálny plán/edíciu Windows/ovládač.\n" +
                    $"Výstup:\n{o}"
                );
            }

            return new InvalidOperationException($"{prefix}\nMožno chýbajú práva správcu.\nVýstup:\n{o}".Trim());
        }

        private static (int exitCode, string output) Run(string args)
        {
            args = (args ?? "").Trim();

            var psi = new ProcessStartInfo
            {
                FileName = "powercfg.exe",
                Arguments = args,
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            };

            using var p = Process.Start(psi);
            if (p == null) return (-1, "Process.Start(powercfg) zlyhal.");

            var stdout = p.StandardOutput.ReadToEnd();
            var stderr = p.StandardError.ReadToEnd();

            if (!p.WaitForExit(5000))
            {
                try { p.Kill(entireProcessTree: true); } catch { /* ignore */ }
                return (-2, "powercfg timeout (5s).");
            }

            var merged = (stdout + "\n" + stderr).Trim();
            return (p.ExitCode, merged);
        }
    }
}
