using System.Text.Json;

namespace DesklyPC;

public sealed class PowerCommandGuard
{
    public const long StaleWindowMs = 30_000;
    public const long RepeatWindowMs = 2_000;

    private readonly object _gate = new();
    private readonly Dictionary<string, long> _lastPowerCommandMsByKey = new(StringComparer.Ordinal);
    private readonly Func<long> _nowMs;

    public PowerCommandGuard(Func<long>? nowMs = null)
    {
        _nowMs = nowMs ?? (() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
    }

    public static bool IsDangerousPowerAction(string type)
        => type is "power_sleep" or "power_shutdown" or "power_restart";

    public static bool TryGetLong(JsonElement payload, string prop, out long value)
    {
        value = 0;
        if (payload.ValueKind != JsonValueKind.Object) return false;
        if (!payload.TryGetProperty(prop, out var el)) return false;

        if (el.ValueKind == JsonValueKind.Number && el.TryGetInt64(out value)) return true;
        if (el.ValueKind == JsonValueKind.String && long.TryParse(el.GetString(), out value)) return true;
        return false;
    }

    public bool IsFreshPowerRequest(JsonElement payload, out string message)
    {
        message = "OK";
        if (!TryGetLong(payload, "issuedAtUtcMs", out var issuedAtUtcMs))
            return true;

        if (Math.Abs(_nowMs() - issuedAtUtcMs) <= StaleWindowMs)
            return true;

        message = "Stale power command rejected";
        return false;
    }

    public bool IsRepeatedPowerRequest(string type, string? token, out string message)
    {
        message = "OK";
        var now = _nowMs();
        var key = $"{token ?? ""}:{type}";

        lock (_gate)
        {
            if (_lastPowerCommandMsByKey.TryGetValue(key, out var lastMs) &&
                now - lastMs >= 0 &&
                now - lastMs < RepeatWindowMs)
            {
                message = "Repeated power command rejected";
                return true;
            }

            _lastPowerCommandMsByKey[key] = now;
            return false;
        }
    }
}
