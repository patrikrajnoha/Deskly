using System;

namespace DesklyPC;

public sealed class SleepTimerService
{
    private readonly object _lock = new();
    private System.Threading.Timer? _timer;

    private DateTime? _endUtc;
    private string? _action; // "sleep" | "shutdown"
    private bool _fadeOutVolume;

    public (bool running, int remainingSeconds, string? action) GetStatus()
    {
        lock (_lock)
        {
            if (_timer == null || _endUtc == null)
                return (false, 0, null);

            var rem = (int)Math.Max(0, (_endUtc.Value - DateTime.UtcNow).TotalSeconds);
            return (true, rem, _action);
        }
    }

    public void Cancel()
    {
        lock (_lock)
        {
            _timer?.Dispose();
            _timer = null;
            _endUtc = null;
            _action = null;
            _fadeOutVolume = false;
        }
    }

    public void Set(int seconds, string action) => Set(seconds, action, fadeOutVolume: false);

    public void Set(int seconds, string action, bool fadeOutVolume)
    {
        seconds = Math.Clamp(seconds, 1, 24 * 60 * 60);
        action = action == "shutdown" ? "shutdown" : "sleep";

        lock (_lock)
        {
            Cancel();

            _action = action;
            _fadeOutVolume = fadeOutVolume && action == "shutdown";
            _endUtc = DateTime.UtcNow.AddSeconds(seconds);

            _timer = new System.Threading.Timer(_ =>
            {
                try
                {
                    var act = _action;
                    var fade = _fadeOutVolume;
                    Cancel(); // one-shot

                    if (act == "shutdown") SystemActions.ShutdownPc(fade);
                    else SystemActions.SleepPc();
                }
                catch { /* ignore */ }
            }, null, TimeSpan.FromSeconds(seconds), System.Threading.Timeout.InfiniteTimeSpan);
        }
    }
}
