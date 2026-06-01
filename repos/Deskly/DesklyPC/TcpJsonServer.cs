using System.Net;
using System.Net.Sockets;
using System.Text;

namespace DesklyPC;

public sealed class TcpJsonServer
{
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;

    private readonly object _stateLock = new();

    public bool IsRunning { get; private set; }

    public event Action<string>? Log;

    // vráti JSON string ako odpoveď
    public event Func<string, string>? HandleRequest;

    // ✅ len keď chceš debugovať wire (IN/OUT)
    public bool LogTraffic { get; set; } = false;

    // bezpečnostný limit na 1 riadok (aby ti niekto nezabil RAM)
    private const int MAX_LINE_CHARS = 256_000;

    public void Start(int port)
    {
        lock (_stateLock)
        {
            if (IsRunning) return;

            _cts = new CancellationTokenSource();
            _listener = new TcpListener(IPAddress.Any, port);
            _listener.Start();

            IsRunning = true;
        }

        Log?.Invoke($"[SERVER] Started (port {port})");

        _ = AcceptLoopAsync(_cts!.Token);
    }

    public void Stop()
    {
        lock (_stateLock)
        {
            if (!IsRunning) return;
            IsRunning = false;
        }

        try
        {
            _cts?.Cancel();
            _listener?.Stop();
        }
        catch { /* ignore */ }
        finally
        {
            Log?.Invoke("[SERVER] Stopped");
        }
    }

    private async Task AcceptLoopAsync(CancellationToken ct)
    {
        var listener = _listener;
        if (listener is null) return;

        while (!ct.IsCancellationRequested)
        {
            TcpClient? client = null;
            try
            {
                client = await listener.AcceptTcpClientAsync(ct);

                var ep = client.Client.RemoteEndPoint?.ToString() ?? "unknown";
                Log?.Invoke($"[CONN] Connected: {ep}");

                _ = HandleClientAsync(client, ct, ep);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (Exception ex)
            {
                Log?.Invoke($"[ERROR] Accept: {ex.Message}");
                try { client?.Close(); } catch { }
            }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken ct, string ep)
    {
        try
        {
            using (client)
            using (var stream = client.GetStream())
            using (var reader = new StreamReader(stream, Encoding.UTF8, detectEncodingFromByteOrderMarks: true, bufferSize: 8192, leaveOpen: true))
            using (var writer = new StreamWriter(stream, Encoding.UTF8, bufferSize: 8192, leaveOpen: true) { AutoFlush = true })
            {
                while (!ct.IsCancellationRequested)
                {
#if NET8_0_OR_GREATER
                    var line = await reader.ReadLineAsync(ct);
#else
                    var line = await reader.ReadLineAsync();
#endif
                    if (line is null) break;
                    if (line.Length == 0) continue;

                    if (line.Length > MAX_LINE_CHARS)
                    {
                        var tooBig = "{\"type\":\"response\",\"ok\":false,\"message\":\"Payload too large\"}";
                        await writer.WriteLineAsync(tooBig);
                        Log?.Invoke($"[WARN] Payload too large ({line.Length} chars) from {ep}");
                        continue;
                    }

                    if (LogTraffic) Log?.Invoke($"[IN ] {line}");

                    string resp;
                    try
                    {
                        resp = HandleRequest?.Invoke(line)
                               ?? "{\"type\":\"response\",\"ok\":false,\"message\":\"No handler\"}";
                    }
                    catch (Exception hx)
                    {
                        resp = $"{{\"type\":\"response\",\"ok\":false,\"message\":\"Handler error: {Escape(hx.Message)}\"}}";
                    }

                    await writer.WriteLineAsync(resp);

                    if (LogTraffic) Log?.Invoke($"[OUT] {resp}");
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            Log?.Invoke($"[ERROR] Client({ep}): {ex.Message}");
        }
        finally
        {
            Log?.Invoke($"[CONN] Disconnected: {ep}");
        }
    }

    private static string Escape(string s)
        => s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "");
}
