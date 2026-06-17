using System.Text.Json;

namespace DesklyPC;

public enum DesklyCommandGroup
{
    Pairing,
    Display,
    Audio,
    Night,
    Quiet,
    Power,
    Timer,
    Mouse,
    Keyboard,
    Shortcut,
    Media,
    Video,
    Clipboard,
    App,
    Web,
    Presentation
}

public enum DesklyAuthRequirement
{
    None,
    Token
}

public sealed record DesklyCommandSpec(
    string Type,
    string ResponseType,
    DesklyCommandGroup Group,
    DesklyAuthRequirement Auth = DesklyAuthRequirement.Token,
    bool Legacy = false);

public static class DesklyProtocol
{
    public const int Version = 1;

    public static readonly IReadOnlyList<DesklyCommandSpec> Commands = new[]
    {
        new DesklyCommandSpec("pair_status", "pair_status_response", DesklyCommandGroup.Pairing, DesklyAuthRequirement.None),
        new DesklyCommandSpec("pair_request", "pair_response", DesklyCommandGroup.Pairing, DesklyAuthRequirement.None),
        new DesklyCommandSpec("auth_request", "auth_response", DesklyCommandGroup.Pairing, DesklyAuthRequirement.None),
        new DesklyCommandSpec("ping_secure", "response", DesklyCommandGroup.Pairing),
        new DesklyCommandSpec("display_list", "display_list_response", DesklyCommandGroup.Display),
        new DesklyCommandSpec("display_control", "display_control_response", DesklyCommandGroup.Display),
        new DesklyCommandSpec("display_mode_set", "display_mode_response", DesklyCommandGroup.Display),
        new DesklyCommandSpec("capabilities_external_brightness", "capabilities_external_brightness_response", DesklyCommandGroup.Display),
        new DesklyCommandSpec("brightness_get", "brightness_response", DesklyCommandGroup.Display),
        new DesklyCommandSpec("brightness_set", "brightness_response", DesklyCommandGroup.Display),
        new DesklyCommandSpec("volume_get", "audio_response", DesklyCommandGroup.Audio),
        new DesklyCommandSpec("volume_set", "audio_response", DesklyCommandGroup.Audio),
        new DesklyCommandSpec("mute_toggle", "audio_response", DesklyCommandGroup.Audio),
        new DesklyCommandSpec("night_get", "night_response", DesklyCommandGroup.Night),
        new DesklyCommandSpec("night_set", "night_response", DesklyCommandGroup.Night),
        new DesklyCommandSpec("quiet_get", "quiet_response", DesklyCommandGroup.Quiet),
        new DesklyCommandSpec("quiet_set", "quiet_response", DesklyCommandGroup.Quiet),
        new DesklyCommandSpec("power_plan_get", "power_plan_response", DesklyCommandGroup.Power),
        new DesklyCommandSpec("power_plan_set", "power_plan_response", DesklyCommandGroup.Power),
        new DesklyCommandSpec("power_lock", "power_response", DesklyCommandGroup.Power),
        new DesklyCommandSpec("power_sleep", "power_response", DesklyCommandGroup.Power),
        new DesklyCommandSpec("power_shutdown", "power_response", DesklyCommandGroup.Power),
        new DesklyCommandSpec("power_restart", "power_response", DesklyCommandGroup.Power),
        new DesklyCommandSpec("sleep_timer_set", "sleep_timer_response", DesklyCommandGroup.Timer),
        new DesklyCommandSpec("sleep_timer_cancel", "sleep_timer_response", DesklyCommandGroup.Timer),
        new DesklyCommandSpec("sleep_timer_status", "sleep_timer_status_response", DesklyCommandGroup.Timer),
        new DesklyCommandSpec("mouse_move", "mouse_response", DesklyCommandGroup.Mouse),
        new DesklyCommandSpec("mouse_click", "mouse_response", DesklyCommandGroup.Mouse),
        new DesklyCommandSpec("mouse_scroll", "mouse_response", DesklyCommandGroup.Mouse),
        new DesklyCommandSpec("mouse_button", "mouse_response", DesklyCommandGroup.Mouse),
        new DesklyCommandSpec("keyboard_text", "keyboard_response", DesklyCommandGroup.Keyboard),
        new DesklyCommandSpec("keyboard_key", "keyboard_response", DesklyCommandGroup.Keyboard),
        new DesklyCommandSpec("keyboard_shortcut", "keyboard_response", DesklyCommandGroup.Keyboard),
        new DesklyCommandSpec("shortcut_action", "shortcut_response", DesklyCommandGroup.Shortcut),
        new DesklyCommandSpec("media_action", "media_response", DesklyCommandGroup.Media),
        new DesklyCommandSpec("media_key", "media_response", DesklyCommandGroup.Media, Legacy: true),
        new DesklyCommandSpec("video_list", "video_list_response", DesklyCommandGroup.Video),
        new DesklyCommandSpec("clipboard_set", "clipboard_response", DesklyCommandGroup.Clipboard),
        new DesklyCommandSpec("app_shortcuts_get", "app_response", DesklyCommandGroup.App),
        new DesklyCommandSpec("app_catalog_get", "app_response", DesklyCommandGroup.App),
        new DesklyCommandSpec("app_shortcut_set", "app_response", DesklyCommandGroup.App),
        new DesklyCommandSpec("app_open", "app_response", DesklyCommandGroup.App),
        new DesklyCommandSpec("app_windows_get", "app_response", DesklyCommandGroup.App),
        new DesklyCommandSpec("app_switch", "app_response", DesklyCommandGroup.App),
        new DesklyCommandSpec("web_open", "web_response", DesklyCommandGroup.Web),
        new DesklyCommandSpec("presentation_action", "presentation_response", DesklyCommandGroup.Presentation)
    };

    private static readonly Dictionary<string, DesklyCommandSpec> CommandsByType =
        Commands.ToDictionary(x => x.Type, StringComparer.Ordinal);

    public static bool IsSupportedVersion(JsonElement root, out int? version)
    {
        version = null;
        if (!root.TryGetProperty("protocolVersion", out var versionEl))
            return true;

        if (versionEl.ValueKind != JsonValueKind.Number || !versionEl.TryGetInt32(out var value))
            return false;

        version = value;
        return value == Version;
    }

    public static bool HasObjectPayloadIfPresent(JsonElement root)
    {
        return !root.TryGetProperty("payload", out var payload) ||
               payload.ValueKind == JsonValueKind.Object;
    }

    public static DesklyCommandSpec? GetCommand(string? type)
    {
        var normalized = NormalizeType(type);
        return CommandsByType.TryGetValue(normalized, out var spec) ? spec : null;
    }

    public static bool IsKnownRequestType(string? type) => GetCommand(type) != null;

    public static string ResponseTypeFor(string? type) => GetCommand(type)?.ResponseType ?? "response";

    public static bool RequiresToken(string? type) => GetCommand(type)?.Auth == DesklyAuthRequirement.Token;

    public static bool IsPrivileged(string? type)
    {
        var spec = GetCommand(type);
        if (spec is null || spec.Auth != DesklyAuthRequirement.Token)
            return false;

        return spec.Group is DesklyCommandGroup.Power
            or DesklyCommandGroup.Clipboard
            or DesklyCommandGroup.App
            or DesklyCommandGroup.Web
            or DesklyCommandGroup.Media
            or DesklyCommandGroup.Video
            or DesklyCommandGroup.Keyboard
            or DesklyCommandGroup.Mouse
            or DesklyCommandGroup.Shortcut
            or DesklyCommandGroup.Presentation;
    }

    public static string NormalizeType(string? type) => (type ?? "").Trim().ToLowerInvariant();
}
