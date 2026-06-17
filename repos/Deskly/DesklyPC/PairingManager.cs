using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text.Json;

namespace DesklyPC;

public sealed class PairingManager
{
    private sealed class TokenRecord
    {
        public DateTime CreatedAt { get; set; }
        public DateTime LastSeenAt { get; set; }
        public string? DeviceName { get; set; }
    }

    private string? _currentPin;
    private DateTime _pinExpiresAt;

    private readonly ConcurrentDictionary<string, TokenRecord> _validTokens = new();

    private readonly object _fileLock = new();
    private readonly string _filePath;

    // (voliteľné) tokeny staršie ako X dní vyhodíme pri štarte/ukladaní
    private static readonly TimeSpan TokenMaxAge = TimeSpan.FromDays(180);

    public string? CurrentPin => _currentPin;
    public bool HasValidPin => _currentPin != null && DateTime.UtcNow < _pinExpiresAt;
    public int ValidTokensCount => _validTokens.Count;
    public string? LastAuthorizedDeviceName =>
        _validTokens.Values
            .OrderByDescending(v => v.LastSeenAt == default ? v.CreatedAt : v.LastSeenAt)
            .FirstOrDefault(v => !string.IsNullOrWhiteSpace(v.DeviceName))
            ?.DeviceName;

    public PairingManager()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "DesklyPC"
        );
        Directory.CreateDirectory(dir);
        _filePath = Path.Combine(dir, "tokens.json");

        LoadTokensFromDisk();
        PruneOldTokens();
    }

    public string GeneratePin(TimeSpan validity)
    {
        var pin = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
        _currentPin = pin;
        _pinExpiresAt = DateTime.UtcNow.Add(validity);
        return pin;
    }

    public (bool ok, string? token, string message) TryPair(string pin, string? deviceName = null)
    {
        if (!HasValidPin)
            return (false, null, "PIN expiroval. Vygeneruj nový.");

        if (!string.Equals(pin, _currentPin, StringComparison.Ordinal))
            return (false, null, "Nesprávny PIN.");

        var token = GenerateToken();

        var now = DateTime.UtcNow;
        _validTokens[token] = new TokenRecord
        {
            CreatedAt = now,
            LastSeenAt = now,
            DeviceName = CleanDeviceName(deviceName)
        };
        SaveTokensToDisk();

        _currentPin = null; // jednorazový PIN
        return (true, token, "Spárovanie úspešné.");
    }

    public bool IsTokenValid(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return false;
        return _validTokens.ContainsKey(token);
    }

    public string? GetDeviceName(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return null;
        return _validTokens.TryGetValue(token, out var record) ? record.DeviceName : null;
    }

    public void MarkSeen(string? token, string? deviceName = null)
    {
        if (string.IsNullOrWhiteSpace(token)) return;
        if (!_validTokens.TryGetValue(token, out var record)) return;

        var cleaned = CleanDeviceName(deviceName);
        record.LastSeenAt = DateTime.UtcNow;
        if (!string.IsNullOrWhiteSpace(cleaned))
            record.DeviceName = cleaned;

        SaveTokensToDisk();
    }

    public bool RevokeToken(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return false;

        var ok = _validTokens.TryRemove(token, out _);
        if (ok) SaveTokensToDisk();
        return ok;
    }

    public int RevokeAllTokens()
    {
        var count = _validTokens.Count;
        _validTokens.Clear();
        _currentPin = null;
        SaveTokensToDisk();
        return count;
    }

    public int PinSecondsRemaining()
    {
        if (!HasValidPin) return 0;
        var sec = (int)Math.Ceiling((_pinExpiresAt - DateTime.UtcNow).TotalSeconds);
        return Math.Max(0, sec);
    }

    private static string GenerateToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(32);
        return Convert.ToBase64String(bytes)
            .Replace("+", "-")
            .Replace("/", "_")
            .TrimEnd('=');
    }

    private void LoadTokensFromDisk()
    {
        try
        {
            lock (_fileLock)
            {
                if (!File.Exists(_filePath)) return;

                var json = File.ReadAllText(_filePath);
                if (string.IsNullOrWhiteSpace(json)) return;

                Dictionary<string, TokenRecord>? records = null;
                try
                {
                    records = JsonSerializer.Deserialize<Dictionary<string, TokenRecord>>(json);
                }
                catch
                {
                    records = null;
                }

                if (records != null && records.Count > 0)
                {
                    foreach (var kv in records)
                    {
                        if (string.IsNullOrWhiteSpace(kv.Key) || kv.Value == null) continue;

                        var createdAt = kv.Value.CreatedAt == default ? DateTime.UtcNow : kv.Value.CreatedAt;
                        var lastSeenAt = kv.Value.LastSeenAt == default ? createdAt : kv.Value.LastSeenAt;
                        _validTokens[kv.Key] = new TokenRecord
                        {
                            CreatedAt = createdAt,
                            LastSeenAt = lastSeenAt,
                            DeviceName = CleanDeviceName(kv.Value.DeviceName)
                        };
                    }

                    return;
                }

                Dictionary<string, DateTime>? legacy = null;
                try
                {
                    legacy = JsonSerializer.Deserialize<Dictionary<string, DateTime>>(json);
                }
                catch
                {
                    legacy = null;
                }

                if (legacy == null) return;

                foreach (var kv in legacy)
                {
                    if (!string.IsNullOrWhiteSpace(kv.Key))
                    {
                        _validTokens[kv.Key] = new TokenRecord
                        {
                            CreatedAt = kv.Value,
                            LastSeenAt = kv.Value
                        };
                    }
                }
            }
        }
        catch
        {
            // Ak sa súbor poškodí, radšej ho ignorujeme (user spáruje znovu)
        }
    }

    private void SaveTokensToDisk()
    {
        try
        {
            lock (_fileLock)
            {
                PruneOldTokens_NoLock();

                var snapshot = _validTokens.ToDictionary(k => k.Key, v => v.Value);
                var json = JsonSerializer.Serialize(snapshot, new JsonSerializerOptions { WriteIndented = true });

                // ✅ atomic write: najprv tmp, potom replace
                var tmp = _filePath + ".tmp";
                File.WriteAllText(tmp, json);

                // overwrite-safe replace
                if (File.Exists(_filePath))
                    File.Delete(_filePath);

                File.Move(tmp, _filePath);
            }
        }
        catch
        {
            // ignore
        }
    }

    private void PruneOldTokens()
    {
        lock (_fileLock)
        {
            PruneOldTokens_NoLock();
        }
    }

    private void PruneOldTokens_NoLock()
    {
        var cutoff = DateTime.UtcNow - TokenMaxAge;
        foreach (var kv in _validTokens.ToArray())
        {
            var lastSeen = kv.Value.LastSeenAt == default ? kv.Value.CreatedAt : kv.Value.LastSeenAt;
            if (lastSeen < cutoff)
                _validTokens.TryRemove(kv.Key, out _);
        }
    }

    private static string? CleanDeviceName(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;

        var cleaned = value.Trim();
        foreach (var ch in new[] { '\r', '\n', '\t' })
            cleaned = cleaned.Replace(ch, ' ');

        while (cleaned.Contains("  ", StringComparison.Ordinal))
            cleaned = cleaned.Replace("  ", " ", StringComparison.Ordinal);

        return cleaned.Length <= 80 ? cleaned : cleaned[..80];
    }
}
