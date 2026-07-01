using System.Diagnostics;

namespace DesklyPC;

public static class WebOpenService
{
    public static bool TryNormalizeHttpUrl(string? url, out string normalizedUrl, out string host, out string message)
    {
        normalizedUrl = "";
        host = "";
        message = "";

        var safeUrl = (url ?? "").Trim();
        if (string.IsNullOrWhiteSpace(safeUrl) || safeUrl.Length > 2000)
        {
            message = "Invalid URL";
            return false;
        }

        if (!Uri.TryCreate(safeUrl, UriKind.Absolute, out var uri))
        {
            message = "Invalid URL";
            return false;
        }

        if (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps)
        {
            message = "Unsupported URL scheme";
            return false;
        }

        if (string.IsNullOrWhiteSpace(uri.Host))
        {
            message = "Invalid URL host";
            return false;
        }

        normalizedUrl = uri.AbsoluteUri;
        host = uri.Host;
        message = "OK";
        return true;
    }

    public static bool TryOpenWebUrl(string? url, out string message, out string host)
    {
        if (!TryNormalizeHttpUrl(url, out var normalizedUrl, out host, out message))
            return false;

        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = normalizedUrl,
                UseShellExecute = true
            });
            message = "OK";
            return true;
        }
        catch (Exception ex)
        {
            message = ex.Message;
            return false;
        }
    }
}
