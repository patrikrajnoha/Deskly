using System.Management;

public static class WmiBrightnessService
{
    public static bool IsSupported()
    {
        try
        {
            using var s = new ManagementObjectSearcher(
                "root\\WMI",
                "SELECT * FROM WmiMonitorBrightness"
            );
            return s.Get().Count > 0;
        }
        catch { return false; }
    }

    public static bool TrySet(int value, out string message)
    {
        message = "";
        value = Math.Clamp(value, 0, 100);

        try
        {
            using var s = new ManagementObjectSearcher(
                "root\\WMI",
                "SELECT * FROM WmiMonitorBrightnessMethods"
            );

            foreach (ManagementObject o in s.Get())
            {
                o.InvokeMethod(
                    "WmiSetBrightness",
                    new object[] { 0u, (byte)value }
                );
                return true;
            }

            message = "No WMI brightness methods";
            return false;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
    }
}
