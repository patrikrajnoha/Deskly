using System.Collections.Concurrent;
using System.Security.Cryptography;
using System.Text.Json;

namespace DesklyPC;

public sealed class PairingManager
{
    private string? _currentPin;
    private DateTime _pinExpiresAt;

    private readonly ConcurrentDictionary<string, DateTime> _validTokens = new();

    private readonly object _fileLock = new();
    private readonly string _filePath;

    // (voliteľné) tokeny staršie ako X dní vyhodíme pri štarte/ukladaní
    private static readonly TimeSpan TokenMaxAge = TimeSpan.FromDays(180);

    public string? CurrentPin => _currentPin;
    public bool HasValidPin => _currentPin != null && DateTime.UtcNow < _pinExpiresAt;
    public int ValidTokensCount => _validTokens.Count;

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

    public (bool ok, string? token, string message) TryPair(string pin)
    {
        if (!HasValidPin)
            return (false, null, "PIN expiroval. Vygeneruj nový.");

        if (!string.Equals(pin, _currentPin, StringComparison.Ordinal))
            return (false, null, "Nesprávny PIN.");

        var token = GenerateToken();

        _validTokens[token] = DateTime.UtcNow;
        SaveTokensToDisk();

        _currentPin = null; // jednorazový PIN
        return (true, token, "Spárovanie úspešné.");
    }

    public bool IsTokenValid(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return false;
        return _validTokens.ContainsKey(token);
    }

    public bool RevokeToken(string? token)
    {
        if (string.IsNullOrWhiteSpace(token)) return false;

        var ok = _validTokens.TryRemove(token, out _);
        if (ok) SaveTokensToDisk();
        return ok;
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

                var dict = JsonSerializer.Deserialize<Dictionary<string, DateTime>>(json);
                if (dict == null) return;

                foreach (var kv in dict)
                {
                    if (!string.IsNullOrWhiteSpace(kv.Key))
                        _validTokens[kv.Key] = kv.Value;
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
            if (kv.Value < cutoff)
                _validTokens.TryRemove(kv.Key, out _);
        }
    }
}
