using System.Net;
using HomeCursor.Protocol;

namespace HomeCursor.Core.Settings;

/// <summary>
/// User-configurable host settings. Every property has a default, so an empty JSON object is valid.
/// </summary>
public sealed record HostSettings
{
    public const string DefaultUpdateManifestUrl = "https://wip.talgrim.dev/files/manifest.json";

    private const int MaxHostNameLength = 64;

    /// <summary>Local address the sockets bind to. <c>0.0.0.0</c> listens on every network interface.</summary>
    public string BindAddress { get; init; } = "0.0.0.0";

    public int TcpPort { get; init; } = ProtocolDefaults.TcpPort;

    public int UdpPort { get; init; } = ProtocolDefaults.UdpPointerPort;

    public int DiscoveryPort { get; init; } = ProtocolDefaults.DiscoveryPort;

    /// <summary>Name advertised to clients during discovery.</summary>
    public string HostName { get; init; } = Environment.MachineName;

    /// <summary>How often clients are told to send a ping.</summary>
    public int PingIntervalSeconds { get; init; } = 5;

    /// <summary>A connected client that sends nothing for this long is dropped.</summary>
    public int HeartbeatTimeoutSeconds { get; init; } = 15;

    /// <summary>Whether the host shows a notification when a client connects.</summary>
    public bool NotifyOnClientConnection { get; init; } = true;

    /// <summary>Whether the host shows a notification when a client disconnects.</summary>
    public bool NotifyOnClientDisconnection { get; init; }

    /// <summary>
    /// Whether the host looks for a newer release (at start and then once a day) and offers to install it.
    /// The tray menu can still check by hand when this is off.
    /// </summary>
    public bool CheckForUpdates { get; init; } = true;

    /// <summary>Address of the release manifest the host checks for updates.</summary>
    public string UpdateManifestUrl { get; init; } = DefaultUpdateManifestUrl;

    /// <summary>
    /// Whether the host writes a diagnostics log (connection events and per-second pointer statistics) to a file,
    /// to help find out where lag comes from. Off by default.
    /// </summary>
    public bool DiagnosticsEnabled { get; init; }

    /// <summary>Folder of the diagnostics log. Empty uses the folder of the host's executable.</summary>
    public string? DiagnosticsLogDirectory { get; init; }

    public IPAddress GetBindAddress() => IPAddress.Parse(BindAddress);

    public TimeSpan GetHeartbeatTimeout() => TimeSpan.FromSeconds(HeartbeatTimeoutSeconds);

    /// <exception cref="SettingsException">A value is out of range.</exception>
    public void Validate()
    {
        var first = GetErrors().FirstOrDefault();
        if (first is not null)
        {
            throw new SettingsException(first.Message);
        }
    }

    /// <summary>Every problem with the current values, in the order they are checked. Empty when the settings are valid.</summary>
    public IReadOnlyList<SettingsError> GetErrors()
    {
        var errors = new List<SettingsError>();

        if (!IPAddress.TryParse(BindAddress, out _))
        {
            errors.Add(new(nameof(BindAddress), $"{nameof(BindAddress)} '{BindAddress}' is not a valid IP address."));
        }

        ValidatePort(errors, nameof(TcpPort), TcpPort);
        ValidatePort(errors, nameof(UdpPort), UdpPort);
        ValidatePort(errors, nameof(DiscoveryPort), DiscoveryPort);

        if (UdpPort == DiscoveryPort)
        {
            errors.Add(new(nameof(DiscoveryPort), $"{nameof(UdpPort)} and {nameof(DiscoveryPort)} must be different."));
        }

        if (string.IsNullOrWhiteSpace(HostName) || HostName.Length > MaxHostNameLength)
        {
            errors.Add(new(nameof(HostName), $"{nameof(HostName)} must be 1-{MaxHostNameLength} characters long."));
        }

        if (PingIntervalSeconds is < 1 or > byte.MaxValue)
        {
            errors.Add(new(
                nameof(PingIntervalSeconds), $"{nameof(PingIntervalSeconds)} must be between 1 and {byte.MaxValue}."));
        }

        if (!Uri.TryCreate(UpdateManifestUrl, UriKind.Absolute, out var manifestUri) ||
            manifestUri.Scheme is not ("http" or "https"))
        {
            errors.Add(new(
                nameof(UpdateManifestUrl),
                $"{nameof(UpdateManifestUrl)} '{UpdateManifestUrl}' is not a valid http(s) address."));
        }

        if (HeartbeatTimeoutSeconds <= PingIntervalSeconds)
        {
            errors.Add(new(
                nameof(HeartbeatTimeoutSeconds),
                $"{nameof(HeartbeatTimeoutSeconds)} must be greater than {nameof(PingIntervalSeconds)}."));
        }

        return errors;
    }

    private static void ValidatePort(List<SettingsError> errors, string name, int port)
    {
        if (port is < 1 or > ushort.MaxValue)
        {
            errors.Add(new(name, $"{name} must be between 1 and {ushort.MaxValue}."));
        }
    }
}
