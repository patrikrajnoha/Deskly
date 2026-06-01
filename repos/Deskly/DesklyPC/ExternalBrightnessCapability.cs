using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;

namespace DesklyPC;

/// <summary>
/// Best-effort test, či externý monitor podporuje zmenu jasu cez DDC/CI (VCP 0x10).
/// Používa dxva2.dll (Physical Monitor API).
/// </summary>
public static class ExternalBrightnessCapability
{
    // VCP code for luminance / brightness
    private const byte VCP_BRIGHTNESS = 0x10;

    public sealed class MonitorResult
    {
        /// <summary>1-based index: ddc_1, ddc_2, ...</summary>
        public int DdcIndex { get; set; }

        /// <summary>Popis z Windows (často "Generic PnP Monitor", niekedy obsahuje model).</summary>
        public string Name { get; set; } = "";

        /// <summary>True ak GetVCPFeature pre VCP 0x10 prešiel.</summary>
        public bool DdcciAvailable { get; set; }

        /// <summary>Pre jednoduchosť: rovnaké ako DdcciAvailable.</summary>
        public bool CanGet { get; set; }

        /// <summary>True ak SetVCPFeature pre VCP 0x10 prešlo (testuje sa bez viditeľnej zmeny – nastaví sa rovnaká hodnota).</summary>
        public bool CanSet { get; set; }

        /// <summary>Doplňujúca poznámka pri probléme.</summary>
        public string Note { get; set; } = "";
    }

    public sealed class CapabilityResult
    {
        public bool Supported { get; set; }
        public List<MonitorResult> Monitors { get; set; } = new();
    }

    /// <summary>
    /// Otestuje všetky pripojené monitory a vráti výsledok.
    /// Supported = true ak aspoň jeden monitor vie Get alebo Set jas cez VCP 0x10.
    /// </summary>
    public static CapabilityResult Test()
    {
        var result = new CapabilityResult();
        var monitors = new List<MonitorResult>();

        int ddcIndex = 0;

        try
        {
            EnumDisplayMonitors(IntPtr.Zero, IntPtr.Zero,
                (hMon, _, _, _) =>
                {
                    try
                    {
                        if (!GetNumberOfPhysicalMonitorsFromHMONITOR(hMon, out var count) || count == 0)
                            return true;

                        var phys = new PHYSICAL_MONITOR[count];
                        if (!GetPhysicalMonitorsFromHMONITOR(hMon, count, phys))
                            return true;

                        try
                        {
                            for (int i = 0; i < phys.Length; i++)
                            {
                                ddcIndex++;

                                var hPhys = phys[i].hPhysicalMonitor;

                                var name = (phys[i].szPhysicalMonitorDescription ?? "").Trim();
                                if (string.IsNullOrWhiteSpace(name))
                                    name = "Unknown monitor";

                                // 1) Get test
                                bool ddcci = TryGetBrightness(hPhys, out var current, out var maximum, out var noteGet);
                                bool canGet = ddcci;

                                // 2) Set test (bez viditeľnej zmeny – nastavíme rovnakú hodnotu)
                                bool canSet = false;
                                string note = "";

                                if (!ddcci)
                                {
                                    note = string.IsNullOrWhiteSpace(noteGet) ? "DDC/CI alebo VCP 0x10 nedostupné" : noteGet;
                                }
                                else
                                {
                                    canSet = TrySetBrightness(hPhys, current, out var noteSet);
                                    if (!canSet && !string.IsNullOrWhiteSpace(noteSet))
                                        note = noteSet;

                                    if (maximum <= 0 && string.IsNullOrWhiteSpace(note))
                                        note = "Neznámy rozsah jasu";
                                }

                                monitors.Add(new MonitorResult
                                {
                                    DdcIndex = ddcIndex,
                                    Name = name,
                                    DdcciAvailable = ddcci,
                                    CanGet = canGet,
                                    CanSet = canSet,
                                    Note = note
                                });
                            }
                        }
                        finally
                        {
                            // Always free physical monitor handles
                            try { DestroyPhysicalMonitors((uint)phys.Length, phys); } catch { /* ignore */ }
                        }
                    }
                    catch
                    {
                        // ignore this HMONITOR
                    }

                    return true; // continue enumeration
                }, IntPtr.Zero);
        }
        catch
        {
            // ignore
        }

        result.Monitors = monitors;
        result.Supported = monitors.Any(m => m.DdcciAvailable && (m.CanGet || m.CanSet));
        return result;
    }

    private static bool TryGetBrightness(IntPtr hPhys, out uint current, out uint maximum, out string note)
    {
        current = 0;
        maximum = 0;
        note = "";

        try
        {
            if (!GetVCPFeatureAndVCPFeatureReply(
                    hPhys,
                    VCP_BRIGHTNESS,
                    out _,
                    out var cur,
                    out var max))
            {
                note = "GetVCPFeature zlyhal";
                return false;
            }

            current = cur;
            maximum = max;
            return true;
        }
        catch (Exception ex)
        {
            note = ex.Message;
            return false;
        }
    }

    private static bool TrySetBrightness(IntPtr hPhys, uint value, out string note)
    {
        note = "";
        try
        {
            if (!SetVCPFeature(hPhys, VCP_BRIGHTNESS, value))
            {
                note = "SetVCPFeature zlyhal";
                return false;
            }
            return true;
        }
        catch (Exception ex)
        {
            note = ex.Message;
            return false;
        }
    }

    // -------------------------
    // P/Invoke: user32 + dxva2
    // -------------------------

    private delegate bool MonitorEnumProc(IntPtr hMonitor, IntPtr hdcMonitor, IntPtr lprcMonitor, IntPtr dwData);

    [DllImport("user32.dll")]
    private static extern bool EnumDisplayMonitors(IntPtr hdc, IntPtr lprcClip, MonitorEnumProc lpfnEnum, IntPtr dwData);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct PHYSICAL_MONITOR
    {
        public IntPtr hPhysicalMonitor;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string szPhysicalMonitorDescription;
    }

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool GetNumberOfPhysicalMonitorsFromHMONITOR(IntPtr hMonitor, out uint pdwNumberOfPhysicalMonitors);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool GetPhysicalMonitorsFromHMONITOR(IntPtr hMonitor, uint dwPhysicalMonitorArraySize, [Out] PHYSICAL_MONITOR[] pPhysicalMonitorArray);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool DestroyPhysicalMonitors(uint dwPhysicalMonitorArraySize, [In] PHYSICAL_MONITOR[] pPhysicalMonitorArray);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool GetVCPFeatureAndVCPFeatureReply(
        IntPtr hMonitor,
        byte bVCPCode,
        out uint pvct,
        out uint pdwCurrentValue,
        out uint pdwMaximumValue);

    [DllImport("dxva2.dll", SetLastError = true)]
    private static extern bool SetVCPFeature(IntPtr hMonitor, byte bVCPCode, uint dwNewValue);
}
