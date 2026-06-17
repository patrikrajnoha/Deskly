using System;
using System.ComponentModel;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace DesklyPC
{
    public class CardPanel : Panel
    {
        private int _cornerRadius = 12;
        private bool _showBorder = true;
        private Color _borderColor = Color.FromArgb(32, 255, 255, 255);
        private Color _cardColor = Color.FromArgb(18, 20, 28); // #12141C

        private bool _shadow = true;
        private int _shadowSize = 10;
        private int _shadowOffsetY = 4;
        private Color _shadowColor = Color.FromArgb(60, 0, 0, 0);

        public CardPanel()
        {
            SetStyle(ControlStyles.UserPaint |
                     ControlStyles.AllPaintingInWmPaint |
                     ControlStyles.OptimizedDoubleBuffer |
                     ControlStyles.ResizeRedraw |
                     ControlStyles.SupportsTransparentBackColor, true);

            DoubleBuffered = true;
            BackColor = Color.Transparent;
        }

        [Category("Appearance")]
        [DefaultValue(12)]
        public int CornerRadius
        {
            get => _cornerRadius;
            set { _cornerRadius = Math.Max(0, value); Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(true)]
        public bool ShowBorder
        {
            get => _showBorder;
            set { _showBorder = value; Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(typeof(Color), "40, 255, 255, 255")]
        public Color BorderColor
        {
            get => _borderColor;
            set { _borderColor = value; Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(typeof(Color), "18, 20, 28")]
        public Color CardColor
        {
            get => _cardColor;
            set { _cardColor = value; Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(true)]
        public bool Shadow
        {
            get => _shadow;
            set { _shadow = value; Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(8)]
        public int ShadowSize
        {
            get => _shadowSize;
            set { _shadowSize = Math.Max(0, value); Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(3)]
        public int ShadowOffsetY
        {
            get => _shadowOffsetY;
            set { _shadowOffsetY = value; Invalidate(); }
        }

        [Category("Appearance")]
        [DefaultValue(typeof(Color), "70, 0, 0, 0")]
        public Color ShadowColor
        {
            get => _shadowColor;
            set { _shadowColor = value; Invalidate(); }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;

            var rect = ClientRectangle;
            if (rect.Width <= 1 || rect.Height <= 1) return;

            // nech si nestrihneš okraj
            var cardRect = Rectangle.Inflate(rect, -1, -1);

            using var cardPath = RoundedRect(cardRect, CornerRadius);

            // Shadow (veľmi jemný)
            if (Shadow && ShadowSize > 0)
            {
                for (int i = ShadowSize; i >= 1; i--)
                {
                    int a = (int)(ShadowColor.A * (i / (float)(ShadowSize * 1.4f)));
                    if (a <= 0) continue;

                    var c = Color.FromArgb(a, ShadowColor.R, ShadowColor.G, ShadowColor.B);
                    var r = Rectangle.Inflate(cardRect, i, i);
                    r.Offset(0, ShadowOffsetY);

                    using var p = RoundedRect(r, CornerRadius + i);
                    using var b = new SolidBrush(c);
                    e.Graphics.FillPath(b, p);
                }
            }

            // Fill
            using (var fill = new SolidBrush(CardColor))
                e.Graphics.FillPath(fill, cardPath);

            // Border (jemný)
            if (ShowBorder)
            {
                using var pen = new Pen(BorderColor, 1f);
                e.Graphics.DrawPath(pen, cardPath);
            }

            base.OnPaint(e);
        }

        private static GraphicsPath RoundedRect(Rectangle bounds, int radius)
        {
            var path = new GraphicsPath();
            int r = Math.Max(0, radius);
            if (r == 0)
            {
                path.AddRectangle(bounds);
                path.CloseFigure();
                return path;
            }

            int d = r * 2;
            path.AddArc(bounds.X, bounds.Y, d, d, 180, 90);
            path.AddArc(bounds.Right - d, bounds.Y, d, d, 270, 90);
            path.AddArc(bounds.Right - d, bounds.Bottom - d, d, d, 0, 90);
            path.AddArc(bounds.X, bounds.Bottom - d, d, d, 90, 90);
            path.CloseFigure();
            return path;
        }
    }
}
