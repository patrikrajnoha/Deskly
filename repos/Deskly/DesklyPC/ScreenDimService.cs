using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DesklyPC;

public static class ScreenDimService
{
    private static readonly object LockObj = new();
    private static Control? _uiInvoker;
    private static readonly List<DimOverlayForm> Overlays = new();
    private static int _dim;

    public static void Init(Control uiInvoker) => _uiInvoker = uiInvoker;

    public static int GetState()
    {
        lock (LockObj) return _dim;
    }

    public static bool TrySet(int dim0to100, out string message)
    {
        message = "";
        dim0to100 = Math.Clamp(dim0to100, 0, 100);

        try
        {
            void Apply()
            {
                lock (LockObj) _dim = dim0to100;

                if (dim0to100 <= 0)
                {
                    CloseOverlays();
                    return;
                }

                var bounds = Screen.AllScreens.Select(s => s.Bounds).ToArray();
                if (bounds.Length == 0) bounds = new[] { SystemInformation.VirtualScreen };

                if (Overlays.Count != bounds.Length)
                {
                    CloseOverlays();
                    foreach (var b in bounds)
                    {
                        var f = new DimOverlayForm(b);
                        Overlays.Add(f);
                        f.Show();
                    }
                }

                var opacity = Math.Clamp(dim0to100 / 100.0 * 0.85, 0.0, 0.85);
                for (var i = 0; i < Overlays.Count; i++)
                {
                    Overlays[i].Bounds = bounds[Math.Min(i, bounds.Length - 1)];
                    Overlays[i].SetOpacity01(opacity);
                    if (!Overlays[i].Visible) Overlays[i].Show();
                }
            }

            var inv = _uiInvoker;
            if (inv != null && !inv.IsDisposed)
            {
                if (inv.InvokeRequired) inv.Invoke((Action)Apply);
                else Apply();
            }
            else
            {
                Apply();
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
        try { TrySet(0, out _); }
        catch { /* ignore */ }
    }

    private static void CloseOverlays()
    {
        foreach (var f in Overlays.ToArray())
        {
            try { f.Close(); f.Dispose(); } catch { /* ignore */ }
        }
        Overlays.Clear();
    }

    private sealed class DimOverlayForm : Form
    {
        public DimOverlayForm(Rectangle bounds)
        {
            FormBorderStyle = FormBorderStyle.None;
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.Manual;
            Bounds = bounds;
            TopMost = true;
            BackColor = Color.Black;
        }

        protected override CreateParams CreateParams
        {
            get
            {
                const int WS_EX_TRANSPARENT = 0x20;
                const int WS_EX_LAYERED = 0x80000;
                const int WS_EX_TOOLWINDOW = 0x80;

                var cp = base.CreateParams;
                cp.ExStyle |= WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW;
                return cp;
            }
        }

        protected override bool ShowWithoutActivation => true;

        public void SetOpacity01(double opacity01)
        {
            Opacity = Math.Clamp(opacity01, 0.0, 0.85);
        }
    }
}
