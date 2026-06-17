namespace DesklyPC;

public enum MediaActionKind
{
    Key,
    Shortcut
}

public sealed record MediaActionMapping(MediaActionKind Kind, string[] Keys)
{
    public static MediaActionMapping Key(string key) => new(MediaActionKind.Key, new[] { key });
    public static MediaActionMapping Shortcut(params string[] keys) => new(MediaActionKind.Shortcut, keys);
}

public static class MediaActionMapper
{
    public static MediaActionMapping? Resolve(string? action, ShortcutPlatform platform = ShortcutPlatform.Windows)
    {
        var modifier = platform == ShortcutPlatform.MacOS ? "cmd" : "ctrl";

        return Normalize(action) switch
        {
            "play_pause" => MediaActionMapping.Key("media_play_pause"),
            "previous" => MediaActionMapping.Key("media_previous"),
            "next" => MediaActionMapping.Key("media_next"),
            "volume_down" => MediaActionMapping.Key("volume_down"),
            "volume_up" => MediaActionMapping.Key("volume_up"),
            "mute" => MediaActionMapping.Key("volume_mute"),
            "seek_backward" => MediaActionMapping.Key("left"),
            "seek_forward" => MediaActionMapping.Key("right"),
            "fullscreen" => platform == ShortcutPlatform.MacOS
                ? MediaActionMapping.Shortcut(modifier, "ctrl", "f")
                : MediaActionMapping.Key("f11"),
            _ => null
        };
    }

    public static string Normalize(string? action) =>
        (action ?? "").Trim().ToLowerInvariant().Replace("-", "_").Replace(" ", "_") switch
        {
            "play" or "pause" or "media_play_pause" => "play_pause",
            "prev" or "media_previous" or "previous_track" => "previous",
            "media_next" or "next_track" => "next",
            "vol_down" => "volume_down",
            "vol_up" => "volume_up",
            "volume_mute" or "media_mute" => "mute",
            "rewind" or "backward" => "seek_backward",
            "forward" => "seek_forward",
            var normalized => normalized
        };
}
