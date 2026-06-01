using System.Diagnostics;
using System.Runtime.InteropServices;
using NAudio.CoreAudioApi;

namespace DesklyPC;

public static class SystemActions
{
    // ===== LOCK =====
    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool LockWorkStation();

    public static bool LockPc() => LockWorkStation();

    // ===== SLEEP =====
    // SetSuspendState(Hibernate, ForceCritical, DisableWakeEvent)
    [DllImport("powrprof.dll", SetLastError = true)]
    private static extern bool SetSuspendState(bool hibernate, bool forceCritical, bool disableWakeEvent);

    public static bool SleepPc()
    {
        try
        {
            // false = sleep (nie hibernate)
            return SetSuspendState(false, true, false);
        }
        catch
        {
            // fallback (ak by dll call zlyhal)
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "rundll32.exe",
                    Arguments = "powrprof.dll,SetSuspendState 0,1,0",
                    CreateNoWindow = true,
                    UseShellExecute = false
                });
                return true;
            }
            catch
            {
                return false;
            }
        }
    }

    // ===== SHUTDOWN / RESTART =====
    public static bool ShutdownPc()
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "shutdown.exe",
                Arguments = "/s /f /t 0",
                CreateNoWindow = true,
                UseShellExecute = false
            });
            return true;
        }
        catch { return false; }
    }

    public static bool RestartPc()
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = "shutdown.exe",
                Arguments = "/r /f /t 0",
                CreateNoWindow = true,
                UseShellExecute = false
            });
            return true;
        }
        catch { return false; }
    }

    // ===== AUDIO (NAudio - default playback device) =====
    private static MMDevice GetDefaultOutputDevice()
    {
        var enumerator = new MMDeviceEnumerator();
        return enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
    }

    /// <summary>Zistí hlasitosť 0-100</summary>
    public static int GetVolume()
    {
        using var device = GetDefaultOutputDevice();
        var v = device.AudioEndpointVolume.MasterVolumeLevelScalar; // 0..1
        var percent = (int)Math.Round(v * 100);
        return Math.Clamp(percent, 0, 100);
    }

    /// <summary>Nastaví hlasitosť 0-100</summary>
    public static void SetVolume(int volume)
    {
        volume = Math.Clamp(volume, 0, 100);
        using var device = GetDefaultOutputDevice();
        device.AudioEndpointVolume.MasterVolumeLevelScalar = volume / 100f;
    }

    /// <summary>Zistí, či je systém mute</summary>
    public static bool GetMute()
    {
        using var device = GetDefaultOutputDevice();
        return device.AudioEndpointVolume.Mute;
    }

    /// <summary>Prepne mute (toggle) a vráti nový stav</summary>
    public static bool ToggleMute()
    {
        using var device = GetDefaultOutputDevice();
        device.AudioEndpointVolume.Mute = !device.AudioEndpointVolume.Mute;
        return device.AudioEndpointVolume.Mute;
    }
}
