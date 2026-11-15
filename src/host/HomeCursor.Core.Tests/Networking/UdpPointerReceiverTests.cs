using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Tests.Networking;

public sealed class UdpPointerReceiverTests : IAsyncLifetime
{
    private readonly HostSettings _settings = new()
    {
        BindAddress = "127.0.0.1",
        UdpPort = TestPorts.FreeUdpPort()
    };

    private readonly SessionGate _gate = new(NullLogger<SessionGate>.Instance);
    private readonly RecordingDispatcher _dispatcher = new();
    private readonly UdpPointerReceiver _receiver;
    private readonly UdpClient _sender = new();

    public UdpPointerReceiverTests()
    {
        _receiver = new UdpPointerReceiver(Options.Create(_settings), _gate, _dispatcher, NullLogger<UdpPointerReceiver>.Instance);
    }

    public Task InitializeAsync() => _receiver.StartAsync(CancellationToken.None);

    public async Task DisposeAsync()
    {
        _sender.Dispose();
        await _receiver.StopAsync(CancellationToken.None);
    }

    private ClientSession AcquireLoopbackSession()
    {
        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "test", out var session));
        return session;
    }

    private Task SendAsync(uint sessionId, uint sequence, ProtocolMessage message)
    {
        var bytes = new UdpPacket(sessionId, sequence, message).Encode();
        return _sender.SendAsync(bytes, bytes.Length, new IPEndPoint(IPAddress.Loopback, _settings.UdpPort));
    }

    [Fact]
    public async Task ValidPacket_IsDispatched()
    {
        var session = AcquireLoopbackSession();

        await SendAsync(session.SessionId, 1, new MouseMoveMessage(5, 6));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count == 1);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(5, 6)], _dispatcher.Messages);
    }

    [Fact]
    public async Task ScrollPacket_IsDispatched()
    {
        var session = AcquireLoopbackSession();

        await SendAsync(session.SessionId, 1, new ScrollMessage(0, 120));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count == 1);

        Assert.Equal<ProtocolMessage>([new ScrollMessage(0, 120)], _dispatcher.Messages);
    }

    [Fact]
    public async Task PacketWithoutActiveSession_IsIgnored()
    {
        await SendAsync(1, 1, new MouseMoveMessage(1, 1));
        var session = AcquireLoopbackSession();
        await SendAsync(session.SessionId, 1, new MouseMoveMessage(2, 2));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count >= 1);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(2, 2)], _dispatcher.Messages);
    }

    [Fact]
    public async Task PacketWithWrongSessionId_IsIgnored()
    {
        var session = AcquireLoopbackSession();

        await SendAsync(session.SessionId + 1, 1, new MouseMoveMessage(1, 1));
        await SendAsync(session.SessionId, 2, new MouseMoveMessage(2, 2));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count >= 1);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(2, 2)], _dispatcher.Messages);
    }

    [Fact]
    public async Task PacketFromOtherAddress_IsIgnored()
    {
        _gate.TryAcquire(IPAddress.Parse("10.1.2.3"), "elsewhere", out var other);
        await SendAsync(other!.SessionId, 1, new MouseMoveMessage(1, 1));
        _gate.Release(other, SessionEndReason.ClientDisconnected);
        var session = AcquireLoopbackSession();

        await SendAsync(session.SessionId, 1, new MouseMoveMessage(2, 2));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count >= 1);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(2, 2)], _dispatcher.Messages);
    }

    [Fact]
    public async Task StaleOrDuplicateSequence_IsDropped()
    {
        var session = AcquireLoopbackSession();

        await SendAsync(session.SessionId, 5, new MouseMoveMessage(5, 0));
        await SendAsync(session.SessionId, 3, new MouseMoveMessage(3, 0));
        await SendAsync(session.SessionId, 5, new MouseMoveMessage(55, 0));
        await SendAsync(session.SessionId, 6, new MouseMoveMessage(6, 0));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count >= 2);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(5, 0), new MouseMoveMessage(6, 0)], _dispatcher.Messages);
    }

    [Fact]
    public async Task NonPointerMessage_IsIgnored()
    {
        var session = AcquireLoopbackSession();

        await SendAsync(session.SessionId, 1, new TextMessage("typed over UDP"));
        await SendAsync(session.SessionId, 2, new MouseMoveMessage(2, 2));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count >= 1);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(2, 2)], _dispatcher.Messages);
    }

    [Fact]
    public async Task GarbageDatagram_IsIgnored()
    {
        var session = AcquireLoopbackSession();

        await _sender.SendAsync([1, 2, 3], 3, new IPEndPoint(IPAddress.Loopback, _settings.UdpPort));
        await SendAsync(session.SessionId, 1, new MouseMoveMessage(2, 2));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count >= 1);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(2, 2)], _dispatcher.Messages);
    }

    [Fact]
    public async Task StartAsync_WhenPortIsInUse_Throws()
    {
        var receiver = new UdpPointerReceiver(
            Options.Create(_settings), _gate, _dispatcher, NullLogger<UdpPointerReceiver>.Instance);

        await Assert.ThrowsAsync<SocketException>(() => receiver.StartAsync(CancellationToken.None));
    }
}
