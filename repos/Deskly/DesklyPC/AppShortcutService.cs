using System.Diagnostics;
using System.Drawing;
using System.Security.Cryptography;
using System.Text.Json;
using System.Windows.Forms;

namespace DesklyPC;

public static class AppShortcutService
{
    public sealed record AppShortcutSlot(int Slot, string Label, string Path, string Arguments = "");
    public sealed record AppCatalogItem(string Id, string Label, string Path);

    private const int SlotCount = 5;
    private static readonly object Gate = new();

    private static readonly string ConfigDir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "DesklyPC");

    private static readonly string ConfigPath = Path.Combine(ConfigDir, "app_shortcuts.json");

    public static IReadOnlyList<AppShortcutSlot> GetSlots()
    {
        lock (Gate)
        {
            return LoadSlots()
                .OrderBy(x => x.Slot)
                .Select(x => x with { })
                .ToArray();
        }
    }

    public static bool TryOpen(int slot, out string message, out AppShortcutSlot? shortcut)
    {
        shortcut = null;
        if (slot is < 1 or > SlotCount)
        {
            message = "Invalid app slot";
            return false;
        }

        var current = GetSlots().FirstOrDefault(x => x.Slot == slot);
        if (current == null || string.IsNullOrWhiteSpace(current.Path))
        {
            message = "App slot is empty";
            return false;
        }

        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = current.Path,
                Arguments = current.Arguments ?? "",
                UseShellExecute = true
            });

            shortcut = current;
            message = "OK";
            return true;
        }
        catch (Exception ex)
        {
            shortcut = current;
            message = ex.Message;
            return false;
        }
    }

    public static IReadOnlyList<AppCatalogItem> GetCatalog()
    {
        var dirs = new[]
        {
            Environment.GetFolderPath(Environment.SpecialFolder.StartMenu),
            Environment.GetFolderPath(Environment.SpecialFolder.CommonStartMenu)
        };

        return dirs
            .Where(d => !string.IsNullOrWhiteSpace(d) && Directory.Exists(d))
            .SelectMany(EnumerateShortcutFiles)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .Select(path => new AppCatalogItem(MakeCatalogId(path), CleanLabel(Path.GetFileNameWithoutExtension(path)), path))
            .Where(x => !string.IsNullOrWhiteSpace(x.Label))
            .GroupBy(x => x.Label, StringComparer.OrdinalIgnoreCase)
            .Select(g => g.OrderBy(x => x.Path.Length).First())
            .OrderBy(x => x.Label, StringComparer.CurrentCultureIgnoreCase)
            .Take(200)
            .ToArray();
    }

    public static bool TrySetSlotFromCatalog(int slot, string? appId, out string message, out AppShortcutSlot? shortcut)
    {
        shortcut = null;
        if (slot is < 1 or > SlotCount)
        {
            message = "Invalid app slot";
            return false;
        }

        var catalogItem = GetCatalog().FirstOrDefault(x => x.Id.Equals(appId ?? "", StringComparison.Ordinal));
        if (catalogItem == null)
        {
            message = "App not found";
            return false;
        }

        lock (Gate)
        {
            var slots = LoadSlots();
            slots[slot - 1] = new AppShortcutSlot(slot, catalogItem.Label, catalogItem.Path);
            SaveSlots(slots);
            shortcut = slots[slot - 1];
        }

        message = "OK";
        return true;
    }

    public static void ShowConfigureDialog(IWin32Window owner)
    {
        using var dialog = new Form
        {
            Text = "App Shortcuts",
            StartPosition = FormStartPosition.CenterParent,
            FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false,
            MinimizeBox = false,
            ClientSize = new Size(640, 302),
            BackColor = Color.FromArgb(15, 18, 22),
            ForeColor = Color.FromArgb(244, 246, 248),
            Font = new Font("Segoe UI", 9F, FontStyle.Regular, GraphicsUnit.Point)
        };

        var rows = new List<(TextBox label, TextBox path, Button browse, Button clear)>();
        var slots = GetSlots().ToDictionary(x => x.Slot);

        for (var i = 1; i <= SlotCount; i++)
        {
            var y = 18 + (i - 1) * 42;
            var slot = slots[i];

            var slotLabel = new Label
            {
                AutoSize = false,
                Location = new Point(16, y + 7),
                Size = new Size(42, 22),
                Text = $"{i}",
                ForeColor = Color.FromArgb(174, 183, 194)
            };

            var labelBox = new TextBox
            {
                Location = new Point(56, y),
                Size = new Size(110, 26),
                Text = slot.Label,
                BackColor = Color.FromArgb(32, 38, 45),
                ForeColor = Color.FromArgb(244, 246, 248),
                BorderStyle = BorderStyle.FixedSingle
            };

            var pathBox = new TextBox
            {
                Location = new Point(176, y),
                Size = new Size(302, 26),
                Text = slot.Path,
                BackColor = Color.FromArgb(32, 38, 45),
                ForeColor = Color.FromArgb(244, 246, 248),
                BorderStyle = BorderStyle.FixedSingle
            };

            var browse = Button("Browse", 488, y, 68);
            var clear = Button("Clear", 564, y, 58);

            var rowIndex = i;
            browse.Click += (_, _) =>
            {
                using var picker = new OpenFileDialog
                {
                    Title = $"Choose app for slot {rowIndex}",
                    Filter = "Apps and shortcuts|*.exe;*.lnk;*.bat;*.cmd|All files|*.*",
                    CheckFileExists = true,
                    Multiselect = false
                };

                if (picker.ShowDialog(dialog) != DialogResult.OK) return;

                pathBox.Text = picker.FileName;
                if (string.IsNullOrWhiteSpace(labelBox.Text))
                    labelBox.Text = Path.GetFileNameWithoutExtension(picker.FileName);
            };

            clear.Click += (_, _) =>
            {
                labelBox.Text = $"App {rowIndex}";
                pathBox.Text = "";
            };

            dialog.Controls.Add(slotLabel);
            dialog.Controls.Add(labelBox);
            dialog.Controls.Add(pathBox);
            dialog.Controls.Add(browse);
            dialog.Controls.Add(clear);
            rows.Add((labelBox, pathBox, browse, clear));
        }

        var hint = new Label
        {
            AutoSize = false,
            Location = new Point(16, 232),
            Size = new Size(450, 22),
            Text = "These five local PC slots are shown in the Android app.",
            ForeColor = Color.FromArgb(174, 183, 194)
        };

        var cancel = Button("Cancel", 438, 260, 88);
        var save = Button("Save", 534, 260, 88);
        save.BackColor = Color.FromArgb(36, 60, 55);

        cancel.Click += (_, _) => dialog.Close();
        save.Click += (_, _) =>
        {
            var updated = rows.Select((row, index) =>
            {
                var slot = index + 1;
                var label = row.label.Text.Trim();
                var path = row.path.Text.Trim();
                if (label.Length == 0) label = $"App {slot}";
                return new AppShortcutSlot(slot, label, path);
            }).ToArray();

            SaveSlots(updated);
            dialog.DialogResult = DialogResult.OK;
            dialog.Close();
        };

        dialog.Controls.Add(hint);
        dialog.Controls.Add(cancel);
        dialog.Controls.Add(save);
        dialog.AcceptButton = save;
        dialog.CancelButton = cancel;

        dialog.ShowDialog(owner);
    }

    private static Button Button(string text, int x, int y, int width)
    {
        return new Button
        {
            Location = new Point(x, y),
            Size = new Size(width, 28),
            Text = text,
            UseVisualStyleBackColor = false,
            FlatStyle = FlatStyle.Flat,
            BackColor = Color.FromArgb(32, 38, 45),
            ForeColor = Color.FromArgb(244, 246, 248),
            Cursor = Cursors.Hand
        };
    }

    private static AppShortcutSlot[] LoadSlots()
    {
        try
        {
            if (File.Exists(ConfigPath))
            {
                var json = File.ReadAllText(ConfigPath);
                var slots = JsonSerializer.Deserialize<AppShortcutSlot[]>(json);
                if (slots != null) return NormalizeSlots(slots);
            }
        }
        catch
        {
            // Fall back to empty slots; bad config should not break the host.
        }

        return NormalizeSlots(Array.Empty<AppShortcutSlot>());
    }

    private static IEnumerable<string> EnumerateShortcutFiles(string dir)
    {
        try
        {
            return Directory.EnumerateFiles(dir, "*.lnk", SearchOption.AllDirectories).ToArray();
        }
        catch
        {
            return Array.Empty<string>();
        }
    }

    private static string CleanLabel(string label)
    {
        return label
            .Replace(".lnk", "", StringComparison.OrdinalIgnoreCase)
            .Replace(" - Shortcut", "", StringComparison.OrdinalIgnoreCase)
            .Trim();
    }

    private static string MakeCatalogId(string path)
    {
        var bytes = SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(path.ToLowerInvariant()));
        return Convert.ToBase64String(bytes)
            .TrimEnd('=')
            .Replace('+', '-')
            .Replace('/', '_');
    }

    private static void SaveSlots(IEnumerable<AppShortcutSlot> slots)
    {
        Directory.CreateDirectory(ConfigDir);
        var normalized = NormalizeSlots(slots);
        var json = JsonSerializer.Serialize(normalized, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(ConfigPath, json);
    }

    private static AppShortcutSlot[] NormalizeSlots(IEnumerable<AppShortcutSlot> slots)
    {
        var bySlot = slots
            .Where(x => x.Slot is >= 1 and <= SlotCount)
            .GroupBy(x => x.Slot)
            .ToDictionary(g => g.Key, g => g.First());

        return Enumerable.Range(1, SlotCount)
            .Select(i =>
            {
                if (!bySlot.TryGetValue(i, out var slot))
                    return new AppShortcutSlot(i, $"App {i}", "");

                var label = string.IsNullOrWhiteSpace(slot.Label) ? $"App {i}" : slot.Label.Trim();
                var path = slot.Path?.Trim() ?? "";
                var args = slot.Arguments?.Trim() ?? "";
                return new AppShortcutSlot(i, label, path, args);
            })
            .ToArray();
    }
}
