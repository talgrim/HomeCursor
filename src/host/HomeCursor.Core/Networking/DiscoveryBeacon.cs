using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Networking;

/// <summary>
/// Answers UDP discovery probes (sent as broadcasts by clients) with the host name, ports and busy flag.
/// </summary>
public sealed class DiscoveryBeacon : HostLoopBase
{
    private readonly IOptions<HostSettings> _options;
    private readonly ISessionGate _gate;
    private readonly ILogger<DiscoveryBeacon> _logger;
    private UdpClient? _udp;

    public DiscoveryBeacon(IOptions<HostSettings> settings, ISessionGate gate, ILogger<DiscoveryBeacon> logger)
        : base(logger)
    {
        _options = settings;
        _gate = gate;
        _logger = logger;
    }

    public override string Name => "Discovery beacon";

    protected override void Bind()
    {
        var settings = _options.Value;
        _udp = new UdpClient(new IPEndPoint(settings.GetBindAddress(), settings.DiscoveryPort));
        _logger.LogInformation(
            "Answering discovery probes on UDP {Address}:{Port}", settings.BindAddress, settings.DiscoveryPort);
    }

    protected override async Task RunIterationAsync(CancellationToken cancellationToken)
    {
        UdpReceiveResult result;
        try
        {
            result = await _udp!.ReceiveAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (SocketException ex) when (ex.SocketErrorCode == SocketError.ConnectionReset)
        {
            return;
        }

        if (!DiscoveryPacket.TryDecode(result.Buffer, out var message) || message is not DiscoveryProbeMessage)
        {
            return;
        }

        var settings = _options.Value;
        var reply = new DiscoveryReplyMessage(
            ProtocolDefaults.Version,
            settings.HostName,
            (ushort)settings.TcpPort,
            (ushort)settings.UdpPort,
            _gate.IsBusy);
        var bytes = DiscoveryPacket.Encode(reply);

        await _udp!.SendAsync(bytes, result.RemoteEndPoint, cancellationToken).ConfigureAwait(false);
        _logger.LogDebug("Answered discovery probe from {Remote}", result.RemoteEndPoint);
    }

    protected override Task UnbindAsync()
    {
        _udp?.Dispose();
        _udp = null;
        return Task.CompletedTask;
    }
}
