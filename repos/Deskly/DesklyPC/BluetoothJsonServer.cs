using System.Text;
using InTheHand.Net.Bluetooth;
using InTheHand.Net.Sockets;

namespace DesklyPC;

public sealed class BluetoothJsonServer
{
    private readonly object _stateLock = new();
    private BluetoothListener? _listener;
    private CancellationTokenSource? _cts;

    public bool IsRunning { get; private set; }

    public event Action<string>? Log;
    public event Func<string, string>? HandleRequest;

    private const int MAX_LINE_CHARS = 256_000;

    public void Start()
    {
        lock (_stateLock)
        {
            if (IsRunning) return;

            _cts = new CancellationTokenSource();
            _listener = new BluetoothListener(DesklyBluetooth.ServiceUuid)
            {
                ServiceName = DesklyBluetooth.ServiceName
            };
            _listener.Start();
            IsRunning = true;
        }

        Log?.Invoke("[BT] Started");
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
            Log?.Invoke("[BT] Stopped");
        }
    }

    private async Task AcceptLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            BluetoothClient? client = null;
            try
            {
                var listener = _listener;
                if (listener is null) return;

                client = await Task.Run(() => listener.AcceptBluetoothClient(), ct);
                var ep = client.RemoteMachineName ?? "bluetooth";
                Log?.Invoke($"[BT] Connected: {ep}");
                _ = HandleClientAsync(client, ct, ep);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (Exception ex)
            {
                Log?.Invoke($"[BT] Accept failed: {ex.Message}");
                try { client?.Close(); } catch { }
            }
        }
    }

    private async Task HandleClientAsync(BluetoothClient client, CancellationToken ct, string ep)
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
                    var line = await reader.ReadLineAsync(ct);
                    if (line is null) break;
                    if (line.Length == 0) continue;

                    if (line.Length > MAX_LINE_CHARS)
                    {
                        await writer.WriteLineAsync("{\"type\":\"response\",\"ok\":false,\"message\":\"Payload too large\"}");
                        Log?.Invoke($"[BT] Payload too large ({line.Length} chars) from {ep}");
                        continue;
                    }

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
                }
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            Log?.Invoke($"[BT] Client({ep}) failed: {ex.Message}");
        }
        finally
        {
            Log?.Invoke($"[BT] Disconnected: {ep}");
        }
    }

    private static string Escape(string s)
        => s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", "").Replace("\n", "");
}
