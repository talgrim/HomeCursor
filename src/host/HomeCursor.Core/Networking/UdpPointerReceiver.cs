using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Input;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Networking;

/// <summary>
/// Receives pointer datagrams (movement and scrolling). A datagram is applied only when it carries the id
/// of the active session, comes from that client's address and is newer than the last one applied.
/// </summary>
public sealed class UdpPointerReceiver : HostLoopBase
{
    private readonly HostSettings _settings;
    private readonly ISessionGate _gate;
    private readonly IMessageDispatcher _dispatcher;
    private readonly ILogger<UdpPointerReceiver> _logger;
    private UdpClient? _udp;

    public UdpPointerReceiver(
        HostSettings settings,
        ISessionGate gate,
        IMessageDispatcher dispatcher,
        ILogger<UdpPointerReceiver> logger)
        : base(logger)
    {
        _settings = settings;
        _gate = gate;
        _dispatcher = dispatcher;
        _logger = logger;
    }

    public override string Name => "UDP pointer receiver";

    protected override void Bind()
    {
        _udp = new UdpClient(new IPEndPoint(_settings.GetBindAddress(), _settings.UdpPort));
        _logger.LogInformation("Receiving pointer data on UDP {Address}:{Port}", _settings.BindAddress, _settings.UdpPort);
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

        if (!UdpPacket.TryDecode(result.Buffer, out var packet))
        {
            return;
        }

        if (packet.Message is not (MouseMoveMessage or ScrollMessage))
        {
            return;
        }

        var session = _gate.FindActive(packet.SessionId, result.RemoteEndPoint.Address);
        if (session is null || !session.TryAcceptSequence(packet.Sequence))
        {
            return;
        }

        _dispatcher.Dispatch(packet.Message);
    }

    protected override Task UnbindAsync()
    {
        _udp?.Dispose();
        _udp = null;
        return Task.CompletedTask;
    }
}
