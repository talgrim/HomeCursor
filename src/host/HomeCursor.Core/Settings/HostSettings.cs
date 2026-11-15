using System.Net;
using HomeCursor.Protocol;

namespace HomeCursor.Core.Settings;

/// <summary>
/// User-configurable host settings. Every property has a default, so an empty JSON object is valid.
/// </summary>
public sealed record HostSettings
{
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
    public bool NotifyOnClientDisconnection { get; init; } = true;

    public IPAddress GetBindAddress() => IPAddress.Parse(BindAddress);

    public TimeSpan GetHeartbeatTimeout() => TimeSpan.FromSeconds(HeartbeatTimeoutSeconds);

    /// <exception cref="SettingsException">A value is out of range.</exception>
    public void Validate()
    {
        if (!IPAddress.TryParse(BindAddress, out _))
        {
            throw new SettingsException($"{nameof(BindAddress)} '{BindAddress}' is not a valid IP address.");
        }

        ValidatePort(nameof(TcpPort), TcpPort);
        ValidatePort(nameof(UdpPort), UdpPort);
        ValidatePort(nameof(DiscoveryPort), DiscoveryPort);

        if (UdpPort == DiscoveryPort)
        {
            throw new SettingsException($"{nameof(UdpPort)} and {nameof(DiscoveryPort)} must be different.");
        }

        if (string.IsNullOrWhiteSpace(HostName) || HostName.Length > MaxHostNameLength)
        {
            throw new SettingsException($"{nameof(HostName)} must be 1-{MaxHostNameLength} characters long.");
        }

        if (PingIntervalSeconds is < 1 or > byte.MaxValue)
        {
            throw new SettingsException($"{nameof(PingIntervalSeconds)} must be between 1 and {byte.MaxValue}.");
        }

        if (HeartbeatTimeoutSeconds <= PingIntervalSeconds)
        {
            throw new SettingsException(
                $"{nameof(HeartbeatTimeoutSeconds)} must be greater than {nameof(PingIntervalSeconds)}.");
        }
    }

    private static void ValidatePort(string name, int port)
    {
        if (port is < 1 or > ushort.MaxValue)
        {
            throw new SettingsException($"{name} must be between 1 and {ushort.MaxValue}.");
        }
    }
}
