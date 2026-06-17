using System.Runtime.InteropServices;

namespace DesklyPC;

public static class InputService
{
    private const uint INPUT_MOUSE = 0;
    private const uint INPUT_KEYBOARD = 1;

    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x01000;

    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_UNICODE = 0x0004;

    private const int WHEEL_DELTA = 120;
    private const int MAX_TEXT_CHARS = 2000;

    private const ushort VK_BACK = 0x08;
    private const ushort VK_TAB = 0x09;
    private const ushort VK_RETURN = 0x0D;
    private const ushort VK_ESCAPE = 0x1B;
    private const ushort VK_SPACE = 0x20;
    private const ushort VK_PRIOR = 0x21;
    private const ushort VK_NEXT = 0x22;
    private const ushort VK_END = 0x23;
    private const ushort VK_HOME = 0x24;
    private const ushort VK_LEFT = 0x25;
    private const ushort VK_UP = 0x26;
    private const ushort VK_RIGHT = 0x27;
    private const ushort VK_DOWN = 0x28;
    private const ushort VK_DELETE = 0x2E;
    private const ushort VK_CONTROL = 0x11;
    private const ushort VK_MENU = 0x12;
    private const ushort VK_SHIFT = 0x10;
    private const ushort VK_LWIN = 0x5B;
    private const ushort VK_ADD = 0x6B;
    private const ushort VK_SUBTRACT = 0x6D;
    private const ushort VK_OEM_PLUS = 0xBB;
    private const ushort VK_OEM_MINUS = 0xBD;
    private const ushort VK_OEM_4 = 0xDB;
    private const ushort VK_OEM_6 = 0xDD;
    private const ushort VK_F1 = 0x70;
    private const ushort VK_F2 = 0x71;
    private const ushort VK_F3 = 0x72;
    private const ushort VK_F4 = 0x73;
    private const ushort VK_F5 = 0x74;
    private const ushort VK_F6 = 0x75;
    private const ushort VK_F7 = 0x76;
    private const ushort VK_F8 = 0x77;
    private const ushort VK_F9 = 0x78;
    private const ushort VK_F10 = 0x79;
    private const ushort VK_F11 = 0x7A;
    private const ushort VK_F12 = 0x7B;
    private const ushort VK_VOLUME_MUTE = 0xAD;
    private const ushort VK_VOLUME_DOWN = 0xAE;
    private const ushort VK_VOLUME_UP = 0xAF;
    private const ushort VK_MEDIA_NEXT_TRACK = 0xB0;
    private const ushort VK_MEDIA_PREV_TRACK = 0xB1;
    private const ushort VK_MEDIA_STOP = 0xB2;
    private const ushort VK_MEDIA_PLAY_PAUSE = 0xB3;

    public static bool TryMoveRelative(int dx, int dy, out string message)
    {
        dx = Math.Clamp(dx, -5000, 5000);
        dy = Math.Clamp(dy, -5000, 5000);

        if (dx == 0 && dy == 0)
        {
            message = "No movement";
            return true;
        }

        return TrySendMouse(MOUSEEVENTF_MOVE, dx, dy, 0, out message);
    }

    public static bool TryClick(string button, int clicks, out string message)
    {
        clicks = Math.Clamp(clicks, 1, 3);

        var (down, up, normalized) = NormalizeButton(button);
        if (down == 0 || up == 0)
        {
            message = "Unsupported mouse button";
            return false;
        }

        for (var i = 0; i < clicks; i++)
        {
            if (!TrySendInputBatch(new[]
                {
                    MouseInput(down, 0, 0, 0),
                    MouseInput(up, 0, 0, 0)
                }, out message))
                return false;
        }

        message = $"{normalized} click";
        return true;
    }

    public static bool TrySetButton(string button, bool down, out string message)
    {
        var (downFlag, upFlag, normalized) = NormalizeButton(button);
        if (downFlag == 0 || upFlag == 0)
        {
            message = "Unsupported mouse button";
            return false;
        }

        var flag = down ? downFlag : upFlag;
        if (!TrySendMouse(flag, 0, 0, 0, out message))
            return false;

        message = $"{normalized} {(down ? "down" : "up")}";
        return true;
    }

    public static bool TryScroll(int deltaX, int deltaY, out string message)
    {
        deltaX = Math.Clamp(deltaX, -10, 10);
        deltaY = Math.Clamp(deltaY, -10, 10);

        if (deltaX == 0 && deltaY == 0)
        {
            message = "No scroll";
            return true;
        }

        if (deltaY != 0 && !TrySendMouse(MOUSEEVENTF_WHEEL, 0, 0, deltaY * WHEEL_DELTA, out message))
            return false;

        if (deltaX != 0 && !TrySendMouse(MOUSEEVENTF_HWHEEL, 0, 0, deltaX * WHEEL_DELTA, out message))
            return false;

        message = "Scrolled";
        return true;
    }

    public static bool TryCtrlWheelZoom(int wheelSteps, out string message)
    {
        wheelSteps = Math.Clamp(wheelSteps, -10, 10);
        if (wheelSteps == 0)
        {
            message = "No zoom";
            return true;
        }

        var inputs = new[]
        {
            KeyInput(VK_CONTROL, keyUp: false),
            MouseInput(MOUSEEVENTF_WHEEL, 0, 0, wheelSteps * WHEEL_DELTA),
            KeyInput(VK_CONTROL, keyUp: true)
        };

        if (!TrySendInputBatch(inputs, out message))
            return false;

        message = wheelSteps > 0 ? "Zoomed in" : "Zoomed out";
        return true;
    }

    public static bool TrySendText(string? text, out string message)
    {
        if (string.IsNullOrEmpty(text))
        {
            message = "No text";
            return false;
        }

        if (text.Length > MAX_TEXT_CHARS)
            text = text[..MAX_TEXT_CHARS];

        var inputs = new List<INPUT>(text.Length * 2);
        foreach (var ch in text)
        {
            inputs.Add(UnicodeInput(ch, keyUp: false));
            inputs.Add(UnicodeInput(ch, keyUp: true));
        }

        if (!TrySendInputBatch(inputs.ToArray(), out message))
            return false;

        message = "Text sent";
        return true;
    }

    public static bool TrySendKey(string? key, int presses, out string message)
    {
        presses = Math.Clamp(presses, 1, 10);
        if (!TryNormalizeKey(key, out var vk, out var normalized))
        {
            message = "Unsupported key";
            return false;
        }

        var inputs = new List<INPUT>(presses * 2);
        for (var i = 0; i < presses; i++)
        {
            inputs.Add(KeyInput(vk, keyUp: false));
            inputs.Add(KeyInput(vk, keyUp: true));
        }

        if (!TrySendInputBatch(inputs.ToArray(), out message))
            return false;

        message = normalized;
        return true;
    }

    public static bool TrySendShortcut(IEnumerable<string>? keys, out string message)
    {
        var normalized = (keys ?? Array.Empty<string>())
            .Select(k => k?.Trim())
            .Where(k => !string.IsNullOrWhiteSpace(k))
            .Select(k => k!)
            .Take(4)
            .ToArray();

        if (normalized.Length == 0)
        {
            message = "Missing keys";
            return false;
        }

        var vks = new List<ushort>();
        foreach (var key in normalized)
        {
            if (!TryNormalizeKey(key, out var vk, out _))
            {
                message = $"Unsupported key: {key}";
                return false;
            }
            vks.Add(vk);
        }

        var inputs = new List<INPUT>(vks.Count * 2);
        foreach (var vk in vks)
            inputs.Add(KeyInput(vk, keyUp: false));
        for (var i = vks.Count - 1; i >= 0; i--)
            inputs.Add(KeyInput(vks[i], keyUp: true));

        if (!TrySendInputBatch(inputs.ToArray(), out message))
            return false;

        message = string.Join("+", normalized);
        return true;
    }

    private static (uint down, uint up, string normalized) NormalizeButton(string? button)
    {
        return (button ?? "").Trim().ToLowerInvariant() switch
        {
            "left" => (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, "left"),
            "right" => (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, "right"),
            "middle" => (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP, "middle"),
            _ => (0, 0, "")
        };
    }

    private static bool TrySendMouse(uint flags, int dx, int dy, int mouseData, out string message)
    {
        return TrySendInputBatch(new[] { MouseInput(flags, dx, dy, mouseData) }, out message);
    }

    private static INPUT MouseInput(uint flags, int dx, int dy, int mouseData)
    {
        return new INPUT
        {
            type = INPUT_MOUSE,
            u = new InputUnion
            {
                mi = new MOUSEINPUT
                {
                    dx = dx,
                    dy = dy,
                    mouseData = mouseData,
                    dwFlags = flags,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            }
        };
    }

    private static INPUT KeyInput(ushort vk, bool keyUp)
    {
        return new INPUT
        {
            type = INPUT_KEYBOARD,
            u = new InputUnion
            {
                ki = new KEYBDINPUT
                {
                    wVk = vk,
                    wScan = 0,
                    dwFlags = keyUp ? KEYEVENTF_KEYUP : 0,
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            }
        };
    }

    private static INPUT UnicodeInput(char ch, bool keyUp)
    {
        return new INPUT
        {
            type = INPUT_KEYBOARD,
            u = new InputUnion
            {
                ki = new KEYBDINPUT
                {
                    wVk = 0,
                    wScan = ch,
                    dwFlags = KEYEVENTF_UNICODE | (keyUp ? KEYEVENTF_KEYUP : 0),
                    time = 0,
                    dwExtraInfo = IntPtr.Zero
                }
            }
        };
    }

    private static bool TrySendInputBatch(INPUT[] inputs, out string message)
    {
        if (inputs.Length == 0)
        {
            message = "No input";
            return false;
        }

        var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
        if (sent == (uint)inputs.Length)
        {
            message = "OK";
            return true;
        }

        var error = Marshal.GetLastWin32Error();
        message = error == 0
            ? $"SendInput failed ({sent}/{inputs.Length})"
            : $"SendInput failed ({error}, {sent}/{inputs.Length})";
        return false;
    }

    private static bool TryNormalizeKey(string? key, out ushort vk, out string normalized)
    {
        normalized = (key ?? "").Trim().ToLowerInvariant()
            .Replace("-", "_")
            .Replace(" ", "_");

        if (normalized.Length == 1)
        {
            var c = normalized[0];
            if (c is >= 'a' and <= 'z')
            {
                vk = (ushort)char.ToUpperInvariant(c);
                return true;
            }
            if (c is >= '0' and <= '9')
            {
                vk = c;
                return true;
            }
        }

        vk = normalized switch
        {
            "enter" or "return" => VK_RETURN,
            "esc" or "escape" => VK_ESCAPE,
            "backspace" or "back" => VK_BACK,
            "tab" => VK_TAB,
            "space" => VK_SPACE,
            "delete" or "del" => VK_DELETE,
            "home" => VK_HOME,
            "end" => VK_END,
            "page_up" or "pgup" => VK_PRIOR,
            "page_down" or "pgdn" => VK_NEXT,
            "left" or "arrow_left" => VK_LEFT,
            "right" or "arrow_right" => VK_RIGHT,
            "up" or "arrow_up" => VK_UP,
            "down" or "arrow_down" => VK_DOWN,
            "ctrl" or "control" => VK_CONTROL,
            "alt" => VK_MENU,
            "shift" => VK_SHIFT,
            "win" or "meta" or "cmd" or "command" => VK_LWIN,
            "plus" or "add" or "numpad_plus" or "numpad_add" => VK_ADD,
            "minus" or "subtract" or "numpad_minus" or "numpad_subtract" => VK_SUBTRACT,
            "equals" or "equal" or "oem_plus" => VK_OEM_PLUS,
            "oem_minus" => VK_OEM_MINUS,
            "left_bracket" or "open_bracket" or "oem_4" => VK_OEM_4,
            "right_bracket" or "close_bracket" or "oem_6" => VK_OEM_6,
            "volume_mute" or "mute" => VK_VOLUME_MUTE,
            "volume_down" or "vol_down" => VK_VOLUME_DOWN,
            "volume_up" or "vol_up" => VK_VOLUME_UP,
            "f1" => VK_F1,
            "f2" => VK_F2,
            "f3" => VK_F3,
            "f4" => VK_F4,
            "f5" => VK_F5,
            "f6" => VK_F6,
            "f7" => VK_F7,
            "f8" => VK_F8,
            "f9" => VK_F9,
            "f10" => VK_F10,
            "f11" => VK_F11,
            "f12" => VK_F12,
            "media_next" or "next_track" or "next" => VK_MEDIA_NEXT_TRACK,
            "media_previous" or "media_prev" or "previous_track" or "prev" or "previous" => VK_MEDIA_PREV_TRACK,
            "media_stop" or "stop" => VK_MEDIA_STOP,
            "media_play_pause" or "play_pause" or "play" or "pause" => VK_MEDIA_PLAY_PAUSE,
            _ => 0
        };

        return vk != 0;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion u;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MOUSEINPUT mi;

        [FieldOffset(0)]
        public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public int mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }
}
