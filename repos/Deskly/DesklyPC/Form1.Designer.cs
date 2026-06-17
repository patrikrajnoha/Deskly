using System;
using System.Drawing;
using System.Windows.Forms;

namespace DesklyPC;

partial class Form1
{
    private System.ComponentModel.IContainer components = null;

    private DesklyPC.CardPanel headerBar;
    private DesklyPC.CardPanel cardServer;
    private DesklyPC.CardPanel cardPairing;
    private DesklyPC.CardPanel cardClient;
    private DesklyPC.CardPanel cardDiagnostics;
    private DesklyPC.CardPanel cardLog;

    private Label lblAppTitle;
    private Label lblHeaderSubtitle;
    private Label lblStatusChip;
    private Label lblStatusText;

    private Label lblServerTitle;
    private Label lblIpCaption;
    private Label lblIpValue;
    private Label lblTcpCaption;
    private Label lblTcpValue;
    private Label lblUdpCaption;
    private Label lblUdpValue;
    private Label lblPortCaption;
    private NumericUpDown numPort;
    private Button btnStart;
    private Button btnStop;
    private Button btnRestartHost;
    private Button btnMinimizeTray;
    private Button btnAppShortcuts;
    private CheckBox chkStartWithWindows;
    private Button btnCopyIp;

    private Label lblPairTitle;
    private Label lblPairStatus;
    private Label lblPairInstruction;
    private Label lblPinCaption;
    private Label lblPinValue;
    private Button btnPair;
    private Button btnCopyPairingInfo;
    private Button btnForgetPairing;

    private Label lblClientTitle;
    private Label lblClientStatus;
    private Label lblLastClientCaption;
    private Label lblLastClientValue;
    private Label lblLastConnectionCaption;
    private Label lblLastConnectionValue;
    private Label lblAuthorizedCaption;
    private Label lblAuthorizedValue;

    private Label lblDiagTitle;
    private Label lblLastErrorCaption;
    private Label lblLastErrorValue;
    private Button btnToggleLogs;

    private Label lblLogTitle;
    private RichTextBox txtLog;

    protected override void Dispose(bool disposing)
    {
        if (disposing && (components != null))
            components.Dispose();
        base.Dispose(disposing);
    }

    private void InitializeComponent()
    {
        components = new System.ComponentModel.Container();

        headerBar = new DesklyPC.CardPanel();
        cardServer = new DesklyPC.CardPanel();
        cardPairing = new DesklyPC.CardPanel();
        cardClient = new DesklyPC.CardPanel();
        cardDiagnostics = new DesklyPC.CardPanel();
        cardLog = new DesklyPC.CardPanel();

        lblAppTitle = new Label();
        lblHeaderSubtitle = new Label();
        lblStatusChip = new Label();
        lblStatusText = new Label();

        lblServerTitle = new Label();
        lblIpCaption = new Label();
        lblIpValue = new Label();
        lblTcpCaption = new Label();
        lblTcpValue = new Label();
        lblUdpCaption = new Label();
        lblUdpValue = new Label();
        lblPortCaption = new Label();
        numPort = new NumericUpDown();
        btnStart = new Button();
        btnStop = new Button();
        btnRestartHost = new Button();
        btnMinimizeTray = new Button();
        btnAppShortcuts = new Button();
        chkStartWithWindows = new CheckBox();
        btnCopyIp = new Button();

        lblPairTitle = new Label();
        lblPairStatus = new Label();
        lblPairInstruction = new Label();
        lblPinCaption = new Label();
        lblPinValue = new Label();
        btnPair = new Button();
        btnCopyPairingInfo = new Button();
        btnForgetPairing = new Button();

        lblClientTitle = new Label();
        lblClientStatus = new Label();
        lblLastClientCaption = new Label();
        lblLastClientValue = new Label();
        lblLastConnectionCaption = new Label();
        lblLastConnectionValue = new Label();
        lblAuthorizedCaption = new Label();
        lblAuthorizedValue = new Label();

        lblDiagTitle = new Label();
        lblLastErrorCaption = new Label();
        lblLastErrorValue = new Label();
        btnToggleLogs = new Button();

        lblLogTitle = new Label();
        txtLog = new RichTextBox();

        ((System.ComponentModel.ISupportInitialize)numPort).BeginInit();
        SuspendLayout();

        var bg = ColorTranslator.FromHtml("#0F1216");
        var surface = ColorTranslator.FromHtml("#171B20");
        var surface2 = ColorTranslator.FromHtml("#20262D");
        var stroke = ColorTranslator.FromHtml("#2A3038");
        var text = ColorTranslator.FromHtml("#F4F6F8");
        var muted = ColorTranslator.FromHtml("#AEB7C2");
        var accent = ColorTranslator.FromHtml("#0A84FF");
        var accentSoft = ColorTranslator.FromHtml("#142A42");
        var press = ColorTranslator.FromHtml("#17395A");
        var danger = ColorTranslator.FromHtml("#F47C7C");

        int pad = 16;
        int gap = 12;
        int radius = 8;
        var titleFont = new Font("Segoe UI Semibold", 11.5F, FontStyle.Bold, GraphicsUnit.Point);
        var labelFont = new Font("Segoe UI", 9F, FontStyle.Regular, GraphicsUnit.Point);
        var valueFont = new Font("Segoe UI Semibold", 10.5F, FontStyle.Bold, GraphicsUnit.Point);

        Label Caption(string caption, int x, int y)
        {
            return new Label
            {
                AutoSize = true,
                Location = new Point(x, y),
                Text = caption,
                ForeColor = muted,
                Font = labelFont
            };
        }

        Label Value(string value, int x, int y, int width)
        {
            return new Label
            {
                AutoSize = false,
                Location = new Point(x, y),
                Size = new Size(width, 24),
                Text = value,
                ForeColor = text,
                Font = valueFont
            };
        }

        void StyleCard(DesklyPC.CardPanel panel)
        {
            panel.CornerRadius = radius;
            panel.CardColor = surface;
            panel.ShowBorder = true;
            panel.BorderColor = stroke;
            panel.Shadow = false;
        }

        void StyleButton(Button button)
        {
            button.UseVisualStyleBackColor = false;
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = 1;
            button.FlatAppearance.BorderColor = stroke;
            button.BackColor = surface2;
            button.ForeColor = text;
            button.Font = new Font("Segoe UI Semibold", 9F, FontStyle.Bold, GraphicsUnit.Point);
            button.Cursor = Cursors.Hand;
            button.MouseEnter += (_, __) => { if (button.Enabled) button.BackColor = accentSoft; };
            button.MouseLeave += (_, __) => { if (button.Enabled) button.BackColor = surface2; };
            button.MouseDown += (_, __) => { if (button.Enabled) button.BackColor = press; };
            button.MouseUp += (_, __) => { if (button.Enabled) button.BackColor = accentSoft; };
        }

        var toolTip = new ToolTip(components)
        {
            AutomaticDelay = 300,
            ReshowDelay = 120,
            ShowAlways = true
        };

        AutoScaleDimensions = new SizeF(7F, 15F);
        AutoScaleMode = AutoScaleMode.Font;
        ClientSize = new Size(820, 620);
        MinimumSize = new Size(800, 620);
        Text = "Deskly Host";
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = bg;
        ForeColor = text;
        Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);
        FormBorderStyle = FormBorderStyle.Sizable;
        MaximizeBox = true;
        MinimizeBox = true;
        ShowInTaskbar = true;
        DoubleBuffered = true;
        AutoScroll = true;
        AutoScrollMinSize = new Size(760, 520);

        headerBar.Location = new Point(pad, pad);
        headerBar.Size = new Size(728, 78);
        headerBar.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        StyleCard(headerBar);
        headerBar.CardColor = ColorTranslator.FromHtml("#12161B");

        lblAppTitle.AutoSize = true;
        lblAppTitle.Location = new Point(18, 13);
        lblAppTitle.Text = "Deskly Host";
        lblAppTitle.ForeColor = text;
        lblAppTitle.Font = new Font("Segoe UI Semibold", 17F, FontStyle.Bold, GraphicsUnit.Point);

        lblHeaderSubtitle.AutoSize = false;
        lblHeaderSubtitle.Location = new Point(20, 44);
        lblHeaderSubtitle.Size = new Size(470, 20);
        lblHeaderSubtitle.Text = "Runs quietly in the background.";
        lblHeaderSubtitle.ForeColor = muted;
        lblHeaderSubtitle.Font = labelFont;

        lblStatusChip.AutoSize = false;
        lblStatusChip.Location = new Point(552, 31);
        lblStatusChip.Size = new Size(9, 9);
        lblStatusChip.Text = "";
        lblStatusChip.BackColor = ColorTranslator.FromHtml("#8B98AA");
        lblStatusChip.ForeColor = Color.Transparent;

        lblStatusText.AutoSize = false;
        lblStatusText.Location = new Point(574, 21);
        lblStatusText.Size = new Size(140, 30);
        lblStatusText.Text = "Stopped";
        lblStatusText.TextAlign = ContentAlignment.MiddleCenter;
        lblStatusText.ForeColor = muted;
        lblStatusText.BackColor = surface2;
        lblStatusText.Font = new Font("Segoe UI Semibold", 9.5F, FontStyle.Bold, GraphicsUnit.Point);

        headerBar.Controls.Add(lblAppTitle);
        headerBar.Controls.Add(lblHeaderSubtitle);
        headerBar.Controls.Add(lblStatusChip);
        headerBar.Controls.Add(lblStatusText);

        cardServer.Location = new Point(pad, 106);
        cardServer.Size = new Size(728, 132);
        cardServer.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        StyleCard(cardServer);

        lblServerTitle.AutoSize = true;
        lblServerTitle.Location = new Point(16, 14);
        lblServerTitle.Text = "This PC";
        lblServerTitle.ForeColor = text;
        lblServerTitle.Font = titleFont;

        lblIpCaption = Caption("IP", 16, 48);
        lblIpValue = Value("-", 16, 68, 132);

        lblTcpCaption = Caption("Port", 164, 48);
        lblTcpValue = Value("5050", 164, 68, 72);

        lblUdpCaption = Caption("Discovery", 252, 48);
        lblUdpValue = Value("5051", 252, 68, 106);

        lblAuthorizedCaption = Caption("Phone", 374, 48);
        lblAuthorizedValue = Value("No phone", 374, 68, 154);

        lblClientTitle.AutoSize = true;
        lblClientTitle.Location = new Point(548, 48);
        lblClientTitle.Text = "Status";
        lblClientTitle.ForeColor = muted;
        lblClientTitle.Font = labelFont;

        lblClientStatus.AutoSize = false;
        lblClientStatus.Location = new Point(548, 68);
        lblClientStatus.Size = new Size(130, 24);
        lblClientStatus.Text = "Pair required";
        lblClientStatus.ForeColor = muted;
        lblClientStatus.Font = valueFont;

        btnCopyIp.Location = new Point(16, 98);
        btnCopyIp.Size = new Size(92, 28);
        btnCopyIp.Text = "Copy IP";
        StyleButton(btnCopyIp);
        btnCopyIp.Click += (_, __) => CopyIpToClipboard();
        toolTip.SetToolTip(btnCopyIp, "Copy this PC address.");

        btnCopyPairingInfo.Location = new Point(96, 98);
        btnCopyPairingInfo.Size = new Size(104, 28);
        btnCopyPairingInfo.Text = "Copy setup";
        StyleButton(btnCopyPairingInfo);
        btnCopyPairingInfo.Click += btnCopyPairingInfo_Click;
        toolTip.SetToolTip(btnCopyPairingInfo, "Copy IP, port and current PIN.");

        lblPortCaption.AutoSize = true;
        lblPortCaption.Location = new Point(0, 0);
        lblPortCaption.Visible = false;

        numPort.Location = new Point(0, 0);
        numPort.Size = new Size(110, 25);
        numPort.Minimum = 1024;
        numPort.Maximum = 65535;
        numPort.Value = 5050;
        numPort.Visible = false;

        cardServer.Controls.Add(lblServerTitle);
        cardServer.Controls.Add(lblIpCaption);
        cardServer.Controls.Add(lblIpValue);
        cardServer.Controls.Add(lblTcpCaption);
        cardServer.Controls.Add(lblTcpValue);
        cardServer.Controls.Add(lblUdpCaption);
        cardServer.Controls.Add(lblUdpValue);
        cardServer.Controls.Add(lblAuthorizedCaption);
        cardServer.Controls.Add(lblAuthorizedValue);
        cardServer.Controls.Add(lblClientTitle);
        cardServer.Controls.Add(lblClientStatus);
        cardServer.Controls.Add(btnCopyIp);
        cardServer.Controls.Add(lblPortCaption);
        cardServer.Controls.Add(numPort);

        cardPairing.Location = new Point(pad, 250);
        cardPairing.Size = new Size(356, 132);
        StyleCard(cardPairing);

        lblPairTitle.AutoSize = true;
        lblPairTitle.Location = new Point(16, 14);
        lblPairTitle.Text = "Pair Phone";
        lblPairTitle.ForeColor = text;
        lblPairTitle.Font = titleFont;

        lblPairStatus.AutoSize = false;
        lblPairStatus.Location = new Point(16, 44);
        lblPairStatus.Size = new Size(166, 22);
        lblPairStatus.Text = "Pair required";
        lblPairStatus.ForeColor = muted;
        lblPairStatus.Font = labelFont;

        lblPinCaption = Caption("PIN", 216, 36);
        lblPinValue = Value("------", 214, 54, 120);
        lblPinValue.Font = new Font("Segoe UI Semibold", 22F, FontStyle.Bold, GraphicsUnit.Point);
        lblPinValue.ForeColor = accent;

        lblPairInstruction.AutoSize = false;
        lblPairInstruction.Location = new Point(16, 66);
        lblPairInstruction.Size = new Size(178, 28);
        lblPairInstruction.Text = "Enter this PIN in Deskly.";
        lblPairInstruction.ForeColor = muted;
        lblPairInstruction.Font = labelFont;

        btnPair.Location = new Point(16, 98);
        btnPair.Size = new Size(72, 28);
        btnPair.Text = "Pair";
        btnPair.Enabled = false;
        StyleButton(btnPair);
        btnPair.Click += btnPair_Click;

        btnForgetPairing.Location = new Point(208, 98);
        btnForgetPairing.Size = new Size(92, 28);
        btnForgetPairing.Text = "Forget";
        StyleButton(btnForgetPairing);
        btnForgetPairing.ForeColor = danger;
        btnForgetPairing.Click += btnForgetPairing_Click;

        cardPairing.Controls.Add(lblPairTitle);
        cardPairing.Controls.Add(lblPairStatus);
        cardPairing.Controls.Add(lblPinCaption);
        cardPairing.Controls.Add(lblPinValue);
        cardPairing.Controls.Add(lblPairInstruction);
        cardPairing.Controls.Add(btnPair);
        cardPairing.Controls.Add(btnCopyPairingInfo);
        cardPairing.Controls.Add(btnForgetPairing);

        cardClient.Location = new Point(pad + 356 + gap, 250);
        cardClient.Size = new Size(360, 132);
        StyleCard(cardClient);

        var lblHostControlsTitle = new Label
        {
            AutoSize = true,
            Location = new Point(16, 14),
            Text = "Advanced",
            ForeColor = text,
            Font = titleFont
        };

        btnStart.Location = new Point(16, 46);
        btnStart.Size = new Size(104, 30);
        btnStart.Text = "Start";
        StyleButton(btnStart);
        btnStart.Click += btnStart_Click;

        btnStop.Location = new Point(128, 46);
        btnStop.Size = new Size(96, 30);
        btnStop.Text = "Stop";
        btnStop.Enabled = false;
        StyleButton(btnStop);
        btnStop.Click += btnStop_Click;

        btnRestartHost.Location = new Point(232, 46);
        btnRestartHost.Size = new Size(104, 30);
        btnRestartHost.Text = "Restart";
        StyleButton(btnRestartHost);
        btnRestartHost.Click += btnRestartHost_Click;

        btnMinimizeTray.Location = new Point(16, 88);
        btnMinimizeTray.Size = new Size(154, 30);
        btnMinimizeTray.Text = "Hide";
        StyleButton(btnMinimizeTray);
        btnMinimizeTray.Click += btnMinimizeTray_Click;
        toolTip.SetToolTip(btnMinimizeTray, "Hide Deskly. The host keeps running in the tray.");

        btnAppShortcuts.Location = new Point(178, 88);
        btnAppShortcuts.Size = new Size(158, 30);
        btnAppShortcuts.Text = "Apps";
        StyleButton(btnAppShortcuts);
        btnAppShortcuts.Click += btnAppShortcuts_Click;

        chkStartWithWindows.AutoSize = true;
        chkStartWithWindows.Location = new Point(180, 16);
        chkStartWithWindows.Text = "Open at login";
        chkStartWithWindows.ForeColor = muted;
        chkStartWithWindows.Font = labelFont;
        chkStartWithWindows.FlatStyle = FlatStyle.Flat;
        chkStartWithWindows.CheckedChanged += chkStartWithWindows_CheckedChanged;

        cardClient.Controls.Add(lblHostControlsTitle);
        cardClient.Controls.Add(chkStartWithWindows);
        cardClient.Controls.Add(btnStart);
        cardClient.Controls.Add(btnStop);
        cardClient.Controls.Add(btnRestartHost);
        cardClient.Controls.Add(btnMinimizeTray);
        cardClient.Controls.Add(btnAppShortcuts);

        cardDiagnostics.Location = new Point(pad, 394);
        cardDiagnostics.Size = new Size(728, 96);
        cardDiagnostics.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        StyleCard(cardDiagnostics);

        lblDiagTitle.AutoSize = true;
        lblDiagTitle.Location = new Point(16, 14);
        lblDiagTitle.Text = "Activity";
        lblDiagTitle.ForeColor = text;
        lblDiagTitle.Font = titleFont;

        lblLastClientCaption = Caption("Phone", 16, 42);
        lblLastClientValue = Value("No phone", 16, 62, 170);
        lblLastClientValue.Font = labelFont;

        lblLastConnectionCaption = Caption("Last action", 206, 42);
        lblLastConnectionValue = Value("Never", 206, 62, 180);
        lblLastConnectionValue.Font = labelFont;

        lblLastErrorCaption = Caption("Issue", 396, 42);
        lblLastErrorValue = Value("No errors", 396, 62, 184);
        lblLastErrorValue.Font = labelFont;
        lblLastErrorValue.ForeColor = muted;

        btnToggleLogs.Location = new Point(606, 52);
        btnToggleLogs.Size = new Size(106, 30);
        btnToggleLogs.Text = "Logs";
        StyleButton(btnToggleLogs);
        btnToggleLogs.Click += btnToggleLogs_Click;

        cardDiagnostics.Controls.Add(lblDiagTitle);
        cardDiagnostics.Controls.Add(lblLastClientCaption);
        cardDiagnostics.Controls.Add(lblLastClientValue);
        cardDiagnostics.Controls.Add(lblLastConnectionCaption);
        cardDiagnostics.Controls.Add(lblLastConnectionValue);
        cardDiagnostics.Controls.Add(lblLastErrorCaption);
        cardDiagnostics.Controls.Add(lblLastErrorValue);
        cardDiagnostics.Controls.Add(btnToggleLogs);

        cardLog.Location = new Point(pad, 496);
        cardLog.Size = new Size(728, 174);
        cardLog.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        StyleCard(cardLog);
        cardLog.Visible = false;

        lblLogTitle.AutoSize = true;
        lblLogTitle.Location = new Point(16, 12);
        lblLogTitle.Text = "Logs";
        lblLogTitle.ForeColor = text;
        lblLogTitle.Font = titleFont;

        txtLog.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        txtLog.Location = new Point(16, 40);
        txtLog.Size = new Size(696, 134);
        txtLog.ReadOnly = true;
        txtLog.BorderStyle = BorderStyle.FixedSingle;
        txtLog.BackColor = ColorTranslator.FromHtml("#101418");
        txtLog.ForeColor = text;
        txtLog.Font = new Font("Consolas", 9F, FontStyle.Regular, GraphicsUnit.Point);
        txtLog.DetectUrls = false;
        txtLog.ScrollBars = RichTextBoxScrollBars.Vertical;
        txtLog.TabStop = false;

        cardLog.Controls.Add(lblLogTitle);
        cardLog.Controls.Add(txtLog);

        Controls.Add(headerBar);
        Controls.Add(cardServer);
        Controls.Add(cardPairing);
        Controls.Add(cardClient);
        Controls.Add(cardDiagnostics);
        Controls.Add(cardLog);

        ((System.ComponentModel.ISupportInitialize)numPort).EndInit();
        ResumeLayout(false);
        PerformLayout();
    }
}
