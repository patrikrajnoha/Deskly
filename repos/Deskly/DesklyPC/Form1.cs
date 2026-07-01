// Deskly Host: UI plus request routing.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;
using System.Drawing;
using System.Drawing.Drawing2D;

namespace DesklyPC;

public partial class Form1 : Form
{
    private readonly TcpJsonServer _server = new();
    private readonly BluetoothJsonServer _bluetoothServer = new();
    private readonly PairingManager _pairing = new();
    private readonly SleepTimerService _sleepTimer = new();

    // ✅ UDP discovery
    private UdpDiscoveryServer? _discovery;
    private const int UDP_DISCOVERY_PORT = 5051;
    private string _serverId = "";

    private readonly System.Windows.Forms.Timer _uiTimer = new();
    private readonly System.Windows.Forms.Timer _sleepUiTimer = new();

    private string? _lastGeneratedPin;

    // ✅ track running state (aby sme neštartovali 2x)
    private bool _isRunning = false;

    // Night
    private int _lastNightNonZeroIntensity = 35;

    // Quiet (UI-side cache – aby toggle fungoval aj keď QuietModeService.GetState() niekedy vracia starú hodnotu)
    private bool _quietUiEnabled = false;

    // Smooth transitions (external brightness + night intensity)
    private readonly BrightnessSmoother _brightnessSmoother;
    private readonly NightSmoother _nightSmoother;

    // =========================
    // LOG SETTINGS
    // =========================
    private static readonly bool DEBUG_LOG = false;
    private static readonly bool PERF_DIAGNOSTICS =
        string.Equals(Environment.GetEnvironmentVariable("DESKLY_PERF_DIAGNOSTICS"), "1", StringComparison.OrdinalIgnoreCase);
    private const bool ADVANCED_UI = true;

    private enum LogLevel { Info, Ok, Warn, Error }

    // last states to avoid spam
    private (bool running, int remainingSeconds, string action) _lastTimerStatus = (false, -1, "");
    private DateTime _lastThrottledLogAt = DateTime.MinValue;
    private string _lastThrottledLogKey = "";
    private bool? _lastAuthOk = null;

    // Unknown type throttling
    private DateTime _lastUnknownLogAt = DateTime.MinValue;
    private string _lastUnknownType = "";

    private readonly PowerCommandGuard _powerCommandGuard = new();

    private bool _logsVisible = false;
    private readonly Size _compactSize = new(820, 620);
    private readonly Size _logsSize = new(820, 720);
    private DateTime _lastClientPulseAt = DateTime.MinValue;
    private bool _syncingStartupCheck;
    private DateTime _lastPerfLogAt = DateTime.UtcNow;
    private TimeSpan _lastPerfCpu = TimeSpan.Zero;
    private int _perfRequestCount = 0;
    private int _perfHighFrequencyCount = 0;

    public Form1()
    {
        InitializeComponent();

        // --- Apple-like status dot (circle) ---
        try
        {
            if (lblStatusChip != null)
            {
                using var gp = new GraphicsPath();
                gp.AddEllipse(0, 0, lblStatusChip.Width, lblStatusChip.Height);
                lblStatusChip.Region = new Region(gp);
                lblStatusChip.Text = "";
            }

            if (lblStatusText != null)
                lblStatusText.Region = new Region(RoundedRect(new Rectangle(0, 0, lblStatusText.Width, lblStatusText.Height), 12));
        }
        catch { /* ignore */ }

        Text = "Deskly Host";
        MinimizeBox = true;
        ShowInTaskbar = true;

        // Minimize-to-tray (keeps server running)
        Resize += (_, __) =>
        {
            if (WindowState == FormWindowState.Minimized)
            {
                Hide();
                ShowInTaskbar = false;
            }
        };

        // ✅ stable server id + discovery init
        _serverId = LoadOrCreateServerId();
        var pcName = Environment.MachineName;
        _discovery = new UdpDiscoveryServer(_serverId, pcName);
        _discovery.Log += AppendLog;

        // Night/Quiet services
        NightModeService.Init(this);
        ScreenDimService.Init(this);
        QuietModeService.Init(this);

        // Smoothers (inject logger so failures are visible)
        _brightnessSmoother = new BrightnessSmoother((lvl, msg) => WriteLog(lvl, msg));
        _nightSmoother = new NightSmoother();

        // Brightness smoother starts only when a request arrives. Night smoothing is currently handled by NightModeService.

        // Sync UI cache with service at startup
        _quietUiEnabled = QuietModeService.GetState();

        numPort.Minimum = 1024;
        numPort.Maximum = 65535;
        numPort.Value = 5050;

        _server.Log += AppendLog;
        _server.HandleRequest += HandleIncomingRequest;
        _bluetoothServer.Log += AppendLog;
        _bluetoothServer.HandleRequest += HandleIncomingRequest;

        lblIpValue.Text = GetLocalIpv4() ?? "unknown";
        SyncStartupUi();
        RefreshHostStatus();
        RefreshPairingStatus();
        RefreshServicesStatus();
        SetRunningUi(false);

        InitLogToggle();

        // ✅ AUTO START servera po spustení appky
        Shown += (_, __) => StartServerAndDiscovery();

        // PIN countdown (1s)
        _uiTimer.Interval = 1000;
        _uiTimer.Tick += (_, _) =>
        {
            if (_pairing.HasValidPin)
            {
                var sec = _pairing.PinSecondsRemaining();
                if (sec > 0)
                {
                    lblPinValue.Text = string.IsNullOrWhiteSpace(_lastGeneratedPin)
                        ? "------"
                        : _lastGeneratedPin;
                    RefreshPairingStatus();
                }
                else
                {
                    MarkPinExpired();
                }
            }
            else if (!string.IsNullOrWhiteSpace(_lastGeneratedPin))
            {
                MarkPinExpired();
            }
        };
        _uiTimer.Start();

        // ✅ Timer UI log: len zmeny + posledných 10s
        _sleepUiTimer.Interval = 1000;
        _sleepUiTimer.Tick += (_, _) =>
        {
            var st = _sleepTimer.GetStatus();
            var action = st.action ?? "";
            if (!st.running && !_lastTimerStatus.running)
            {
                _sleepUiTimer.Stop();
                return;
            }

            var changed = st.running != _lastTimerStatus.running ||
                          action != _lastTimerStatus.action ||
                          (st.running && st.remainingSeconds != _lastTimerStatus.remainingSeconds);

            if (st.running != _lastTimerStatus.running || action != _lastTimerStatus.action)
            {
                if (st.running)
                    LogInfo($"Timer spustený: {HumanTimerAction(action)} (zostáva {FormatSeconds(st.remainingSeconds)})");
                else
                    LogInfo("Timer zastavený");
            }

            if (st.running && st.remainingSeconds <= 10 && st.remainingSeconds != _lastTimerStatus.remainingSeconds)
            {
                LogWarn($"Timer: {HumanTimerAction(action)} za {st.remainingSeconds}s");
            }

            _lastTimerStatus = (st.running, st.remainingSeconds, action);
            if (changed || st.running)
                RefreshServicesStatus();
        };
        // Started only while a sleep/shutdown timer is active.

        // načítaj night state
        var (_, inten) = NightModeService.GetState();
        if (inten > 0) _lastNightNonZeroIntensity = inten;
        RefreshServicesStatus();

        FormClosing += (_, e) =>
        {
            // ✅ Klik na X = iba schovať do tray
            if (!Program.IsRealExit && e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
                ShowInTaskbar = false;
                return;
            }

            _uiTimer.Stop();
            _sleepUiTimer.Stop();
            _sleepTimer.Cancel();

            _discovery?.Stop();
            _server.Stop();

            if (e.CloseReason == CloseReason.WindowsShutDown)
                return;

            NightModeService.Shutdown();
            ScreenDimService.Shutdown();
            _brightnessSmoother.Stop();
            _nightSmoother.Stop();

            QuietModeService.Shutdown();
        };
    }

    // =========================
    // UI helper
    // =========================
    private void TrySetText(string controlName, string text)
    {
        try
        {
            var c = Controls.Find(controlName, true).FirstOrDefault();
            if (c is Label lbl) lbl.Text = text;
            else if (c is Button btn) btn.Text = text;
            else if (c is TextBox tb) tb.Text = text;
        }
        catch { /* ignore */ }
    }

    private bool TrySetClipboardText(string? text, out string message)
    {
        var safeText = text ?? "";
        if (string.IsNullOrEmpty(safeText))
        {
            message = "No text";
            return false;
        }

        try
        {
            void SetText() => Clipboard.SetText(safeText);

            if (InvokeRequired)
                Invoke((Action)SetText);
            else
                SetText();

            message = "OK";
            return true;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
    }

    private static bool TryOpenWebUrl(string? url, out string message, out string host)
        => WebOpenService.TryOpenWebUrl(url, out message, out host);

    private void RefreshHostStatus()
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(RefreshHostStatus));
            return;
        }

        var tcpPort = (int)numPort.Value;
        lblTcpValue.Text = tcpPort.ToString();
        lblUdpValue.Text = _isRunning ? $"{UDP_DISCOVERY_PORT} / BT {(_bluetoothServer.IsRunning ? "On" : "Off")}" : "Off";
    }

    private void RefreshPairingStatus()
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(RefreshPairingStatus));
            return;
        }

        lblAuthorizedValue.Text = ShortUiText(_pairing.LastAuthorizedDeviceName, 24)
            ?? (_pairing.ValidTokensCount > 0 ? $"{_pairing.ValidTokensCount} paired" : "No phone");

        if (!_isRunning)
        {
            lblPairStatus.Text = "Stopped";
            lblPairInstruction.Text = "Start Deskly to pair.";
            btnPair.Text = "Pair";
            btnForgetPairing.Enabled = _pairing.ValidTokensCount > 0 || _pairing.HasValidPin;
            UpdateHostStatusPresentation();
            return;
        }

        if (_pairing.HasValidPin)
        {
            lblPairStatus.Text = $"PIN active - {_pairing.PinSecondsRemaining()}s";
            lblPairInstruction.Text = "Enter this PIN in Deskly.";
            btnPair.Text = "New PIN";
            btnForgetPairing.Enabled = true;
            UpdateHostStatusPresentation();
            return;
        }

        lblPinValue.Text = "------";
        lblPairStatus.Text = _pairing.ValidTokensCount > 0 ? "Paired" : "Pair required";
        lblPairInstruction.Text = _pairing.ValidTokensCount > 0
            ? "Ready for your phone."
            : "Pair a phone to control this PC.";
        btnPair.Text = "Pair";
        btnForgetPairing.Enabled = _pairing.ValidTokensCount > 0;
        UpdateHostStatusPresentation();
    }

    private void RefreshServicesStatus()
    {
        // Service state is intentionally controlled from Android and kept out of the compact host UI.
    }

    private void SetLastConnection(string value)
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(() => SetLastConnection(value)));
            return;
        }

        lblLastConnectionValue.Text = value;
    }

    private void SetClientStatus(string value, Color? color = null)
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(() => SetClientStatus(value, color)));
            return;
        }

        var display = value switch
        {
            "No client" => "No phone",
            "Unauthorized" => "Pair failed",
            "Authorized" => "Paired",
            _ => value
        };

        lblClientStatus.Text = display;
        lblClientStatus.ForeColor = color ?? Color.FromArgb(244, 246, 248);

        if (lblLastClientValue != null)
        {
            lblLastClientValue.Text = display;
            lblLastClientValue.ForeColor = color ?? Color.FromArgb(244, 246, 248);
        }
    }

    private void PulseClientCard()
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(PulseClientCard));
            return;
        }

        _lastClientPulseAt = DateTime.UtcNow;
        if (cardServer != null)
            cardServer.BorderColor = Color.FromArgb(10, 132, 255);

        var timer = new System.Windows.Forms.Timer { Interval = 900 };
        timer.Tick += (_, __) =>
        {
            timer.Stop();
            timer.Dispose();

            if ((DateTime.UtcNow - _lastClientPulseAt).TotalMilliseconds >= 850 && cardServer != null)
                cardServer.BorderColor = Color.FromArgb(42, 48, 56);
        };
        timer.Start();
    }

    private void SetLastError(string value)
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(() => SetLastError(value)));
            return;
        }

        lblLastErrorValue.Text = value;
        lblLastErrorValue.ForeColor = string.Equals(value, "No errors", StringComparison.OrdinalIgnoreCase)
            ? Color.FromArgb(174, 183, 194)
            : Color.FromArgb(244, 124, 124);
    }

    private void SetStatusUi(string text, Color dotColor, Color textColor)
    {
        if (lblStatusChip != null)
        {
            lblStatusChip.Text = "";
            lblStatusChip.BackColor = dotColor;
        }

        if (lblStatusText != null)
        {
            lblStatusText.Text = text;
            lblStatusText.ForeColor = textColor;
        }
    }

    private void MarkPinExpired()
    {
        lblPinValue.Text = "------";
        _lastGeneratedPin = null;
        RefreshPairingStatus();
        lblPairStatus.Text = "PIN expired";
        btnPair.Text = "Pair";
        SetLastConnection($"{DateTime.Now:HH:mm:ss} PIN expired");
    }

    private void UpdateHostStatusPresentation()
    {
        if (!_isRunning)
        {
            lblHeaderSubtitle.Text = "Start Deskly to connect your phone.";
            SetStatusUi("Stopped", Color.FromArgb(139, 152, 170), Color.FromArgb(174, 183, 194));
            return;
        }

        if (_pairing.ValidTokensCount <= 0 && !_pairing.HasValidPin)
        {
            lblHeaderSubtitle.Text = "Pair your phone to control this PC.";
            SetStatusUi("Pair required", Color.FromArgb(232, 186, 93), Color.FromArgb(246, 213, 152));
            return;
        }

        lblHeaderSubtitle.Text = "Ready for your phone.";
        SetStatusUi("Running", Color.FromArgb(82, 164, 115), Color.FromArgb(192, 236, 207));
    }

    private static GraphicsPath RoundedRect(Rectangle bounds, int radius)
    {
        var path = new GraphicsPath();
        var r = Math.Max(0, radius);
        if (r == 0)
        {
            path.AddRectangle(bounds);
            path.CloseFigure();
            return path;
        }

        var d = r * 2;
        path.AddArc(bounds.X, bounds.Y, d, d, 180, 90);
        path.AddArc(bounds.Right - d, bounds.Y, d, d, 270, 90);
        path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90);
        path.AddArc(bounds.X, bounds.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }

    private static string? ShortUiText(string? value, int maxLength)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        var trimmed = value.Trim();
        if (trimmed.Length <= maxLength) return trimmed;
        return trimmed[..Math.Max(1, maxLength - 1)] + "...";
    }

    // =========================
    // START/STOP
    // =========================
    private void StartServerAndDiscovery()
    {
        if (_isRunning) return;

        var port = (int)numPort.Value;

        try
        {
            _server.Start(port);
        }
        catch (SocketException)
        {
            SetRunningUi(false);
            lblHeaderSubtitle.Text = "Port unavailable. Close the other app and start again.";
            SetStatusUi("Error", Color.FromArgb(244, 124, 124), Color.FromArgb(244, 124, 124));
            SetLastError($"{DateTime.Now:HH:mm:ss} Port unavailable");
            WriteLog(LogLevel.Error, $"Port unavailable: {port}");
            MessageBox.Show(
                $"TCP port {port} is unavailable.",
                "Port Unavailable",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            return;
        }
        catch (Exception ex)
        {
            SetRunningUi(false);
            lblHeaderSubtitle.Text = "Start failed. Check Windows firewall or permissions.";
            SetStatusUi("Error", Color.FromArgb(244, 124, 124), Color.FromArgb(244, 124, 124));
            SetLastError($"{DateTime.Now:HH:mm:ss} Host start failed");
            WriteLog(LogLevel.Error, $"Host start failed: {ex.Message}");
            return;
        }

        LogOk("Host ready");

        try
        {
            _discovery?.Start(UDP_DISCOVERY_PORT, port);
            LogInfo("Discovery enabled");
        }
        catch (Exception ex)
        {
            LogWarn("Firewall/network warning: discovery unavailable");
            SetLastError($"{DateTime.Now:HH:mm:ss} Firewall/network warning");
            WriteLog(LogLevel.Warn, $"Discovery unavailable: {ex.Message}");
        }

        try
        {
            _bluetoothServer.Start();
            LogInfo("Bluetooth enabled");
        }
        catch (Exception ex)
        {
            LogWarn("Bluetooth unavailable");
            WriteLog(LogLevel.Warn, $"Bluetooth unavailable: {ex.Message}");
        }

        lblIpValue.Text = GetLocalIpv4() ?? "unknown";
        SetRunningUi(true);
        RefreshHostStatus();
        RefreshPairingStatus();
        RefreshServicesStatus();
    }

    private void btnStart_Click(object sender, EventArgs e) => StartServerAndDiscovery();

    private void btnStop_Click(object sender, EventArgs e)
    {
        _server.Stop();
        _bluetoothServer.Stop();
        _sleepTimer.Cancel();
        _discovery?.Stop();

        LogWarn("Host stopped");
        SetRunningUi(false);

        lblPinValue.Text = "------";
        _lastGeneratedPin = null;
        RefreshHostStatus();
        RefreshPairingStatus();
        RefreshServicesStatus();
    }

    private void btnPair_Click(object sender, EventArgs e)
    {
        GeneratePairingPin();
    }

    private void btnCopyPairingInfo_Click(object? sender, EventArgs e)
    {
        CopyPairingInfoToClipboard();
    }

    private void btnRestartHost_Click(object sender, EventArgs e)
    {
        if (_isRunning)
            btnStop_Click(this, EventArgs.Empty);

        StartServerAndDiscovery();
        SetLastConnection($"{DateTime.Now:HH:mm:ss} Host restarted");
    }

    private void btnMinimizeTray_Click(object sender, EventArgs e)
    {
        Hide();
        ShowInTaskbar = false;
    }

    private void btnAppShortcuts_Click(object sender, EventArgs e)
    {
        AppShortcutService.ShowConfigureDialog(this);
        SetLastConnection($"{DateTime.Now:HH:mm:ss} App shortcuts updated");
        LogInfo("App shortcuts updated");
    }

    private void chkStartWithWindows_CheckedChanged(object? sender, EventArgs e)
    {
        if (_syncingStartupCheck) return;

        var requested = chkStartWithWindows.Checked;
        if (!StartupService.SetEnabled(requested, out var msg))
        {
            _syncingStartupCheck = true;
            chkStartWithWindows.Checked = StartupService.IsEnabled();
            _syncingStartupCheck = false;

            SetLastError($"{DateTime.Now:HH:mm:ss} Autostart failed");
            LogErr($"Autostart failed: {msg}");
            return;
        }

        SetLastConnection($"{DateTime.Now:HH:mm:ss} Autostart {(requested ? "enabled" : "disabled")}");
    }

    private void SyncStartupUi()
    {
        _syncingStartupCheck = true;
        chkStartWithWindows.Checked = StartupService.IsEnabled();
        _syncingStartupCheck = false;
    }

    private void btnForgetPairing_Click(object sender, EventArgs e)
    {
        var result = MessageBox.Show(
            "This removes saved phone pairing for this host.",
            "Forget pairing?",
            MessageBoxButtons.YesNo,
            MessageBoxIcon.Warning,
            MessageBoxDefaultButton.Button2);

        if (result != DialogResult.Yes)
            return;

        var removed = _pairing.RevokeAllTokens();
        _lastGeneratedPin = null;
        _lastAuthOk = null;
        lblPinValue.Text = "------";

        RefreshPairingStatus();
        SetClientStatus("Pair failed", Color.FromArgb(244, 124, 124));
        SetLastConnection($"{DateTime.Now:HH:mm:ss} Pairing forgotten");
        LogWarn(removed > 0 ? "Pairing forgotten" : "Pairing cleared");
    }

    internal bool IsHostRunning => _isRunning;

    internal void ShowHostWindow()
    {
        ShowInTaskbar = true;
        if (!Visible) Show();
        if (WindowState == FormWindowState.Minimized)
            WindowState = FormWindowState.Normal;
        Activate();
    }

    internal void StartHost()
    {
        StartServerAndDiscovery();
    }

    internal void StopHost()
    {
        if (_isRunning)
            btnStop_Click(this, EventArgs.Empty);
    }

    internal void GeneratePairingPin()
    {
        if (!_isRunning)
            StartServerAndDiscovery();

        var pin = _pairing.GeneratePin(TimeSpan.FromSeconds(120));
        _lastGeneratedPin = pin;

        lblPinValue.Text = pin;
        RefreshPairingStatus();
        LogInfo("Pairing PIN generated. It is visible in the pairing dialog.");
        SetLastConnection($"{DateTime.Now:HH:mm:ss} PIN generated");
    }

    internal void CopyIpToClipboard()
    {
        try
        {
            Clipboard.SetText(lblIpValue.Text ?? "");
            SetLastConnection($"{DateTime.Now:HH:mm:ss} IP copied");
        }
        catch { /* ignore */ }
    }

    internal void CopyPairingInfoToClipboard(bool silent = false)
    {
        try
        {
            var pin = _pairing.HasValidPin && !string.IsNullOrWhiteSpace(_lastGeneratedPin)
                ? _lastGeneratedPin
                : "generate PIN first";

            Clipboard.SetText($"Deskly Host\r\nIP: {lblIpValue.Text}\r\nPort: {numPort.Value}\r\nPIN: {pin}");
            if (!silent)
                SetLastConnection($"{DateTime.Now:HH:mm:ss} Setup copied");
        }
        catch { /* ignore */ }
    }

    // =========================
    // LOGS
    // =========================
    private void InitLogToggle()
    {
        try
        {
            SetLogsVisible(false);
        }
        catch { /* ignore */ }
    }

    private void btnToggleLogs_Click(object? sender, EventArgs e)
    {
        SetLogsVisible(!_logsVisible);
    }

    private void SetLogsVisible(bool visible)
    {
        _logsVisible = false;

        if (cardLog != null)
            cardLog.Visible = false;

        if (btnToggleLogs != null)
            btnToggleLogs.Visible = false;

        ClientSize = _compactSize;
    }

    // =========================
    // External brightness capability test (simple)
    // =========================
    private void btnTestExternalBrightness_Click(object sender, EventArgs e)
    {
        RunExternalBrightnessCapabilityTest(logToUi: true);
    }

    private ExternalBrightnessCapability.CapabilityResult RunExternalBrightnessCapabilityTest(bool logToUi)
    {
        if (logToUi) LogInfo("Overujem podporu ovládania jasu externého monitora...");

        var res = ExternalBrightnessCapability.Test();

        if (logToUi)
        {
            if (res.Supported) LogOk("Externý monitor podporuje ovládanie jasu cez aplikáciu");
            else LogWarn("Externý monitor nepodporuje ovládanie jasu cez aplikáciu");
        }

        return res;
    }

    // =========================
    // Quiet button (PC-side)
    // =========================
    private void btnQuiet_Click(object sender, EventArgs e)
    {
        var requested = !_quietUiEnabled;

        var ok = QuietModeService.TrySet(requested, out var msg);

        if (ok)
        {
            _quietUiEnabled = requested;
            LogOk($"Tichý režim: {(requested ? "ZAP" : "VYP")}");
            return;
        }

        LogErr($"Tichý režim zlyhal: {msg}");

        if (requested &&
            (msg.Contains("správca", StringComparison.OrdinalIgnoreCase) ||
             msg.Contains("Administrator", StringComparison.OrdinalIgnoreCase)))
        {
            var res = MessageBox.Show(
                "Tichý režim vyžaduje spustenie ako správca.\nChceš reštartnúť DesklyPC ako Administrator?",
                "DesklyPC",
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Warning
            );

            if (res == DialogResult.Yes)
                Program.RestartAsAdmin();
        }
    }

    private void SetRunningUi(bool running)
    {
        _isRunning = running;

        btnStart.Enabled = !running;
        btnStop.Enabled = running;
        btnPair.Enabled = running;
        numPort.Enabled = !running;

        btnStart.Visible = ADVANCED_UI;
        btnStop.Visible = ADVANCED_UI;
        numPort.Visible = false;
        btnRestartHost.Enabled = running;
        btnMinimizeTray.Enabled = true;

        if (!running)
            SetClientStatus("No client", Color.FromArgb(174, 183, 194));

        RefreshHostStatus();
        RefreshPairingStatus();
        UpdateHostStatusPresentation();
    }

    // =========================
    // LOG (raw -> human)
    // =========================
    private void AppendLog(string rawLine)
    {
        UpdateDiagnosticsFromRawLog(rawLine);

        var (lvl, msg, shouldLog) = NormalizeLog(rawLine);
        if (!shouldLog) return;

        WriteLog(lvl, msg);
    }

    private void UpdateDiagnosticsFromRawLog(string rawLine)
    {
        if (string.IsNullOrWhiteSpace(rawLine)) return;

        var line = rawLine.Trim();
        var now = DateTime.Now.ToString("HH:mm:ss");

        if (line.Contains("[CONN] Connected", StringComparison.OrdinalIgnoreCase))
        {
            var endpoint = line.Split(':', 2).LastOrDefault()?.Trim();
            SetClientStatus("Connected", Color.FromArgb(142, 216, 196));
            PulseClientCard();
            SetLastConnection(string.IsNullOrWhiteSpace(endpoint)
                ? $"{now} Connected"
                : $"{now} Connected from {endpoint}");
        }
        else if (line.Contains("[CONN] Disconnected", StringComparison.OrdinalIgnoreCase))
        {
            var endpoint = line.Split(':', 2).LastOrDefault()?.Trim();
            SetClientStatus("No client", Color.FromArgb(174, 183, 194));
            SetLastConnection(string.IsNullOrWhiteSpace(endpoint)
                ? $"{now} Disconnected"
                : $"{now} Disconnected from {endpoint}");
        }
        else if (line.Contains("[ERROR]", StringComparison.OrdinalIgnoreCase) ||
                 line.Contains("[DISCOVERY] Error", StringComparison.OrdinalIgnoreCase))
        {
            SetLastError($"{now} {line}");
            lblHeaderSubtitle.Text = "Last command failed.";
            SetStatusUi("Error", Color.FromArgb(244, 124, 124), Color.FromArgb(244, 124, 124));
        }
    }

    private void LogInfo(string msg) => WriteLog(LogLevel.Info, msg);
    private void LogOk(string msg) => WriteLog(LogLevel.Ok, msg);
    private void LogWarn(string msg) => WriteLog(LogLevel.Warn, msg);
    private void LogErr(string msg) => WriteLog(LogLevel.Error, msg);

    private void WriteLog(LogLevel lvl, string msg)
    {
        if (InvokeRequired)
        {
            BeginInvoke(new Action(() => WriteLog(lvl, msg)));
            return;
        }

        if (lvl != LogLevel.Error && !DEBUG_LOG)
            return;

        var color = lvl switch
        {
            LogLevel.Ok => Color.FromArgb(192, 236, 207),
            LogLevel.Warn => Color.FromArgb(246, 213, 152),
            LogLevel.Error => Color.FromArgb(244, 124, 124),
            _ => Color.FromArgb(214, 223, 233),
        };

        if (lvl == LogLevel.Error)
        {
            SetLastError($"{DateTime.Now:HH:mm:ss} {msg}");
            lblHeaderSubtitle.Text = "Last command failed.";
            SetStatusUi("Error", Color.FromArgb(244, 124, 124), Color.FromArgb(244, 124, 124));
        }

        if (!DEBUG_LOG)
            return;

        txtLog.SelectionStart = txtLog.TextLength;
        txtLog.SelectionLength = 0;
        txtLog.SelectionColor = color;
        txtLog.AppendText($"{DateTime.Now:HH:mm:ss} {msg}{Environment.NewLine}");
        txtLog.SelectionColor = txtLog.ForeColor;
        txtLog.ScrollToCaret();
    }

    private static (LogLevel lvl, string msg, bool shouldLog) NormalizeLog(string raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
            return (LogLevel.Info, "", false);

        var line = raw.Trim();

        // wire spam
        if (line.StartsWith("[IN ") || line.StartsWith("[OUT]") || line.StartsWith("[IN]"))
            return (LogLevel.Info, "", DEBUG_LOG);

        if (line.Contains("[CONN] Connected", StringComparison.OrdinalIgnoreCase))
            return (LogLevel.Ok, "Phone connected", true);

        if (line.Contains("[CONN] Disconnected", StringComparison.OrdinalIgnoreCase))
            return (LogLevel.Warn, "Phone disconnected", true);

        if (line.Contains("Unauthorized", StringComparison.OrdinalIgnoreCase))
            return (LogLevel.Warn, "Pair failed", true);

        if (line.Contains("Bad JSON", StringComparison.OrdinalIgnoreCase))
            return (LogLevel.Error, "Command failed", true);

        if (line.Contains("[ERROR]", StringComparison.OrdinalIgnoreCase))
            return (LogLevel.Error, line.Replace("[ERROR]", "Error:", StringComparison.OrdinalIgnoreCase).Trim(), true);

        return (LogLevel.Info, line, DEBUG_LOG);
    }

    private static string? GetLocalIpv4()
    {
        try
        {
            var host = Dns.GetHostEntry(Dns.GetHostName());
            var addr = host.AddressList.FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork);
            return addr?.ToString();
        }
        catch { return null; }
    }

    // =========================
    // RID helpers
    // =========================
    private static string? GetRid(JsonElement root)
    {
        if (root.ValueKind != JsonValueKind.Object) return null;
        if (root.TryGetProperty("rid", out var ridEl) && ridEl.ValueKind == JsonValueKind.String)
            return ridEl.GetString();
        return null;
    }

    private static string WithRid(string requestJson, object responseObj)
    {
        using var doc = JsonDocument.Parse(requestJson);
        var rid = GetRid(doc.RootElement);

        if (string.IsNullOrEmpty(rid))
            return JsonSerializer.Serialize(responseObj);

        var json = JsonSerializer.SerializeToElement(responseObj);

        using var ms = new MemoryStream();
        using var writer = new Utf8JsonWriter(ms);

        writer.WriteStartObject();
        writer.WriteString("rid", rid);

        foreach (var prop in json.EnumerateObject())
            prop.WriteTo(writer);

        writer.WriteEndObject();
        writer.Flush();

        return Encoding.UTF8.GetString(ms.ToArray());
    }

    // =========================
    // Payload helpers
    // =========================
    private static string? GetTokenFromPayload(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return null;
        if (payload.TryGetProperty("token", out var tokenEl))
            return tokenEl.GetString();
        return null;
    }

    private static string? GetDeviceNameFromPayload(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return null;
        if (!payload.TryGetProperty("deviceName", out var nameEl) || nameEl.ValueKind != JsonValueKind.String)
            return null;

        var name = (nameEl.GetString() ?? "").Trim();
        return string.IsNullOrWhiteSpace(name) ? null : name;
    }

    private static string GetDisplayIdFromPayload(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object) return "all";

        if (payload.TryGetProperty("displayId", out var idEl) && idEl.ValueKind == JsonValueKind.String)
        {
            var s = (idEl.GetString() ?? "").Trim();
            if (!string.IsNullOrEmpty(s)) return s;
        }

        if (!payload.TryGetProperty("target", out var tEl) || tEl.ValueKind != JsonValueKind.String)
            return "all";

        var t = (tEl.GetString() ?? "all").Trim().ToLowerInvariant();
        return t switch
        {
            "internal" or "laptop" => "internal",
            "2" or "display2" or "d2" => "ddc_2",
            "1" or "display1" or "d1" => "ddc_1",
            "all" or "both" or "1+2" or "12" => "all",
            _ => "all",
        };
    }

    private static bool TryGetBool(JsonElement payload, string prop, bool fallback)
    {
        if (payload.ValueKind != JsonValueKind.Object) return fallback;
        if (!payload.TryGetProperty(prop, out var el)) return fallback;

        return el.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            JsonValueKind.String => bool.TryParse(el.GetString(), out var b) ? b : fallback,
            JsonValueKind.Number => el.TryGetInt32(out var n) ? (n != 0) : fallback,
            _ => fallback
        };
    }

    private static bool TryGetBoolNullable(JsonElement payload, string prop, out bool value)
    {
        value = false;
        if (payload.ValueKind != JsonValueKind.Object) return false;
        if (!payload.TryGetProperty(prop, out var el)) return false;

        switch (el.ValueKind)
        {
            case JsonValueKind.True: value = true; return true;
            case JsonValueKind.False: value = false; return true;
            case JsonValueKind.String: return bool.TryParse(el.GetString(), out value);
            case JsonValueKind.Number:
                if (el.TryGetInt32(out var n)) { value = n != 0; return true; }
                return false;
            default:
                return false;
        }
    }

    private static int TryGetInt(JsonElement payload, string prop, int fallback)
    {
        if (payload.ValueKind != JsonValueKind.Object) return fallback;
        if (!payload.TryGetProperty(prop, out var el)) return fallback;

        if (el.ValueKind == JsonValueKind.Number && el.TryGetInt32(out var v)) return v;
        if (el.ValueKind == JsonValueKind.String && int.TryParse(el.GetString(), out var vs)) return vs;
        return fallback;
    }

    private static object EmptyPowerPlanData()
        => new { plan = "", name = "", supported = false, plans = Array.Empty<PowerPlanDto>() };

    private static object EmptyTimerData()
        => new { running = false, remainingSeconds = 0, action = (string?)null, fadeOutVolume = false };

    private static object EmptyNightData()
        => new { enabled = false, intensity = 0, kelvin = (int?)null, screenDim = 0 };

    // =========================
    // Brightness routing
    // =========================
    private static bool TryGetBrightnessByDisplayId(string displayId, out int value0to100, out string mechanism, out string message)
    {
        value0to100 = -1;
        mechanism = "UNKNOWN";
        message = "";

        if (string.Equals(displayId, "internal", StringComparison.OrdinalIgnoreCase))
        {
            mechanism = "WMI";
            return DisplayService.TryGetInternalBrightness(out value0to100, out message);
        }

        if (DisplayService.TryResolveDdcIndex(displayId, out var idx1))
        {
            mechanism = "DDC/CI";
            return MonitorBrightness.TryGet(idx1, out value0to100, out message);
        }

        if (displayId.StartsWith("ddc_", StringComparison.OrdinalIgnoreCase)
            && int.TryParse(displayId.Substring(4), out var idxFallback))
        {
            mechanism = "DDC/CI";
            return MonitorBrightness.TryGet(idxFallback, out value0to100, out message);
        }

        message = "Unknown display";
        return false;
    }

    private static bool TrySetBrightnessByDisplayId(string displayId, int value0to100, out string mechanism, out string message)
    {
        mechanism = "UNKNOWN";
        message = "";
        value0to100 = Math.Clamp(value0to100, 0, 100);

        if (string.Equals(displayId, "internal", StringComparison.OrdinalIgnoreCase))
        {
            mechanism = "WMI";
            return DisplayService.TrySetInternalBrightness(value0to100, out message);
        }

        if (DisplayService.TryResolveDdcIndex(displayId, out var idx1))
        {
            mechanism = "DDC/CI";
            return MonitorBrightness.TrySet(idx1, value0to100, out message);
        }

        if (displayId.StartsWith("ddc_", StringComparison.OrdinalIgnoreCase)
            && int.TryParse(displayId.Substring(4), out var idxFallback))
        {
            mechanism = "DDC/CI";
            return MonitorBrightness.TrySet(idxFallback, value0to100, out message);
        }

        message = "Unknown display";
        return false;
    }

    private static string HumanDisplay(string displayId)
    {
        if (string.Equals(displayId, "internal", StringComparison.OrdinalIgnoreCase))
            return "Interný displej";
        if (string.Equals(displayId, "all", StringComparison.OrdinalIgnoreCase))
            return "Všetky displeje";
        return "Externý monitor";
    }

    private void RecordPerfRequest(string type, int bytes)
    {
        if (!PERF_DIAGNOSTICS) return;

        _perfRequestCount++;
        if (type is "mouse_move" or "mouse_scroll" or "keyboard_text")
            _perfHighFrequencyCount++;

        var now = DateTime.UtcNow;
        if ((now - _lastPerfLogAt).TotalSeconds < 30) return;

        try
        {
            using var p = Process.GetCurrentProcess();
            var cpu = p.TotalProcessorTime;
            var cpuDeltaMs = (cpu - _lastPerfCpu).TotalMilliseconds;
            _lastPerfCpu = cpu;
            _lastPerfLogAt = now;

            LogInfo(
                $"Perf: requests={_perfRequestCount}, highFreq={_perfHighFrequencyCount}, " +
                $"lastBytes={bytes}, cpuDeltaMs={cpuDeltaMs:0}, workingSetMb={p.WorkingSet64 / 1024 / 1024}");
        }
        catch
        {
            _lastPerfLogAt = now;
        }

        _perfRequestCount = 0;
        _perfHighFrequencyCount = 0;
    }

    // =========================
    // JSON ROUTER (FULL)
    // =========================
    private string HandleIncomingRequest(string jsonLine)
    {
        try
        {
            using var doc = JsonDocument.Parse(jsonLine);
            var root = doc.RootElement;

            if (root.ValueKind != JsonValueKind.Object)
                return WithRid(jsonLine, new { type = "response", ok = false, message = "Invalid request" });

            if (!root.TryGetProperty("type", out var typeEl))
                return WithRid(jsonLine, new { type = "response", ok = false, message = "Missing type" });

            var type = typeEl.GetString() ?? "";
            RecordPerfRequest(type, jsonLine.Length);
            if (!DesklyProtocol.IsSupportedVersion(root, out var protocolVersion))
            {
                LogWarn($"Unsupported protocol version: {protocolVersion?.ToString() ?? "invalid"}");
                return WithRid(jsonLine, new
                {
                    type = "response",
                    ok = false,
                    message = "Unsupported protocol version",
                    data = new { supportedProtocolVersion = DesklyProtocol.Version }
                });
            }

            if (!DesklyProtocol.HasObjectPayloadIfPresent(root))
                return WithRid(jsonLine, new { type = "response", ok = false, message = "Invalid payload" });

            if (type == "pair_status")
            {
                return WithRid(jsonLine, new
                {
                    type = "pair_status_response",
                    ok = true,
                    data = new
                    {
                        hasValidPin = _pairing.HasValidPin,
                        pinSecondsRemaining = _pairing.PinSecondsRemaining(),
                        validTokensCount = _pairing.ValidTokensCount
                    }
                });
            }

            if (type == "pair_request")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "pair_response", ok = false, message = "Missing payload" });

                var pin = payload.TryGetProperty("pin", out var pinEl) ? (pinEl.GetString() ?? "") : "";
                var deviceName = GetDeviceNameFromPayload(payload);
                var (ok, token, message) = _pairing.TryPair(pin, deviceName);

                if (ok)
                {
                    LogOk("Spárovanie úspešné");
                    _lastGeneratedPin = null;
                    SetClientStatus("Authorized", Color.FromArgb(192, 236, 207));
                    PulseClientCard();
                    SetLastConnection(string.IsNullOrWhiteSpace(deviceName)
                        ? $"{DateTime.Now:HH:mm:ss} Paired"
                        : $"{DateTime.Now:HH:mm:ss} Paired {ShortUiText(deviceName, 28)}");
                    RefreshPairingStatus();
                    return WithRid(jsonLine, new { type = "pair_response", ok = true, message, data = new { token } });
                }

                LogWarn("Spárovanie zlyhalo");
                SetClientStatus("Unauthorized", Color.FromArgb(242, 183, 183));
                RefreshPairingStatus();
                return WithRid(jsonLine, new { type = "pair_response", ok = false, message });
            }

            if (type == "auth_request")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "auth_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                var deviceName = GetDeviceNameFromPayload(payload);
                var ok = _pairing.IsTokenValid(token);

                if (_lastAuthOk != ok)
                {
                    _lastAuthOk = ok;
                    if (ok)
                    {
                        _pairing.MarkSeen(token, deviceName);
                        SetClientStatus("Authorized", Color.FromArgb(192, 236, 207));
                        PulseClientCard();
                        var displayName = _pairing.GetDeviceName(token);
                        SetLastConnection(string.IsNullOrWhiteSpace(displayName)
                            ? $"{DateTime.Now:HH:mm:ss} Authorized client"
                            : $"{DateTime.Now:HH:mm:ss} Authorized {ShortUiText(displayName, 28)}");
                        LogOk("Autorizácia OK");
                    }
                    else
                    {
                        SetClientStatus("Unauthorized", Color.FromArgb(242, 183, 183));
                        LogWarn("Autorizácia zlyhala");
                    }
                }
                else if (ok)
                {
                    _pairing.MarkSeen(token, deviceName);
                }

                RefreshPairingStatus();
                return WithRid(jsonLine, new { type = "auth_response", ok, message = ok ? "Authorized" : "Unauthorized" });
            }

            if (type == "display_list")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "display_list_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "display_list_response", ok = false, message = "Unauthorized" });

                var displays = DisplayService.GetDisplays();
                return WithRid(jsonLine, new { type = "display_list_response", ok = true, message = "OK", data = new { displays } });
            }

            if (type == "display_control")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "display_control_response", ok = false, message = "Missing payload", data = new { supported = false } });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "display_control_response", ok = false, message = "Unauthorized", data = new { supported = false } });

                var action = payload.TryGetProperty("action", out var actionEl) ? (actionEl.GetString() ?? "") : "";
                action = action.Trim().ToLowerInvariant();

                if (action == "turn_off")
                {
                    var ok = DisplayControlService.TurnOffDisplay(out var msg);
                    if (ok) LogInfo("Obrazovka vypnutá");
                    else LogErr($"Obrazovka: {msg}");

                    return WithRid(jsonLine, new
                    {
                        type = "display_control_response",
                        ok,
                        message = ok ? "OK" : msg,
                        data = new { action, supported = true }
                    });
                }

                if (action == "turn_on")
                {
                    var ok = DisplayControlService.TurnOnDisplay(out var msg);
                    if (ok) LogInfo("Obrazovka zapnutá");
                    else LogErr($"Obrazovka: {msg}");

                    return WithRid(jsonLine, new
                    {
                        type = "display_control_response",
                        ok,
                        message = ok ? "OK" : msg,
                        data = new { action, supported = true }
                    });
                }

                return WithRid(jsonLine, new
                {
                    type = "display_control_response",
                    ok = false,
                    message = "Unsupported display action",
                    data = new { action, supported = false }
                });
            }

            if (type == "display_mode_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "display_mode_response", ok = false, message = "Missing payload", data = new { supported = false } });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "display_mode_response", ok = false, message = "Unauthorized", data = new { supported = false } });

                var mode = payload.TryGetProperty("mode", out var modeEl) ? (modeEl.GetString() ?? "") : "";
                var ok = DisplayControlService.TrySetProjectionMode(mode, out var normalized, out var msg);
                if (ok) LogInfo($"Display mode: {normalized}");
                else LogErr($"Display mode failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "display_mode_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new { mode = normalized, supported = ok }
                });
            }

            if (type == "capabilities_external_brightness")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "capabilities_external_brightness_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "capabilities_external_brightness_response", ok = false, message = "Unauthorized" });

                var res = RunExternalBrightnessCapabilityTest(logToUi: false);

                var monitors = res.Monitors.Select(m => new
                {
                    ddcIndex = m.DdcIndex,
                    name = string.IsNullOrWhiteSpace(m.Name) ? "Unknown monitor" : m.Name,
                    ddcci = m.DdcciAvailable,
                    canGet = m.CanGet,
                    canSet = m.CanSet,
                    note = m.Note ?? ""
                }).ToArray();

                return WithRid(jsonLine, new
                {
                    type = "capabilities_external_brightness_response",
                    ok = true,
                    message = "OK",
                    data = new { supported = res.Supported, monitors }
                });
            }

            if (type == "ping_secure")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "response", ok = false, message = "Unauthorized" });

                return WithRid(jsonLine, new { type = "response", ok = true, message = "pong" });
            }

            if (type is "mouse_move" or "mouse_click" or "mouse_scroll" or "mouse_button")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "mouse_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "mouse_response", ok = false, message = "Unauthorized" });

                bool ok;
                string msg;
                object data;

                if (type == "mouse_move")
                {
                    var dx = TryGetInt(payload, "dx", fallback: 0);
                    var dy = TryGetInt(payload, "dy", fallback: 0);
                    ok = InputService.TryMoveRelative(dx, dy, out msg);
                    data = new { command = type, dx = Math.Clamp(dx, -5000, 5000), dy = Math.Clamp(dy, -5000, 5000) };
                }
                else if (type == "mouse_click")
                {
                    var button = payload.TryGetProperty("button", out var buttonEl) ? (buttonEl.GetString() ?? "left") : "left";
                    var clicks = Math.Clamp(TryGetInt(payload, "clicks", fallback: 1), 1, 3);
                    ok = InputService.TryClick(button, clicks, out msg);
                    if (ok) LogInfo($"Mouse: {button} click x{clicks}");
                    else LogErr($"Mouse click failed: {msg}");
                    data = new { command = type, button, clicks };
                }
                else if (type == "mouse_button")
                {
                    var button = payload.TryGetProperty("button", out var buttonEl) ? (buttonEl.GetString() ?? "left") : "left";
                    var down = TryGetBool(payload, "down", fallback: false);
                    ok = InputService.TrySetButton(button, down, out msg);
                    if (ok) LogInfo($"Mouse: {button} {(down ? "down" : "up")}");
                    else LogErr($"Mouse button failed: {msg}");
                    data = new { command = type, button, down };
                }
                else
                {
                    var deltaX = TryGetInt(payload, "deltaX", fallback: 0);
                    var deltaY = TryGetInt(payload, "deltaY", fallback: 0);
                    ok = InputService.TryScroll(deltaX, deltaY, out msg);
                    data = new { command = type, deltaX = Math.Clamp(deltaX, -10, 10), deltaY = Math.Clamp(deltaY, -10, 10) };
                }

                return WithRid(jsonLine, new
                {
                    type = "mouse_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data
                });
            }

            if (type is "keyboard_text" or "keyboard_key" or "keyboard_shortcut")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "keyboard_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "keyboard_response", ok = false, message = "Unauthorized" });

                bool ok;
                string msg;
                object data;

                if (type == "keyboard_text")
                {
                    var text = payload.TryGetProperty("text", out var textEl) && textEl.ValueKind == JsonValueKind.String
                        ? textEl.GetString()
                        : "";
                    ok = InputService.TrySendText(text, out msg);
                    if (ok) LogInfo($"Keyboard: text ({(text ?? "").Length} chars)");
                    else LogErr($"Keyboard text failed: {msg}");
                    data = new { command = type, length = (text ?? "").Length };
                }
                else if (type == "keyboard_key")
                {
                    var key = payload.TryGetProperty("key", out var keyEl) ? (keyEl.GetString() ?? "") : "";
                    var presses = Math.Clamp(TryGetInt(payload, "presses", fallback: 1), 1, 10);
                    ok = InputService.TrySendKey(key, presses, out msg);
                    if (ok) LogInfo($"Keyboard: {key} x{presses}");
                    else LogErr($"Keyboard key failed: {msg}");
                    data = new { command = type, key, presses };
                }
                else
                {
                    var keys = new List<string>();
                    if (payload.TryGetProperty("keys", out var keysEl) && keysEl.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var item in keysEl.EnumerateArray())
                        {
                            if (item.ValueKind == JsonValueKind.String)
                            {
                                var k = item.GetString();
                                if (!string.IsNullOrWhiteSpace(k)) keys.Add(k);
                            }
                        }
                    }

                    ok = InputService.TrySendShortcut(keys, out msg);
                    if (ok) LogInfo($"Keyboard: shortcut {string.Join("+", keys.Take(4))}");
                    else LogErr($"Keyboard shortcut failed: {msg}");
                    data = new { command = type, keys = keys.Take(4).ToArray() };
                }

                return WithRid(jsonLine, new
                {
                    type = "keyboard_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data
                });
            }

            if (type == "shortcut_action")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "shortcut_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "shortcut_response", ok = false, message = "Unauthorized" });

                var rawAction = payload.TryGetProperty("action", out var actionEl) ? (actionEl.GetString() ?? "") : "";
                var action = ShortcutActionMapper.Normalize(rawAction);
                var mapping = ShortcutActionMapper.Resolve(action);

                var msg = "";
                var ok = mapping is not null && (mapping.Kind switch
                {
                    ShortcutActionKind.CtrlWheelZoom => InputService.TryCtrlWheelZoom(mapping.WheelSteps, out msg),
                    _ => InputService.TrySendShortcut(mapping.Keys, out msg)
                });
                if (mapping is null) msg = "Unsupported shortcut action";
                if (ok) LogInfo($"Shortcut: {action}");
                else LogErr($"Shortcut failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "shortcut_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new { command = type, action }
                });
            }

            if (type == "clipboard_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "clipboard_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "clipboard_response", ok = false, message = "Unauthorized" });

                var text = payload.TryGetProperty("text", out var textEl) && textEl.ValueKind == JsonValueKind.String
                    ? textEl.GetString()
                    : "";
                if ((text ?? "").Length > 10_000) text = text![..10_000];

                var ok = TrySetClipboardText(text, out var msg);
                if (ok) LogInfo($"Clipboard: text ({(text ?? "").Length} chars)");
                else LogErr($"Clipboard failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "clipboard_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new { command = type, length = (text ?? "").Length }
                });
            }

            if (type is "media_key" or "media_action")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "media_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "media_response", ok = false, message = "Unauthorized" });

                bool ok;
                string msg;
                object data;
                if (type == "media_action")
                {
                    var rawAction = payload.TryGetProperty("action", out var actionEl) ? (actionEl.GetString() ?? "") : "";
                    var action = MediaActionMapper.Normalize(rawAction);
                    var targetId = payload.TryGetProperty("targetId", out var targetEl) && targetEl.ValueKind == JsonValueKind.String
                        ? targetEl.GetString()
                        : "";
                    if (!string.IsNullOrWhiteSpace(targetId) &&
                        !WindowSwitcherService.TrySwitchTo(targetId, out var switchMsg, out _))
                    {
                        return WithRid(jsonLine, new
                        {
                            type = "media_response",
                            ok = false,
                            message = switchMsg,
                            data = new { command = type, action, targetId }
                        });
                    }

                    var mapping = MediaActionMapper.Resolve(action);
                    msg = "";
                    ok = mapping is not null && (mapping.Kind switch
                    {
                        MediaActionKind.Shortcut => InputService.TrySendShortcut(mapping.Keys, out msg),
                        _ => InputService.TrySendKey(mapping.Keys.FirstOrDefault(), 1, out msg)
                    });
                    if (mapping is null) msg = "Unsupported media action";
                    if (ok) LogInfo($"Media: {action}");
                    else LogErr($"Media action failed: {msg}");
                    data = new { command = type, action, targetId };
                }
                else
                {
                    var key = payload.TryGetProperty("key", out var keyEl) ? (keyEl.GetString() ?? "") : "";
                    ok = InputService.TrySendKey(key, 1, out msg);
                    if (ok) LogInfo($"Media: {key}");
                    else LogErr($"Media key failed: {msg}");
                    data = new { command = type, key };
                }

                return WithRid(jsonLine, new
                {
                    type = "media_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data
                });
            }

            if (type == "video_list")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "video_list_response", ok = false, message = "Missing payload", data = new { supported = false, videos = Array.Empty<object>(), fallback = "media_remote" } });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "video_list_response", ok = false, message = "Unauthorized", data = new { supported = false, videos = Array.Empty<object>(), fallback = "media_remote" } });

                var videos = VideoDetectionService.Detect()
                    .Select(x => new
                    {
                        id = x.Id,
                        title = x.Title,
                        source = x.Source,
                        playbackState = x.PlaybackState,
                        controllable = x.Controllable
                    })
                    .ToArray();

                return WithRid(jsonLine, new
                {
                    type = "video_list_response",
                    ok = true,
                    message = videos.Length == 0 ? "No active media windows detected. Use Media Remote." : "OK",
                    data = new
                    {
                        supported = true,
                        videos,
                        fallback = "media_remote"
                    }
                });
            }

            if (type == "presentation_action")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "presentation_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "presentation_response", ok = false, message = "Unauthorized" });

                var action = payload.TryGetProperty("action", out var actionEl) ? (actionEl.GetString() ?? "") : "";
                var key = action.Trim().ToLowerInvariant() switch
                {
                    "start" => "f5",
                    "next" => "right",
                    "previous" or "prev" => "left",
                    "black" => "b",
                    "exit" => "esc",
                    _ => ""
                };

                var msg = "";
                var ok = !string.IsNullOrWhiteSpace(key) && InputService.TrySendKey(key, 1, out msg);
                if (string.IsNullOrWhiteSpace(key)) msg = "Unsupported presentation action";
                if (ok) LogInfo($"Presentation: {action}");
                else LogErr($"Presentation action failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "presentation_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new { command = type, action }
                });
            }

            if (type == "app_shortcuts_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Unauthorized" });

                var slots = AppShortcutService.GetSlots()
                    .Select(x => new
                    {
                        slot = x.Slot,
                        label = x.Label,
                        configured = !string.IsNullOrWhiteSpace(x.Path)
                    })
                    .ToArray();

                return WithRid(jsonLine, new
                {
                    type = "app_response",
                    ok = true,
                    message = "OK",
                    data = new { command = type, slots }
                });
            }

            if (type == "app_catalog_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Unauthorized" });

                var apps = AppShortcutService.GetCatalog()
                    .Select(x => new { id = x.Id, label = x.Label })
                    .ToArray();

                return WithRid(jsonLine, new
                {
                    type = "app_response",
                    ok = true,
                    message = "OK",
                    data = new { command = type, apps }
                });
            }

            if (type == "app_shortcut_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Unauthorized" });

                var slot = Math.Clamp(TryGetInt(payload, "slot", fallback: 0), 0, 5);
                var appId = payload.TryGetProperty("appId", out var appIdEl) ? (appIdEl.GetString() ?? "") : "";
                var ok = AppShortcutService.TrySetSlotFromCatalog(slot, appId, out var msg, out var shortcut);
                if (ok) LogInfo($"App shortcut set: slot {slot} -> {shortcut?.Label ?? ""}");
                else LogErr($"App shortcut set failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "app_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new
                    {
                        command = type,
                        slot,
                        label = shortcut?.Label ?? "",
                        configured = shortcut != null && !string.IsNullOrWhiteSpace(shortcut.Path)
                    }
                });
            }

            if (type == "app_open")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Unauthorized" });

                var slot = Math.Clamp(TryGetInt(payload, "slot", fallback: 0), 0, 5);
                var ok = AppShortcutService.TryOpen(slot, out var msg, out var shortcut);
                if (ok) LogInfo($"App shortcut: {shortcut?.Label ?? $"Slot {slot}"}");
                else LogErr($"App shortcut failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "app_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new
                    {
                        command = type,
                        slot,
                        label = shortcut?.Label ?? ""
                    }
                });
            }

            if (type == "app_windows_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Unauthorized" });

                var windows = WindowSwitcherService.GetOpenWindows()
                    .Select(x => new { id = x.Id, title = x.Title, appName = x.AppName })
                    .ToArray();

                return WithRid(jsonLine, new
                {
                    type = "app_response",
                    ok = true,
                    message = "OK",
                    data = new
                    {
                        command = type,
                        windows
                    }
                });
            }

            if (type == "app_switch")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "app_response", ok = false, message = "Unauthorized" });

                var windowId = payload.TryGetProperty("windowId", out var windowIdEl) && windowIdEl.ValueKind == JsonValueKind.String
                    ? windowIdEl.GetString()
                    : "";
                var ok = WindowSwitcherService.TrySwitchTo(windowId, out var msg, out var window);
                if (ok) LogInfo("App switch: window activated");
                else LogErr($"App switch failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "app_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new
                    {
                        command = type,
                        windowId = window?.Id ?? "",
                        appName = window?.AppName ?? ""
                    }
                });
            }

            if (type == "web_open")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "web_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "web_response", ok = false, message = "Unauthorized" });

                var url = payload.TryGetProperty("url", out var urlEl) && urlEl.ValueKind == JsonValueKind.String
                    ? urlEl.GetString()
                    : "";
                var ok = TryOpenWebUrl(url, out var msg, out var host);
                if (ok) LogInfo($"Web: {host}");
                else LogErr($"Web failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "web_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new
                    {
                        command = type,
                        host
                    }
                });
            }

            if (type == "power_plan_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "power_plan_response", ok = false, message = "Missing payload", data = EmptyPowerPlanData() });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "power_plan_response", ok = false, message = "Unauthorized", data = EmptyPowerPlanData() });

                var cur = PowerPlanService.GetCurrent();
                return WithRid(jsonLine, new
                {
                    type = "power_plan_response",
                    ok = cur.supported,
                    message = cur.message,
                    data = new { plan = cur.id, name = cur.name, supported = cur.supported, plans = cur.plans }
                });
            }

            if (type == "power_plan_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "power_plan_response", ok = false, message = "Missing payload", data = EmptyPowerPlanData() });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "power_plan_response", ok = false, message = "Unauthorized", data = EmptyPowerPlanData() });

                var plan = payload.TryGetProperty("plan", out var planEl) ? (planEl.GetString() ?? "") : "";
                var ok = PowerPlanService.TrySet(plan, out var normalized, out var msg, out var plans);
                var cur = PowerPlanService.GetCurrent();

                if (ok) LogInfo($"Power plan: {normalized}");
                else LogErr($"Power plan failed: {msg}");

                return WithRid(jsonLine, new
                {
                    type = "power_plan_response",
                    ok,
                    message = ok ? "OK" : msg,
                    data = new { plan = ok ? normalized : cur.id, requested = normalized, name = ok ? PowerPlanService.GetPlanName(normalized) : cur.name, supported = ok || cur.supported, plans }
                });
            }

            if (type is "power_lock" or "power_sleep" or "power_shutdown" or "power_restart")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "power_response", ok = false, message = "Missing payload", data = new { action = type, fadeOutVolume = false } });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "power_response", ok = false, message = "Unauthorized", data = new { action = type, fadeOutVolume = false } });

                if (!_powerCommandGuard.IsFreshPowerRequest(payload, out var staleMessage))
                    return WithRid(jsonLine, new { type = "power_response", ok = false, message = staleMessage, data = new { action = type, fadeOutVolume = false } });

                if (PowerCommandGuard.IsDangerousPowerAction(type) &&
                    payload.TryGetProperty("confirmed", out _) &&
                    (!TryGetBoolNullable(payload, "confirmed", out var confirmed) || !confirmed))
                {
                    return WithRid(jsonLine, new { type = "power_response", ok = false, message = "Confirmation required", data = new { action = type, fadeOutVolume = false } });
                }

                if (PowerCommandGuard.IsDangerousPowerAction(type) && _powerCommandGuard.IsRepeatedPowerRequest(type, token, out var repeatMessage))
                    return WithRid(jsonLine, new { type = "power_response", ok = false, message = repeatMessage, data = new { action = type, fadeOutVolume = false } });

                bool ok;
                string msg;
                string human;
                var fadeOutVolume = type == "power_shutdown" && TryGetBool(payload, "fadeOutVolume", fallback: false);

                switch (type)
                {
                    case "power_lock":
                        ok = SystemActions.LockPc();
                        msg = ok ? "Locked" : "Lock failed";
                        human = ok ? "PC uzamknuté" : "Nepodarilo sa uzamknúť PC";
                        break;
                    case "power_sleep":
                        ok = SystemActions.SleepPc();
                        msg = ok ? "Sleeping" : "Sleep failed";
                        human = ok ? "PC uspávané" : "Nepodarilo sa uspať PC";
                        break;
                    case "power_shutdown":
                        ok = SystemActions.ShutdownPcAfterOptionalFadeAsync(fadeOutVolume, out var shutdownMsg);
                        msg = ok ? shutdownMsg : "Shutdown failed";
                        human = ok ? "PC sa vypína" : "Nepodarilo sa vypnúť PC";
                        break;
                    case "power_restart":
                        ok = SystemActions.RestartPc();
                        msg = ok ? "Restarting" : "Restart failed";
                        human = ok ? "PC sa reštartuje" : "Nepodarilo sa reštartovať PC";
                        break;
                    default:
                        ok = false; msg = "Unknown power"; human = "Neznáma akcia";
                        break;
                }

                if (ok) LogInfo(human);
                else LogErr(human);

                return WithRid(jsonLine, new { type = "power_response", ok, message = msg, data = new { action = type, fadeOutVolume } });
            }

            if (type == "sleep_timer_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "sleep_timer_response", ok = false, message = "Missing payload", data = EmptyTimerData() });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "sleep_timer_response", ok = false, message = "Unauthorized", data = EmptyTimerData() });

                if (!payload.TryGetProperty("seconds", out var secEl) || secEl.ValueKind != JsonValueKind.Number)
                    return WithRid(jsonLine, new { type = "sleep_timer_response", ok = false, message = "Missing seconds", data = EmptyTimerData() });

                var seconds = secEl.GetInt32();
                var action = payload.TryGetProperty("action", out var actEl) ? (actEl.GetString() ?? "sleep") : "sleep";
                action = action == "shutdown" ? "shutdown" : "sleep";
                var fadeOutVolume = action == "shutdown" && TryGetBool(payload, "fadeOutVolume", fallback: false);

                _sleepTimer.Set(seconds, action, fadeOutVolume);
                _sleepUiTimer.Start();
                LogOk($"Timer nastavený: {FormatSeconds(seconds)} ({HumanTimerAction(action)})");

                var st = _sleepTimer.GetStatus();
                RefreshServicesStatus();
                return WithRid(jsonLine, new
                {
                    type = "sleep_timer_response",
                    ok = true,
                    message = "Timer set",
                    data = new { running = st.running, remainingSeconds = st.remainingSeconds, action = st.action, fadeOutVolume }
                });
            }

            if (type == "sleep_timer_cancel")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "sleep_timer_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "sleep_timer_response", ok = false, message = "Unauthorized" });

                _sleepTimer.Cancel();
                _sleepUiTimer.Stop();
                LogInfo("Timer zrušený");
                RefreshServicesStatus();

                return WithRid(jsonLine, new { type = "sleep_timer_response", ok = true, message = "Timer cancelled", data = new { running = false, remainingSeconds = 0, action = (string?)null } });
            }

            if (type == "sleep_timer_status")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "sleep_timer_status_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "sleep_timer_status_response", ok = false, message = "Unauthorized" });

                var st = _sleepTimer.GetStatus();
                return WithRid(jsonLine, new { type = "sleep_timer_status_response", ok = true, message = "OK", data = new { running = st.running, remainingSeconds = st.remainingSeconds, action = st.action } });
            }

            if (type == "volume_get" || type == "volume_set" || type == "mute_toggle")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "audio_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "audio_response", ok = false, message = "Unauthorized" });

                if (type == "volume_get")
                {
                    var vol = SystemActions.GetVolume();
                    var muted = SystemActions.GetMute();
                    return WithRid(jsonLine, new { type = "audio_response", ok = true, message = "OK", data = new { volume = vol, muted } });
                }

                if (type == "volume_set")
                {
                    if (!payload.TryGetProperty("volume", out var volEl) || volEl.ValueKind != JsonValueKind.Number)
                        return WithRid(jsonLine, new { type = "audio_response", ok = false, message = "Missing volume" });

                    var volume = volEl.GetInt32();
                    SystemActions.SetVolume(volume);

                    var now = DateTime.UtcNow;
                    var key = $"vol:{Math.Clamp(volume, 0, 100)}";
                    if (now - _lastThrottledLogAt > TimeSpan.FromMilliseconds(700) || _lastThrottledLogKey != key)
                    {
                        _lastThrottledLogAt = now;
                        _lastThrottledLogKey = key;
                        LogInfo($"Hlasitosť: {Math.Clamp(volume, 0, 100)}%");
                    }

                    var current = SystemActions.GetVolume();
                    var muted = SystemActions.GetMute();
                    return WithRid(jsonLine, new { type = "audio_response", ok = true, message = "OK", data = new { volume = current, muted } });
                }

                // mute_toggle
                {
                    var muted = SystemActions.ToggleMute();
                    var vol = SystemActions.GetVolume();
                    LogInfo(muted ? "Zvuk: stlmený" : "Zvuk: zapnutý");
                    return WithRid(jsonLine, new { type = "audio_response", ok = true, message = "OK", data = new { volume = vol, muted } });
                }
            }

            if (type == "brightness_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "brightness_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "brightness_response", ok = false, message = "Unauthorized" });

                var displayId = GetDisplayIdFromPayload(payload);

                if (string.Equals(displayId, "all", StringComparison.OrdinalIgnoreCase))
                {
                    var list = DisplayService.GetEntries();
                    var values = list.Select(d =>
                    {
                        var ok = TryGetBrightnessByDisplayId(d.Id, out var v, out var mech, out var msg);
                        return new { id = d.Id, value = ok ? v : -1, mechanism = mech, ok, message = ok ? "OK" : msg };
                    }).ToArray();

                    var anyOk = values.Any(x => x.ok);
                    var firstErr = values.FirstOrDefault(x => !x.ok)?.message ?? "No data";

                    return WithRid(jsonLine, new { type = "brightness_response", ok = anyOk, message = anyOk ? "OK" : firstErr, data = new { displayId = "all", values } });
                }
                else
                {
                    var ok = TryGetBrightnessByDisplayId(displayId, out var value, out var mech, out var msg);
                    return WithRid(jsonLine, new { type = "brightness_response", ok, message = ok ? "OK" : msg, data = new { displayId, value, mechanism = mech } });
                }
            }

            if (type == "brightness_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "brightness_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "brightness_response", ok = false, message = "Unauthorized" });

                if (!payload.TryGetProperty("value", out var valEl) || valEl.ValueKind != JsonValueKind.Number)
                    return WithRid(jsonLine, new { type = "brightness_response", ok = false, message = "Missing value" });

                var value = Math.Clamp(valEl.GetInt32(), 0, 100);
                var displayId = GetDisplayIdFromPayload(payload);

                if (string.Equals(displayId, "all", StringComparison.OrdinalIgnoreCase))
                {
                    var list = DisplayService.GetEntries();

                    var results = list
                        .Where(d => d.SupportsBrightness)
                        .Select(d =>
                        {
                            var ok = TrySetBrightnessByDisplayId(d.Id, value, out var mech, out var msg);
                            var got = TryGetBrightnessByDisplayId(d.Id, out var current, out _, out _);
                            return new { id = d.Id, ok, mechanism = mech, message = ok ? "OK" : msg, value = got ? current : value };
                        })
                        .ToArray();

                    var anyOk = results.Any(r => r.ok);
                    var firstErr = results.FirstOrDefault(r => !r.ok)?.message ?? "Failed";

                    if (!anyOk) LogErr($"Jas: zlyhalo ({firstErr})");

                    return WithRid(jsonLine, new { type = "brightness_response", ok = anyOk, message = anyOk ? "OK" : firstErr, data = new { displayId = "all", results } });
                }
                else
                {
                    bool ok;
                    string mech;
                    string msg;

                    if (string.Equals(displayId, "internal", StringComparison.OrdinalIgnoreCase))
                    {
                        ok = TrySetBrightnessByDisplayId(displayId, value, out mech, out msg);
                    }
                    else
                    {
                        ok = _brightnessSmoother.SetTarget(displayId, value, out mech, out msg);
                    }

                    if (!ok) LogErr($"{HumanDisplay(displayId)}: jas sa nepodarilo zmeniť ({msg})");

                    return WithRid(jsonLine, new { type = "brightness_response", ok, message = ok ? "OK" : msg, data = new { displayId, value, mechanism = mech } });
                }
            }

            // --- NIGHT MODE ---
            if (type == "night_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "night_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "night_response", ok = false, message = "Unauthorized" });

                var (enabled, intensity) = NightModeService.GetState();
                if (intensity > 0) _lastNightNonZeroIntensity = intensity;

                return WithRid(jsonLine, new { type = "night_response", ok = true, message = "OK", data = new { enabled, intensity, screenDim = ScreenDimService.GetState() } });
            }

            if (type == "night_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "night_response", ok = false, message = "Missing payload", data = EmptyNightData() });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "night_response", ok = false, message = "Unauthorized", data = EmptyNightData() });

                var reqEnabled = TryGetBool(payload, "enabled", fallback: true);
                var reqIntensity = Math.Clamp(TryGetInt(payload, "intensity", fallback: 0), 0, 100);
                int? reqKelvin = null;
                if (payload.TryGetProperty("kelvin", out var kelvinEl) && kelvinEl.ValueKind == JsonValueKind.Number && kelvinEl.TryGetInt32(out var kelvin))
                    reqKelvin = Math.Clamp(kelvin, 500, 6500);

                var reqScreenDim = ScreenDimService.GetState();
                if (payload.TryGetProperty("screenDim", out var dimEl) && dimEl.ValueKind == JsonValueKind.Number && dimEl.TryGetInt32(out var dim))
                    reqScreenDim = Math.Clamp(dim, 0, 100);

                if (reqEnabled)
                {
                    if (reqIntensity <= 0)
                        reqIntensity = Math.Clamp(_lastNightNonZeroIntensity, 1, 100);
                }
                else
                {
                    reqIntensity = 0;
                }

                var ok = NightModeService.TrySet(reqEnabled, reqIntensity, reqKelvin, out var msg);
                var dimOk = ScreenDimService.TrySet(reqEnabled ? reqScreenDim : 0, out var dimMsg);
                var (curEnabled, curIntensity) = NightModeService.GetState();
                if (curIntensity > 0) _lastNightNonZeroIntensity = curIntensity;

                var finalOk = ok && dimOk;
                var finalMsg = finalOk ? "OK" : (!ok ? msg : dimMsg);

                if (finalOk) LogOk($"Nočný režim: {(curEnabled ? $"ZAP ({curIntensity}%)" : "VYP")}");
                else LogErr($"Nočný režim zlyhal: {finalMsg}");

                RefreshServicesStatus();
                return WithRid(jsonLine, new { type = "night_response", ok = finalOk, message = finalMsg, data = new { enabled = curEnabled, intensity = curIntensity, kelvin = reqKelvin, screenDim = ScreenDimService.GetState() } });
            }

            // --- QUIET MODE (from mobile) ---
            if (type == "quiet_get")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "quiet_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "quiet_response", ok = false, message = "Unauthorized" });

                var svc = QuietModeService.GetState();
                if (svc != _quietUiEnabled) _quietUiEnabled = svc;

                return WithRid(jsonLine, new { type = "quiet_response", ok = true, message = "OK", data = new { enabled = _quietUiEnabled } });
            }

            if (type == "quiet_set")
            {
                if (!root.TryGetProperty("payload", out var payload))
                    return WithRid(jsonLine, new { type = "quiet_response", ok = false, message = "Missing payload" });

                var token = GetTokenFromPayload(payload);
                if (!_pairing.IsTokenValid(token))
                    return WithRid(jsonLine, new { type = "quiet_response", ok = false, message = "Unauthorized" });

                bool reqEnabled;
                if (!TryGetBoolNullable(payload, "enabled", out reqEnabled))
                    reqEnabled = !_quietUiEnabled;

                var ok = QuietModeService.TrySet(reqEnabled, out var msg);

                if (ok)
                {
                    _quietUiEnabled = reqEnabled;
                    LogOk($"Tichý režim: {(reqEnabled ? "ZAP" : "VYP")}");
                    RefreshServicesStatus();
                    return WithRid(jsonLine, new { type = "quiet_response", ok = true, message = "OK", data = new { enabled = _quietUiEnabled } });
                }

                LogErr($"Tichý režim zlyhal: {msg}");
                RefreshServicesStatus();
                return WithRid(jsonLine, new { type = "quiet_response", ok = false, message = msg, data = new { enabled = _quietUiEnabled } });
            }

            // Unknown request type: log (throttled) so it isn't silent anymore
            var nowU = DateTime.UtcNow;
            if (_lastUnknownType != type || (nowU - _lastUnknownLogAt) > TimeSpan.FromSeconds(2))
            {
                _lastUnknownType = type;
                _lastUnknownLogAt = nowU;
                LogWarn($"Neznáma požiadavka: {type}");
            }

            return WithRid(jsonLine, new { type = "response", ok = false, message = $"Unknown type: {type}" });
        }
        catch (Exception ex)
        {
            LogErr("Chyba komunikácie (Bad JSON)");
            return JsonSerializer.Serialize(new { type = "response", ok = false, message = "Bad JSON: " + ex.Message });
        }
    }

    // =========================
    // Helpers
    // =========================
    private static string HumanTimerAction(string? action)
        => string.Equals(action, "shutdown", StringComparison.OrdinalIgnoreCase) ? "Vypnutie PC" : "Uspanie PC";

    private static string FormatSeconds(int totalSeconds)
    {
        if (totalSeconds < 0) totalSeconds = 0;
        var ts = TimeSpan.FromSeconds(totalSeconds);
        if (ts.TotalHours >= 1) return $"{(int)ts.TotalHours}:{ts.Minutes:D2}:{ts.Seconds:D2}";
        return $"{ts.Minutes}:{ts.Seconds:D2}";
    }

    // =========================
    // Server ID (stable)
    // =========================
    private static string LoadOrCreateServerId()
    {
        try
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "DesklyPC"
            );
            Directory.CreateDirectory(dir);

            var file = Path.Combine(dir, "server_id.txt");

            if (File.Exists(file))
            {
                var s = File.ReadAllText(file).Trim();
                if (!string.IsNullOrWhiteSpace(s)) return s;
            }

            var id = Guid.NewGuid().ToString("N");
            File.WriteAllText(file, id);
            return id;
        }
        catch
        {
            return Guid.NewGuid().ToString("N");
        }
    }

    // =========================
    // Smoothers (natural fade)
    // =========================
    private sealed class BrightnessSmoother
    {
        public delegate void LogFn(LogLevel lvl, string msg);

        private sealed class State
        {
            public string DisplayId = "";
            public int DdcIndex1Based;
            public int Current;
            public int Target;
            public bool Active;
            public DateTime LastApplyAt = DateTime.MinValue;
            public string LastError = "";
        }

        private readonly object _lock = new();
        private readonly System.Threading.Timer _timer;
        private readonly Dictionary<string, State> _states = new(StringComparer.OrdinalIgnoreCase);
        private readonly LogFn _log;

        private const int STEP_NEAR = 15;
        private const int STEP_FAR = 25;
        private const int TICK_MS = 80;
        private const int APPLY_GUARD_MS = 70;

        public BrightnessSmoother(LogFn log)
        {
            _log = log;
            _timer = new System.Threading.Timer(_ => Tick(), null,
                System.Threading.Timeout.Infinite, System.Threading.Timeout.Infinite);
        }

        public void Start() => _timer.Change(0, TICK_MS);
        public void Stop() => _timer.Change(System.Threading.Timeout.Infinite, System.Threading.Timeout.Infinite);

        public bool SetTarget(string displayId, int target0to100, out string mechanism, out string message)
        {
            mechanism = "DDC/CI (smooth)";
            message = "";
            target0to100 = Math.Clamp(target0to100, 0, 100);

            if (!DisplayService.TryResolveDdcIndex(displayId, out var idx1) || idx1 <= 0)
            {
                mechanism = "UNKNOWN";
                message = "Unknown external display";
                return false;
            }

            // Probe once so we can fail fast (instead of silently failing in Tick)
            // Prefer setting to current value (no visible jump); fallback to target.
            int probeValue = target0to100;
            if (MonitorBrightness.TryGet(idx1, out var cur, out _))
                probeValue = cur;

            if (!MonitorBrightness.TrySet(idx1, probeValue, out var probeMsg))
            {
                mechanism = "DDC/CI";
                message = string.IsNullOrWhiteSpace(probeMsg) ? "DDC/CI set failed" : probeMsg;
                return false;
            }

            lock (_lock)
            {
                if (!_states.TryGetValue(displayId, out var st))
                {
                    st = new State
                    {
                        DisplayId = displayId,
                        DdcIndex1Based = idx1,
                        Current = probeValue,
                        Target = target0to100,
                        Active = true
                    };

                    // Best effort: refresh current after probe
                    if (MonitorBrightness.TryGet(idx1, out var cur2, out _))
                        st.Current = cur2;

                    _states[displayId] = st;
                }

                st.DdcIndex1Based = idx1;
                st.Target = target0to100;
                st.Active = true;
            }

            Start();
            return true;
        }

        private void Tick()
        {
            List<State> work;
            lock (_lock)
            {
                if (_states.Count == 0)
                {
                    Stop();
                    return;
                }
                work = _states.Values.Where(s => s.Active).ToList();
                if (work.Count == 0)
                {
                    Stop();
                    return;
                }
            }

            foreach (var st in work)
            {
                var idx1 = st.DdcIndex1Based;
                if (idx1 <= 0) { st.Active = false; continue; }

                var now = DateTime.UtcNow;
                if ((now - st.LastApplyAt).TotalMilliseconds < APPLY_GUARD_MS)
                    continue;

                var cur = st.Current;
                var tgt = st.Target;

                if (cur == tgt)
                {
                    st.Active = false;
                    continue;
                }

                var diff = tgt - cur;
                var abs = Math.Abs(diff);
                var stepAbs = abs >= 60 ? STEP_FAR : STEP_NEAR;
                var step = Math.Sign(diff) * Math.Min(stepAbs, abs);
                var next = Math.Clamp(cur + step, 0, 100);

                if (MonitorBrightness.TrySet(idx1, next, out var err))
                {
                    st.Current = next;
                    st.LastApplyAt = now;
                    st.LastError = "";
                }
                else
                {
                    // stop trying and expose error once
                    st.Active = false;

                    if (!string.Equals(st.LastError, err ?? "", StringComparison.Ordinal))
                    {
                        st.LastError = err ?? "";
                        _log(LogLevel.Error, string.IsNullOrWhiteSpace(err)
                            ? "Externý jas: zlyhalo nastavenie (DDC/CI)"
                            : $"Externý jas: zlyhalo nastavenie ({err})");
                    }
                }
            }
        }
    }

    private sealed class NightSmoother
    {
        private readonly object _lock = new();
        private readonly System.Threading.Timer _timer;

        private int _targetIntensity = 0;
        private bool _active = false;

        private const int STEP_NEAR = 15;
        private const int STEP_FAR = 25;
        private const int TICK_MS = 80;

        public NightSmoother()
        {
            _timer = new System.Threading.Timer(_ => Tick(), null,
                System.Threading.Timeout.Infinite, System.Threading.Timeout.Infinite);
        }

        public void Start() => _timer.Change(0, TICK_MS);
        public void Stop() => _timer.Change(System.Threading.Timeout.Infinite, System.Threading.Timeout.Infinite);

        public bool SetTarget(int intensity0to100, out string message)
        {
            message = "";
            intensity0to100 = Math.Clamp(intensity0to100, 0, 100);

            lock (_lock)
            {
                _targetIntensity = intensity0to100;
                _active = true;
            }

            return true;
        }

        private void Tick()
        {
            int target;
            bool active;
            lock (_lock)
            {
                target = _targetIntensity;
                active = _active;
            }
            if (!active) return;

            var (enabled, curIntensity) = NightModeService.GetState();
            if (!enabled) curIntensity = 0;

            if (curIntensity == target)
            {
                lock (_lock) _active = false;
                return;
            }

            var diff = target - curIntensity;
            var abs = Math.Abs(diff);
            var stepAbs = abs >= 60 ? STEP_FAR : STEP_NEAR;
            var step = Math.Sign(diff) * Math.Min(stepAbs, abs);
            var next = Math.Clamp(curIntensity + step, 0, 100);

            NightModeService.TrySet(true, next, out _);

            if (next == target)
            {
                lock (_lock) _active = false;
            }
        }
    }
}
