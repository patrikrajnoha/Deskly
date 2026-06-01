using System;
using System.Drawing;
using System.Security.Principal;
using System.Windows.Forms;

namespace DesklyPC
{
    internal static class Program
    {
        private static NotifyIcon? _tray;
        private static Form1? _mainForm;

        // Form1 to používa na rozlíšenie "hide do tray" vs skutočný exit
        internal static bool IsRealExit { get; set; } = false;

        [STAThread]
        static void Main()
        {
            ApplicationConfiguration.Initialize();

            _mainForm = new Form1();

            _tray = new NotifyIcon
            {
                Icon = SystemIcons.Application, // TODO: nahraď vlastnou .ico
                Text = "DesklyPC (beží na pozadí)",
                Visible = true
            };

            _tray.DoubleClick += (_, __) => ShowMainWindow();

            var menu = new ContextMenuStrip();
            menu.Items.Add("Otvoriť", null, (_, __) => ShowMainWindow());
            menu.Items.Add(new ToolStripSeparator());

            menu.Items.Add("Ukončiť DesklyPC", null, (_, __) =>
            {
                IsRealExit = true;

                if (_tray != null)
                {
                    _tray.Visible = false;
                    _tray.Dispose();
                    _tray = null;
                }

                if (_mainForm != null && !_mainForm.IsDisposed)
                    _mainForm.Close(); // spustí FormClosing -> reálny cleanup
                else
                    Application.Exit();
            });

            _tray.ContextMenuStrip = menu;

            Application.ApplicationExit += (_, __) =>
            {
                if (_tray != null)
                {
                    _tray.Visible = false;
                    _tray.Dispose();
                    _tray = null;
                }
            };

            Application.Run(_mainForm);
        }

        internal static void RestartAsAdmin()
        {
            // ✅ Ak už bežíme ako admin, nerob nič
            if (IsRunningAsAdmin())
                return;

            try
            {
                var exe = Application.ExecutablePath;

                var psi = new System.Diagnostics.ProcessStartInfo
                {
                    FileName = exe,
                    UseShellExecute = true,
                    Verb = "runas"
                };

                System.Diagnostics.Process.Start(psi);

                // zavri aktuálnu inštanciu
                IsRealExit = true;

                if (_tray != null)
                {
                    _tray.Visible = false;
                    _tray.Dispose();
                    _tray = null;
                }

                Application.Exit();
            }
            catch
            {
                // user dal "No" na UAC alebo iná chyba – ignor
            }
        }

        private static bool IsRunningAsAdmin()
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

        private static void ShowMainWindow()
        {
            if (_mainForm == null || _mainForm.IsDisposed) return;

            _mainForm.ShowInTaskbar = true;

            if (!_mainForm.Visible)
                _mainForm.Show();

            if (_mainForm.WindowState == FormWindowState.Minimized)
                _mainForm.WindowState = FormWindowState.Normal;

            _mainForm.Activate();
        }
    }
}
