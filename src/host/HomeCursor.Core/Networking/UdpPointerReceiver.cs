using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Diagnostics;
using HomeCursor.Core.Input;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Networking;

/// <summary>
/// Receives pointer datagrams (movement and scrolling). A datagram is applied only when it carries the id
/// of the active session, comes from that client's address and is newer than the last one applied.
/// </summary>
public sealed class UdpPointerReceiver : HostLoopBase
{
    private readonly IOptions<HostSettings> _options;
    private readonly ISessionGate _gate;
    private readonly IMessageDispatcher _dispatcher;
    private readonly IPointerDiagnostics _diagnostics;
    private readonly ILogger<UdpPointerReceiver> _logger;
    private UdpClient? _udp;

    public UdpPointerReceiver(
        IOptions<HostSettings> settings,
        ISessionGate gate,
        IMessageDispatcher dispatcher,
        IPointerDiagnostics diagnostics,
        ILogger<UdpPointerReceiver> logger)
        : base(logger)
    {
        _options = settings;
        _gate = gate;
        _dispatcher = dispatcher;
        _diagnostics = diagnostics;
        _logger = logger;
    }

    public override string Name => "UDP pointer receiver";

    protected override void Bind()
    {
        var settings = _options.Value;
        _udp = new UdpClient(new IPEndPoint(settings.GetBindAddress(), settings.UdpPort));
        _logger.LogInformation("Receiving pointer data on UDP {Address}:{Port}", settings.BindAddress, settings.UdpPort);
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
            // Windows reports ICMP "port unreachable" from an earlier send on UDP sockets; nothing to do.
            return;
        }

        var received = Stopwatch.GetTimestamp();

        if (!UdpPacket.TryDecode(result.Buffer, out var packet))
        {
            _diagnostics.Record(new PacketSample(PacketOutcome.Undecodable, received));
            return;
        }

        if (packet.Message is not (MouseMoveMessage or ScrollMessage))
        {
            _diagnostics.Record(new PacketSample(PacketOutcome.NotPointer, received));
            return;
        }

        var session = _gate.FindActive(packet.SessionId, result.RemoteEndPoint.Address);
        if (session is null)
        {
            _diagnostics.Record(new PacketSample(PacketOutcome.Rejected, received));
            return;
        }

        if (!session.TryAcceptSequence(packet.Sequence, out var missed))
        {
            _diagnostics.Record(new PacketSample(PacketOutcome.Stale, received));
            return;
        }

        var backlog = _udp!.Available;
        var injectStart = Stopwatch.GetTimestamp();
        _dispatcher.Dispatch(packet.Message);
        _diagnostics.Record(new PacketSample(
            PacketOutcome.Applied, received, missed, backlog, Stopwatch.GetElapsedTime(injectStart)));
    }

    protected override Task UnbindAsync()
    {
        _udp?.Dispose();
        _udp = null;
        return Task.CompletedTask;
    }
}
