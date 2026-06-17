namespace DesklyPC;

public enum ShortcutActionKind
{
    KeyboardShortcut,
    CtrlWheelZoom
}

public enum ShortcutPlatform
{
    Windows,
    MacOS
}

public sealed record ShortcutActionMapping(
    ShortcutActionKind Kind,
    string[] Keys,
    int WheelSteps)
{
    public static ShortcutActionMapping Keyboard(params string[] keys) =>
        new(ShortcutActionKind.KeyboardShortcut, keys, 0);

    public static ShortcutActionMapping Zoom(int wheelSteps) =>
        new(ShortcutActionKind.CtrlWheelZoom, Array.Empty<string>(), wheelSteps);
}

public static class ShortcutActionMapper
{
    public static ShortcutActionMapping? Resolve(string? action, ShortcutPlatform platform = ShortcutPlatform.Windows)
    {
        var modifier = platform == ShortcutPlatform.MacOS ? "cmd" : "ctrl";

        return Normalize(action) switch
        {
            "show_desktop" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "f3")
                : ShortcutActionMapping.Keyboard("win", "d"),
            "task_manager" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "alt", "esc")
                : ShortcutActionMapping.Keyboard("ctrl", "shift", "esc"),
            "close_window" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "w")
                : ShortcutActionMapping.Keyboard("alt", "f4"),
            "browser_back" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "left_bracket")
                : ShortcutActionMapping.Keyboard("alt", "left"),
            "browser_forward" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "right_bracket")
                : ShortcutActionMapping.Keyboard("alt", "right"),
            "new_tab" => ShortcutActionMapping.Keyboard(modifier, "t"),
            "close_tab" => ShortcutActionMapping.Keyboard(modifier, "w"),
            "next_tab" => ShortcutActionMapping.Keyboard("ctrl", "tab"),
            "previous_tab" => ShortcutActionMapping.Keyboard("ctrl", "shift", "tab"),
            "refresh" => ShortcutActionMapping.Keyboard(modifier, "r"),
            "address_bar" => ShortcutActionMapping.Keyboard(modifier, "l"),
            "fullscreen" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "ctrl", "f")
                : ShortcutActionMapping.Keyboard("f11"),
            "page_scroll_up" => ShortcutActionMapping.Keyboard("page_up"),
            "page_scroll_down" => ShortcutActionMapping.Keyboard("page_down"),
            "zoom_in" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "plus")
                : ShortcutActionMapping.Zoom(1),
            "zoom_out" => platform == ShortcutPlatform.MacOS
                ? ShortcutActionMapping.Keyboard("cmd", "minus")
                : ShortcutActionMapping.Zoom(-1),
            "zoom_reset" => ShortcutActionMapping.Keyboard(modifier, "0"),
            _ => null
        };
    }

    public static string Normalize(string? action) =>
        (action ?? "").Trim().ToLowerInvariant().Replace("-", "_").Replace(" ", "_");
}
