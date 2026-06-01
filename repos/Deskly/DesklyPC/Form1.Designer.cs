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
    private DesklyPC.CardPanel cardLog;
    private DesklyPC.CardPanel cardDiagnostics;

    private Label lblStatusText;

    private Label lblDiagTitle;
    private Label lblExternalBrightnessStatus;
    private Button btnCopyIp;

    private Label lblAppTitle;
    private Label lblStatusChip;

    private Label lblServerTitle;
    private Label lblIpCaption;
    private Label lblIpValue;
    private Label lblPortCaption;
    private NumericUpDown numPort;
    private Button btnStart;
    private Button btnStop;
    private Button btnTestExternalBrightness;

    private Label lblPairTitle;
    private Label lblPinCaption;
    private Label lblPinValue;
    private Button btnPair;

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
        cardLog = new DesklyPC.CardPanel();
        cardDiagnostics = new DesklyPC.CardPanel();

        lblAppTitle = new Label();
        lblStatusChip = new Label();
        lblStatusText = new Label();

        lblServerTitle = new Label();
        lblIpCaption = new Label();
        lblIpValue = new Label();
        lblPortCaption = new Label();
        numPort = new NumericUpDown();
        btnStart = new Button();
        btnStop = new Button();
        btnTestExternalBrightness = new Button();
        btnCopyIp = new Button();

        lblPairTitle = new Label();
        lblPinCaption = new Label();
        lblPinValue = new Label();
        btnPair = new Button();

        lblLogTitle = new Label();
        txtLog = new RichTextBox();

        lblDiagTitle = new Label();
        lblExternalBrightnessStatus = new Label();

        ((System.ComponentModel.ISupportInitialize)numPort).BeginInit();
        SuspendLayout();

        // =========================
        // THEME (Apple-like, from Android colors.xml)
        // =========================
        var bg = ColorTranslator.FromHtml("#0F1117");

        var card = ColorTranslator.FromHtml("#12141C");
        var stroke = ColorTranslator.FromHtml("#2A2F3A");

        var text = ColorTranslator.FromHtml("#FFFFFF");
        var muted = ColorTranslator.FromHtml("#A0A3AA");

        var inputFill = ColorTranslator.FromHtml("#0B0D12");

        // neutral accent (no branding)
        var accent = ColorTranslator.FromHtml("#3A82F7");

        // subtle hover/press
        var hover = ColorTranslator.FromHtml("#1A1D26");
        var pressed = ColorTranslator.FromHtml("#222737");

        // Layout
        int pad = 16;
        int radius = 16;

        // =========================
        // FORM
        // =========================
        AutoScaleDimensions = new SizeF(7F, 15F);
        AutoScaleMode = AutoScaleMode.Font;
        ClientSize = new Size(940, 600);
        Text = "DesklyPC™";
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = bg;
        ForeColor = text;
        Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        MinimizeBox = true;
        ShowInTaskbar = true;
        DoubleBuffered = true;

        // =========================
        // HEADER
        // =========================
        headerBar.Location = new Point(pad, pad);
        headerBar.Size = new Size(940 - pad * 2, 64);
        headerBar.CornerRadius = radius;
        headerBar.CardColor = card;
        headerBar.ShowBorder = true;
        headerBar.BorderColor = stroke;
        headerBar.Shadow = false;

        lblAppTitle.AutoSize = true;
        lblAppTitle.Location = new Point(18, 18);
        lblAppTitle.Text = "desklyPC™";
        lblAppTitle.ForeColor = text;
        lblAppTitle.Font = new Font("Segoe UI Semibold", 15F, FontStyle.Bold, GraphicsUnit.Point);

        // status: dot + text
        lblStatusChip.AutoSize = false;
        lblStatusChip.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        lblStatusChip.Location = new Point(headerBar.Width - 150, 26);
        lblStatusChip.Size = new Size(12, 12);
        lblStatusChip.Text = "";
        lblStatusChip.BackColor = ColorTranslator.FromHtml("#7B7F86");
        lblStatusChip.ForeColor = Color.Transparent;

        lblStatusText.AutoSize = true;
        lblStatusText.Anchor = AnchorStyles.Top | AnchorStyles.Right;
        lblStatusText.Location = new Point(headerBar.Width - 130, 22);
        lblStatusText.Text = "Vypnuté";
        lblStatusText.ForeColor = muted;
        lblStatusText.Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);

        headerBar.Controls.Add(lblAppTitle);
        headerBar.Controls.Add(lblStatusChip);
        headerBar.Controls.Add(lblStatusText);

        // =========================
        // SERVER CARD
        // =========================
        cardServer.Location = new Point(pad, 92);
        cardServer.Size = new Size(590, 170);
        cardServer.CornerRadius = radius;
        cardServer.CardColor = card;
        cardServer.ShowBorder = true;
        cardServer.BorderColor = stroke;
        cardServer.Shadow = false;

        lblServerTitle.AutoSize = true;
        lblServerTitle.Location = new Point(18, 16);
        lblServerTitle.Text = "Pripojenie";
        lblServerTitle.Font = new Font("Segoe UI Semibold", 12.5F, FontStyle.Bold, GraphicsUnit.Point);
        lblServerTitle.ForeColor = text;

        lblIpCaption.AutoSize = true;
        lblIpCaption.Location = new Point(18, 56);
        lblIpCaption.Text = "IP adresa";
        lblIpCaption.ForeColor = muted;

        lblIpValue.AutoSize = true;
        lblIpValue.Location = new Point(18, 80);
        lblIpValue.Text = "—";
        lblIpValue.Font = new Font("Segoe UI Semibold", 11F, FontStyle.Bold, GraphicsUnit.Point);
        lblIpValue.ForeColor = text;

        // Copy IP
        btnCopyIp.Location = new Point(cardServer.Width - 140, 70);
        btnCopyIp.Size = new Size(118, 34);
        btnCopyIp.Text = "Kopírovať";
        btnCopyIp.UseVisualStyleBackColor = false;
        btnCopyIp.FlatStyle = FlatStyle.Flat;
        btnCopyIp.FlatAppearance.BorderSize = 1;
        btnCopyIp.FlatAppearance.BorderColor = stroke;
        btnCopyIp.BackColor = card;
        btnCopyIp.ForeColor = text;
        btnCopyIp.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold, GraphicsUnit.Point);
        btnCopyIp.Cursor = Cursors.Hand;
        btnCopyIp.Click += (_, __) =>
        {
            try { Clipboard.SetText(lblIpValue.Text ?? ""); } catch { }
        };

        btnCopyIp.MouseEnter += (_, __) => btnCopyIp.BackColor = hover;
        btnCopyIp.MouseLeave += (_, __) => btnCopyIp.BackColor = card;
        btnCopyIp.MouseDown += (_, __) => btnCopyIp.BackColor = pressed;
        btnCopyIp.MouseUp += (_, __) => btnCopyIp.BackColor = hover;

        // Port + Start/Stop (advanced hidden)
        lblPortCaption.AutoSize = true;
        lblPortCaption.Location = new Point(18, 0);
        lblPortCaption.Text = "";
        lblPortCaption.ForeColor = muted;
        lblPortCaption.Visible = false;

        numPort.Location = new Point(18, 0);
        numPort.Size = new Size(110, 25);
        numPort.Minimum = 1024;
        numPort.Maximum = 65535;
        numPort.Value = 5050;
        numPort.BorderStyle = BorderStyle.FixedSingle;
        numPort.BackColor = inputFill;
        numPort.ForeColor = text;
        numPort.Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);
        numPort.Visible = false;

        btnStart.Location = new Point(18, 0);
        btnStart.Size = new Size(120, 34);
        btnStart.Text = "Start";
        btnStart.Visible = false;
        btnStart.UseVisualStyleBackColor = false;
        btnStart.FlatStyle = FlatStyle.Flat;
        btnStart.FlatAppearance.BorderSize = 0;
        btnStart.BackColor = accent;
        btnStart.ForeColor = bg;
        btnStart.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold, GraphicsUnit.Point);
        btnStart.Cursor = Cursors.Hand;
        btnStart.Click += btnStart_Click;

        btnStart.MouseEnter += (_, __) => btnStart.BackColor = ColorTranslator.FromHtml("#D0D0D0");
        btnStart.MouseLeave += (_, __) => btnStart.BackColor = accent;
        btnStart.MouseDown += (_, __) => btnStart.BackColor = ColorTranslator.FromHtml("#B8B8B8");
        btnStart.MouseUp += (_, __) => btnStart.BackColor = accent;

        btnStop.Location = new Point(18, 0);
        btnStop.Size = new Size(120, 34);
        btnStop.Text = "Stop";
        btnStop.Visible = false;
        btnStop.Enabled = false;
        btnStop.UseVisualStyleBackColor = false;
        btnStop.FlatStyle = FlatStyle.Flat;
        btnStop.FlatAppearance.BorderSize = 1;
        btnStop.FlatAppearance.BorderColor = stroke;
        btnStop.BackColor = card;
        btnStop.ForeColor = text;
        btnStop.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold, GraphicsUnit.Point);
        btnStop.Cursor = Cursors.Hand;
        btnStop.Click += btnStop_Click;

        btnStop.MouseEnter += (_, __) => { if (btnStop.Enabled) btnStop.BackColor = hover; };
        btnStop.MouseLeave += (_, __) => btnStop.BackColor = card;
        btnStop.MouseDown += (_, __) => { if (btnStop.Enabled) btnStop.BackColor = pressed; };
        btnStop.MouseUp += (_, __) => { if (btnStop.Enabled) btnStop.BackColor = hover; };

        cardServer.Controls.Add(lblServerTitle);
        cardServer.Controls.Add(lblIpCaption);
        cardServer.Controls.Add(lblIpValue);
        cardServer.Controls.Add(btnCopyIp);
        cardServer.Controls.Add(lblPortCaption);
        cardServer.Controls.Add(numPort);
        cardServer.Controls.Add(btnStart);
        cardServer.Controls.Add(btnStop);

        // =========================
        // PAIRING CARD
        // =========================
        cardPairing.Location = new Point(622, 92);
        cardPairing.Size = new Size(286, 170);
        cardPairing.CornerRadius = radius;
        cardPairing.CardColor = card;
        cardPairing.ShowBorder = true;
        cardPairing.BorderColor = stroke;
        cardPairing.Shadow = false;

        lblPairTitle.AutoSize = true;
        lblPairTitle.Location = new Point(18, 16);
        lblPairTitle.Text = "Párovanie";
        lblPairTitle.Font = new Font("Segoe UI Semibold", 12.5F, FontStyle.Bold, GraphicsUnit.Point);
        lblPairTitle.ForeColor = text;

        lblPinCaption.AutoSize = true;
        lblPinCaption.Location = new Point(18, 56);
        lblPinCaption.Text = "Kód (platí 120s)";
        lblPinCaption.ForeColor = muted;

        lblPinValue.AutoSize = true;
        lblPinValue.Location = new Point(18, 80);
        lblPinValue.Text = "------";
        lblPinValue.Font = new Font("Segoe UI Semibold", 22F, FontStyle.Bold, GraphicsUnit.Point);
        lblPinValue.ForeColor = text;

        // Generate PIN
        btnPair.Location = new Point(18, 118);
        btnPair.Size = new Size(cardPairing.Width - 36, 34); // ✅ fits inside card
        btnPair.Text = "Vygenerovať kód";
        btnPair.Enabled = false;
        btnPair.UseVisualStyleBackColor = false;
        btnPair.FlatStyle = FlatStyle.Flat;
        btnPair.FlatAppearance.BorderSize = 1;
        btnPair.FlatAppearance.BorderColor = stroke;
        btnPair.BackColor = card;
        btnPair.ForeColor = text;
        btnPair.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold, GraphicsUnit.Point);
        btnPair.Cursor = Cursors.Hand;
        btnPair.Click += btnPair_Click;

        btnPair.MouseEnter += (_, __) => { if (btnPair.Enabled) btnPair.BackColor = hover; };
        btnPair.MouseLeave += (_, __) => btnPair.BackColor = card;
        btnPair.MouseDown += (_, __) => { if (btnPair.Enabled) btnPair.BackColor = pressed; };
        btnPair.MouseUp += (_, __) => { if (btnPair.Enabled) btnPair.BackColor = hover; };

        cardPairing.Controls.Add(lblPairTitle);
        cardPairing.Controls.Add(lblPinCaption);
        cardPairing.Controls.Add(lblPinValue);
        cardPairing.Controls.Add(btnPair);

        // =========================
        // DIAGNOSTICS CARD
        // =========================
        cardDiagnostics.Location = new Point(pad, 274);
        cardDiagnostics.Size = new Size(940 - pad * 2, 88);
        cardDiagnostics.CornerRadius = radius;
        cardDiagnostics.CardColor = card;
        cardDiagnostics.ShowBorder = true;
        cardDiagnostics.BorderColor = stroke;
        cardDiagnostics.Shadow = true;

        lblDiagTitle.AutoSize = true;
        lblDiagTitle.Location = new Point(18, 14);
        lblDiagTitle.Text = "Externý monitor";
        lblDiagTitle.Font = new Font("Segoe UI Semibold", 12.5F, FontStyle.Bold, GraphicsUnit.Point);
        lblDiagTitle.ForeColor = text;

        lblExternalBrightnessStatus.AutoSize = true;
        lblExternalBrightnessStatus.Location = new Point(18, 44);
        lblExternalBrightnessStatus.Text = "Overenie podpory ovládania jasu";
        lblExternalBrightnessStatus.ForeColor = muted;
        lblExternalBrightnessStatus.Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);

        btnTestExternalBrightness.Location = new Point(cardDiagnostics.Width - 210, 26);
        btnTestExternalBrightness.Size = new Size(192, 34);
        btnTestExternalBrightness.Text = "Otestovať";
        btnTestExternalBrightness.Enabled = true;
        btnTestExternalBrightness.UseVisualStyleBackColor = false;
        btnTestExternalBrightness.FlatStyle = FlatStyle.Flat;
        btnTestExternalBrightness.FlatAppearance.BorderSize = 1;
        btnTestExternalBrightness.FlatAppearance.BorderColor = stroke;
        btnTestExternalBrightness.BackColor = card;
        btnTestExternalBrightness.ForeColor = text;
        btnTestExternalBrightness.Font = new Font("Segoe UI Semibold", 10F, FontStyle.Bold, GraphicsUnit.Point);
        btnTestExternalBrightness.Cursor = Cursors.Hand;
        btnTestExternalBrightness.Click += btnTestExternalBrightness_Click;

        btnTestExternalBrightness.MouseEnter += (_, __) => btnTestExternalBrightness.BackColor = hover;
        btnTestExternalBrightness.MouseLeave += (_, __) => btnTestExternalBrightness.BackColor = card;
        btnTestExternalBrightness.MouseDown += (_, __) => btnTestExternalBrightness.BackColor = pressed;
        btnTestExternalBrightness.MouseUp += (_, __) => btnTestExternalBrightness.BackColor = hover;

        cardDiagnostics.Controls.Add(lblDiagTitle);
        cardDiagnostics.Controls.Add(lblExternalBrightnessStatus);
        cardDiagnostics.Controls.Add(btnTestExternalBrightness);

        // =========================
        // ACTIVITY CARD
        // =========================
        cardLog.Location = new Point(pad, 372);
        cardLog.Size = new Size(940 - pad * 2, 212);
        cardLog.CornerRadius = radius;
        cardLog.CardColor = card;
        cardLog.ShowBorder = true;
        cardLog.BorderColor = stroke;
        cardLog.Shadow = false;

        lblLogTitle.AutoSize = true;
        lblLogTitle.Location = new Point(18, 16);
        lblLogTitle.Text = "Aktivita";
        lblLogTitle.Font = new Font("Segoe UI Semibold", 12.5F, FontStyle.Bold, GraphicsUnit.Point);
        lblLogTitle.ForeColor = text;

        txtLog.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
        txtLog.Location = new Point(18, 54);
        // ✅ Fix: keep some padding at the bottom so last line + scrollbar isn't clipped
        txtLog.Size = new Size(cardLog.Width - 36, cardLog.Height - 72);

        txtLog.ReadOnly = true;
        txtLog.BorderStyle = BorderStyle.FixedSingle;
        txtLog.BackColor = inputFill;
        txtLog.ForeColor = text;
        txtLog.Font = new Font("Segoe UI", 10F, FontStyle.Regular, GraphicsUnit.Point);
        txtLog.DetectUrls = false;
        txtLog.ScrollBars = RichTextBoxScrollBars.Vertical;
        txtLog.TabStop = false;

        cardLog.Controls.Add(lblLogTitle);
        cardLog.Controls.Add(txtLog);

        // =========================
        // ADD CONTROLS
        // =========================
        Controls.Add(headerBar);
        Controls.Add(cardServer);
        Controls.Add(cardPairing);
        Controls.Add(cardDiagnostics);
        Controls.Add(cardLog);

        ((System.ComponentModel.ISupportInitialize)numPort).EndInit();
        ResumeLayout(false);
        PerformLayout();
    }
}
