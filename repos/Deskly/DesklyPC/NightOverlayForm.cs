using System;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DesklyPC;

/// <summary>
/// Click-through always-on-top overlay used for "night mode".
/// Uses Opacity to control intensity (no GPU gamma / admin rights needed).
/// </summary>
internal sealed class NightOverlayForm : Form
{
    public NightOverlayForm(Rectangle bounds)
    {
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        StartPosition = FormStartPosition.Manual;
        Bounds = bounds;
        TopMost = true;

        // Warm orange tint. Intensity is controlled via Opacity.
        BackColor = Color.FromArgb(255, 255, 140, 0);
    }

    // Click-through + hide from Alt-Tab
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
        // Limit so it never becomes fully opaque (keeps it usable).
        opacity01 = Math.Clamp(opacity01, 0.0, 0.85);
        Opacity = opacity01;
    }

    public static Rectangle[] GetAllScreenBounds()
        => Screen.AllScreens.Select(s => s.Bounds).ToArray();
}
