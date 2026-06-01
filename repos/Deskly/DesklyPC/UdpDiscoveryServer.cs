using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace DesklyPC;

public sealed class UdpDiscoveryServer
{
    private UdpClient? _udp;
    private CancellationTokenSource? _cts;

    public bool IsRunning { get; private set; }

    public event Action<string>? Log;

    private const string DISCOVER_MAGIC = "DESKLY_DISCOVER";

    private readonly string _serverId;
    private readonly string _serverName;

    // ✅ aby discovery nespamovalo log (1 log / endpoint / 10s)
    private readonly ConcurrentDictionary<string, DateTime> _lastReplyLogUtc = new();
    private static readonly TimeSpan REPLY_LOG_COOLDOWN = TimeSpan.FromSeconds(10);

    public UdpDiscoveryServer(string serverId, string serverName)
    {
        _serverId = serverId;
        _serverName = serverName;
    }

    public void Start(int udpPort, int tcpPort)
    {
        if (IsRunning) return;

        _cts = new CancellationTokenSource();

        _udp = new UdpClient();
        _udp.EnableBroadcast = true;
        _udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _udp.Client.Bind(new IPEndPoint(IPAddress.Any, udpPort));

        IsRunning = true;
        Log?.Invoke($"[DISCOVERY] Listening (UDP {udpPort})");

        _ = LoopAsync(_cts.Token, udpPort, tcpPort);
    }

    public void Stop()
    {
        if (!IsRunning) return;
        IsRunning = false;

        try { _cts?.Cancel(); } catch { }
        try { _udp?.Close(); } catch { }
        try { _udp?.Dispose(); } catch { }

        _cts = null;
        _udp = null;

        Log?.Invoke("[DISCOVERY] Stopped");
    }

    private async Task LoopAsync(CancellationToken ct, int udpPort, int tcpPort)
    {
        if (_udp == null) return;

        while (!ct.IsCancellationRequested)
        {
            try
            {
                var result = await _udp.ReceiveAsync(ct);
                var text = Encoding.UTF8.GetString(result.Buffer).Trim();

                if (!string.Equals(text, DISCOVER_MAGIC, StringComparison.Ordinal))
                    continue;

                var localIp = GetLocalIPv4ForTarget(result.RemoteEndPoint.Address) ?? "0.0.0.0";

                var json =
                    $"{{\"type\":\"discover_response\",\"id\":\"{Escape(_serverId)}\",\"name\":\"{Escape(_serverName)}\",\"ip\":\"{Escape(localIp)}\",\"port\":{tcpPort},\"udpPort\":{udpPort}}}";

                var bytes = Encoding.UTF8.GetBytes(json);
                await _udp.SendAsync(bytes, bytes.Length, result.RemoteEndPoint);

                // ✅ stručný log + throttling
                var key = result.RemoteEndPoint.ToString();
                var now = DateTime.UtcNow;

                var last = _lastReplyLogUtc.GetOrAdd(key, DateTime.MinValue);
                if (now - last > REPLY_LOG_COOLDOWN)
                {
                    _lastReplyLogUtc[key] = now;
                    Log?.Invoke($"[DISCOVERY] Replied to {key} (ip {localIp}, port {tcpPort})");
                }
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (Exception ex)
            {
                Log?.Invoke($"[DISCOVERY] Error: {ex.Message}");
            }
        }
    }

    private static string? GetLocalIPv4ForTarget(IPAddress target)
    {
        try
        {
            using var s = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
            s.Connect(new IPEndPoint(target, 9));
            var ep = s.LocalEndPoint as IPEndPoint;
            return ep?.Address.ToString();
        }
        catch
        {
            return null;
        }
    }

    private static string Escape(string s)
        => s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "");
}
