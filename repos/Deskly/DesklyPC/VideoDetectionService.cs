namespace DesklyPC;

public static class VideoDetectionService
{
    public sealed record VideoItem(
        string Id,
        string Title,
        string Source,
        string PlaybackState,
        bool Controllable);

    private static readonly HashSet<string> MediaProcesses = new(StringComparer.OrdinalIgnoreCase)
    {
        "chrome",
        "msedge",
        "firefox",
        "opera",
        "opera_gx",
        "brave",
        "vlc",
        "spotify",
        "wmplayer",
        "mpv",
        "potplayermini64",
        "netflix",
        "disneyplus",
        "applemusic"
    };

    public static IReadOnlyList<VideoItem> Detect()
    {
        return WindowSwitcherService.GetOpenWindows()
            .Where(IsLikelyMediaWindow)
            .Select(x => new VideoItem(
                Id: x.Id,
                Title: CleanTitle(x.Title),
                Source: CleanSource(x.AppName),
                PlaybackState: "unknown",
                Controllable: true))
            .Where(x => !string.IsNullOrWhiteSpace(x.Title))
            .Take(50)
            .ToArray();
    }

    private static bool IsLikelyMediaWindow(WindowSwitcherService.WindowItem item)
    {
        if (MediaProcesses.Contains(item.AppName))
            return true;

        var title = item.Title;
        return title.Contains("YouTube", StringComparison.OrdinalIgnoreCase)
            || title.Contains("Netflix", StringComparison.OrdinalIgnoreCase)
            || title.Contains("Disney+", StringComparison.OrdinalIgnoreCase)
            || title.Contains("Apple TV", StringComparison.OrdinalIgnoreCase)
            || title.Contains("VLC", StringComparison.OrdinalIgnoreCase)
            || title.Contains("Spotify", StringComparison.OrdinalIgnoreCase);
    }

    private static string CleanTitle(string title)
    {
        var cleaned = title.Trim();
        foreach (var suffix in new[] { " - Google Chrome", " - Microsoft Edge", " - Mozilla Firefox", " - Opera" })
        {
            if (cleaned.EndsWith(suffix, StringComparison.OrdinalIgnoreCase))
                cleaned = cleaned[..^suffix.Length].Trim();
        }

        return cleaned.Length <= 160 ? cleaned : cleaned[..160];
    }

    private static string CleanSource(string appName)
    {
        if (string.IsNullOrWhiteSpace(appName)) return "Windows";
        return appName.Trim() switch
        {
            var s when s.Equals("chrome", StringComparison.OrdinalIgnoreCase) => "Chrome",
            var s when s.Equals("msedge", StringComparison.OrdinalIgnoreCase) => "Edge",
            var s when s.Equals("firefox", StringComparison.OrdinalIgnoreCase) => "Firefox",
            var s when s.Equals("opera", StringComparison.OrdinalIgnoreCase) => "Opera",
            var s when s.Equals("vlc", StringComparison.OrdinalIgnoreCase) => "VLC",
            var s when s.Equals("spotify", StringComparison.OrdinalIgnoreCase) => "Spotify",
            var s => s
        };
    }
}
