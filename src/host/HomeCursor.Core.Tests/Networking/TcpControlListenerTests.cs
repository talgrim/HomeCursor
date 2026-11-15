using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Networking;

public sealed class TcpControlListenerTests : IAsyncLifetime
{
    private readonly HostSettings _settings = new()
    {
        BindAddress = "127.0.0.1",
        TcpPort = TestPorts.FreeTcpPort(),
        UdpPort = 47801,
        PingIntervalSeconds = 1,
        HeartbeatTimeoutSeconds = 2
    };

    private readonly SessionGate _gate = new(NullLogger<SessionGate>.Instance);
    private readonly RecordingDispatcher _dispatcher = new();
    private readonly TcpControlListener _listener;
    private readonly List<TestTcpClient> _clients = [];

    public TcpControlListenerTests()
    {
        _listener = new TcpControlListener(_settings, _gate, _dispatcher, NullLogger<TcpControlListener>.Instance);
    }

    public Task InitializeAsync() => _listener.StartAsync(CancellationToken.None);

    public async Task DisposeAsync()
    {
        foreach (var client in _clients)
        {
            client.Dispose();
        }

        await _listener.StopAsync(CancellationToken.None);
    }

    private async Task<TestTcpClient> ConnectAsync()
    {
        var client = await TestTcpClient.ConnectAsync(_settings.TcpPort);
        _clients.Add(client);
        return client;
    }

    [Fact]
    public async Task Hello_ReceivesWelcomeWithSessionIdUdpPortAndPingInterval()
    {
        var client = await ConnectAsync();

        var welcome = await client.HandshakeAsync();

        Assert.Equal(ProtocolDefaults.Version, welcome.ProtocolVersion);
        Assert.NotEqual(0u, welcome.SessionId);
        Assert.Equal(_settings.UdpPort, welcome.UdpPort);
        Assert.Equal(_settings.PingIntervalSeconds, welcome.PingIntervalSeconds);
        Assert.True(_gate.IsBusy);
    }

    [Fact]
    public async Task Hello_WithUnsupportedVersion_IsRejectedAndDoesNotTakeTheSession()
    {
        var client = await ConnectAsync();

        await client.SendAsync(new HelloMessage((byte)(ProtocolDefaults.Version + 1), "old"));

        Assert.Equal(new RejectMessage(RejectReason.UnsupportedVersion), await client.ReceiveAsync());
        Assert.Null(await client.ReceiveAsync());
        Assert.False(_gate.IsBusy);
    }

    [Fact]
    public async Task SecondClient_WhileFirstIsConnected_IsRejectedAsBusy()
    {
        var first = await ConnectAsync();
        await first.HandshakeAsync("first");
        var second = await ConnectAsync();

        await second.SendAsync(new HelloMessage(ProtocolDefaults.Version, "second"));

        Assert.Equal(new RejectMessage(RejectReason.Busy), await second.ReceiveAsync());
        Assert.Null(await second.ReceiveAsync());
    }

    [Fact]
    public async Task SecondClient_Rejected_DoesNotDisturbFirstClient()
    {
        var first = await ConnectAsync();
        await first.HandshakeAsync("first");
        var second = await ConnectAsync();
        await second.SendAsync(new HelloMessage(ProtocolDefaults.Version, "second"));
        await second.ReceiveAsync();

        await first.SendAsync(new PingMessage());

        Assert.Equal(new PongMessage(), await first.ReceiveAsync());
        Assert.Equal(0, _dispatcher.ReleaseAllCount);
    }

    [Fact]
    public async Task NewClient_AfterFirstDisconnects_IsAccepted()
    {
        var first = await ConnectAsync();
        await first.HandshakeAsync("first");
        first.Dispose();
        await Wait.UntilAsync(() => !_gate.IsBusy);

        var second = await ConnectAsync();
        var welcome = await second.HandshakeAsync("second");

        Assert.NotNull(welcome);
    }

    [Fact]
    public async Task Ping_ReceivesPong()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        await client.SendAsync(new PingMessage());

        Assert.Equal(new PongMessage(), await client.ReceiveAsync());
    }

    [Fact]
    public async Task InputMessages_AreDispatchedInOrder()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        await client.SendAsync(new MouseMoveMessage(1, 2));
        await client.SendAsync(new TextMessage("hi"));
        await Wait.UntilAsync(() => _dispatcher.Messages.Count == 2);

        Assert.Equal<ProtocolMessage>([new MouseMoveMessage(1, 2), new TextMessage("hi")], _dispatcher.Messages);
    }

    [Fact]
    public async Task Disconnect_ReleasesHeldInputAndSession()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        client.Dispose();
        await Wait.UntilAsync(() => !_gate.IsBusy);

        Assert.Equal(1, _dispatcher.ReleaseAllCount);
    }

    [Fact]
    public async Task FirstMessageNotHello_ClosesConnection()
    {
        var client = await ConnectAsync();

        await client.SendAsync(new PingMessage());

        Assert.Null(await client.ReceiveAsync());
        Assert.False(_gate.IsBusy);
    }

    [Fact]
    public async Task HelloAfterHandshake_ClosesConnectionAndReleasesSession()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        await client.SendAsync(new HelloMessage(ProtocolDefaults.Version, "again"));

        Assert.Null(await client.ReceiveAsync());
        await Wait.UntilAsync(() => !_gate.IsBusy);
    }

    [Fact]
    public async Task MalformedFrame_ClosesConnectionAndReleasesSession()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        await client.SendRawAsync([0x00, 0x00]);

        Assert.Null(await client.ReceiveAsync());
        await Wait.UntilAsync(() => !_gate.IsBusy);
    }

    [Fact]
    public async Task SilentClient_IsDroppedAfterHeartbeatTimeout()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        var next = await client.ReceiveAsync(timeoutMilliseconds: 10_000);

        Assert.Null(next);
        await Wait.UntilAsync(() => !_gate.IsBusy);
        Assert.Equal(1, _dispatcher.ReleaseAllCount);
    }

    [Fact]
    public async Task StartAsync_WhenPortIsInUse_Throws()
    {
        var blocker = new TcpListener(IPAddress.Loopback, 0);
        blocker.Start();
        try
        {
            var port = ((IPEndPoint)blocker.LocalEndpoint).Port;
            var settings = _settings with { TcpPort = port };
            var listener = new TcpControlListener(
                settings, _gate, _dispatcher, NullLogger<TcpControlListener>.Instance);

            await Assert.ThrowsAsync<SocketException>(() => listener.StartAsync(CancellationToken.None));
        }
        finally
        {
            blocker.Stop();
        }
    }

    [Fact]
    public async Task StopAsync_ClosesConnectedClientsAndReleasesSession()
    {
        var client = await ConnectAsync();
        await client.HandshakeAsync();

        await _listener.StopAsync(CancellationToken.None);

        Assert.Null(await client.ReceiveAsync());
        Assert.False(_gate.IsBusy);
    }
}
