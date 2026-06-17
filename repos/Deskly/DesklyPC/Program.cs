using System;
using System.Drawing;
using System.IO;
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
                Icon = LoadTrayIcon(),
                Text = "Deskly Host",
                Visible = true
            };

            _tray.DoubleClick += (_, __) => ShowMainWindow();

            var menu = new ContextMenuStrip();
            menu.Items.Add("Open Deskly", null, (_, __) => ShowMainWindow());
            menu.Items.Add("Start", null, (_, __) =>
            {
                if (_mainForm == null || _mainForm.IsDisposed) return;
                _mainForm.StartHost();
            });
            menu.Items.Add("Stop", null, (_, __) =>
            {
                if (_mainForm == null || _mainForm.IsDisposed) return;
                _mainForm.StopHost();
            });
            menu.Items.Add("Pair", null, (_, __) =>
            {
                if (_mainForm == null || _mainForm.IsDisposed) return;
                _mainForm.GeneratePairingPin();
                ShowMainWindow();
            });
            menu.Items.Add("Copy setup", null, (_, __) =>
            {
                if (_mainForm == null || _mainForm.IsDisposed) return;
                _mainForm.CopyPairingInfoToClipboard();
            });
            menu.Items.Add("Copy IP", null, (_, __) =>
            {
                if (_mainForm == null || _mainForm.IsDisposed) return;
                _mainForm.CopyIpToClipboard();
            });
            menu.Items.Add(new ToolStripSeparator());

            menu.Items.Add("Exit", null, (_, __) =>
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

            _mainForm.ShowHostWindow();
        }

        private static Icon LoadTrayIcon()
        {
            try
            {
                var iconPath = Path.Combine(AppContext.BaseDirectory, "icon.ico");
                if (File.Exists(iconPath))
                    return new Icon(iconPath);
            }
            catch
            {
                // fallback below
            }

            return SystemIcons.Application;
        }
    }
}
