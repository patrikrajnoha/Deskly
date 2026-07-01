using System.Text.Json;

namespace DesklyPC.Tests;

public class ProtocolAndSecurityTests
{
    [Fact]
    public void MediaMappingsCoverRequiredActions()
    {
        var actions = new[]
        {
            "play_pause",
            "previous",
            "next",
            "volume_down",
            "volume_up",
            "mute",
            "seek_backward",
            "seek_forward",
            "fullscreen"
        };

        foreach (var action in actions)
        {
            Assert.NotNull(MediaActionMapper.Resolve(action));
        }
    }

    [Fact]
    public void BrowserMappingsCoverChromeFirefoxOperaCompatibleActions()
    {
        var actions = new[]
        {
            "browser_back",
            "browser_forward",
            "refresh",
            "new_tab",
            "close_tab",
            "next_tab",
            "previous_tab",
            "page_scroll_up",
            "page_scroll_down",
            "fullscreen"
        };

        foreach (var action in actions)
        {
            Assert.NotNull(ShortcutActionMapper.Resolve(action));
        }
    }

    [Fact]
    public void PrivilegedCommandsRequireToken()
    {
        var privileged = new[]
        {
            "clipboard_set",
            "power_shutdown",
            "app_open",
            "app_windows_get",
            "app_switch",
            "web_open",
            "video_list",
            "media_action"
        };

        foreach (var type in privileged)
        {
            Assert.True(DesklyProtocol.RequiresToken(type), type);
            Assert.True(DesklyProtocol.IsPrivileged(type), type);
        }

        Assert.False(DesklyProtocol.RequiresToken("pair_request"));
        Assert.False(DesklyProtocol.IsPrivileged("pair_request"));
    }

    [Fact]
    public void PowerGuardRejectsStaleTimestampedCommands()
    {
        var guard = new PowerCommandGuard(() => 100_000);
        using var doc = JsonDocument.Parse("""{"issuedAtUtcMs": 1}""");

        Assert.False(guard.IsFreshPowerRequest(doc.RootElement, out var message));
        Assert.Equal("Stale power command rejected", message);
    }

    [Fact]
    public void PowerGuardAllowsLegacyUntimestampedCommands()
    {
        var guard = new PowerCommandGuard(() => 100_000);
        using var doc = JsonDocument.Parse("""{}""");

        Assert.True(guard.IsFreshPowerRequest(doc.RootElement, out var message));
        Assert.Equal("OK", message);
    }

    [Fact]
    public void PowerGuardRejectsFastRepeatedDangerousCommands()
    {
        long now = 10_000;
        var guard = new PowerCommandGuard(() => now);

        Assert.False(guard.IsRepeatedPowerRequest("power_shutdown", "token", out _));

        now += 500;
        Assert.True(guard.IsRepeatedPowerRequest("power_shutdown", "token", out var repeatMessage));
        Assert.Equal("Repeated power command rejected", repeatMessage);

        now += PowerCommandGuard.RepeatWindowMs;
        Assert.False(guard.IsRepeatedPowerRequest("power_shutdown", "token", out _));
    }

    [Fact]
    public void WebValidationAllowsOnlyHttpAndHttps()
    {
        Assert.True(WebOpenService.TryNormalizeHttpUrl("https://example.com/path", out var normalized, out var host, out _));
        Assert.Equal("https://example.com/path", normalized.TrimEnd('/'));
        Assert.Equal("example.com", host);

        Assert.False(WebOpenService.TryNormalizeHttpUrl("file:///C:/secret.txt", out _, out _, out var fileMessage));
        Assert.Equal("Unsupported URL scheme", fileMessage);

        Assert.False(WebOpenService.TryNormalizeHttpUrl("javascript:alert(1)", out _, out _, out _));
    }

    [Fact]
    public void VideoListCommandIsPrivilegedAndStable()
    {
        Assert.Equal("video_list_response", DesklyProtocol.ResponseTypeFor("video_list"));
        Assert.True(DesklyProtocol.RequiresToken("video_list"));
        Assert.True(DesklyProtocol.IsPrivileged("video_list"));
    }

    [Fact]
    public void BluetoothServiceUuidIsStable()
    {
        Assert.Equal(Guid.Parse("6f5f7a04-2b5a-41d4-9f5f-3e8e0fd8c901"), DesklyBluetooth.ServiceUuid);
        Assert.Equal("Deskly", DesklyBluetooth.ServiceName);
    }
}
