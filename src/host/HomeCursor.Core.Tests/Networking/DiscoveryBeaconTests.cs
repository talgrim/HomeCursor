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

public sealed class DiscoveryBeaconTests : IAsyncLifetime
{
    private readonly HostSettings _settings = new()
    {
        BindAddress = "127.0.0.1",
        HostName = "Test PC",
        TcpPort = 5001,
        UdpPort = 5002,
        DiscoveryPort = TestPorts.FreeUdpPort()
    };

    private readonly SessionGate _gate = new(NullLogger<SessionGate>.Instance);
    private readonly DiscoveryBeacon _beacon;
    private readonly UdpClient _client = new(new IPEndPoint(IPAddress.Loopback, 0));

    public DiscoveryBeaconTests()
    {
        _beacon = new DiscoveryBeacon(Options.Create(_settings), _gate, NullLogger<DiscoveryBeacon>.Instance);
    }

    public Task InitializeAsync() => _beacon.StartAsync(CancellationToken.None);

    public async Task DisposeAsync()
    {
        _client.Dispose();
        await _beacon.StopAsync(CancellationToken.None);
    }

    private Task SendAsync(byte[] bytes) =>
        _client.SendAsync(bytes, bytes.Length, new IPEndPoint(IPAddress.Loopback, _settings.DiscoveryPort));

    private async Task<DiscoveryReplyMessage> ProbeAsync()
    {
        await SendAsync(DiscoveryPacket.Encode(new DiscoveryProbeMessage(ProtocolDefaults.Version)));

        using var cts = new CancellationTokenSource(5000);
        var result = await _client.ReceiveAsync(cts.Token);
        Assert.True(DiscoveryPacket.TryDecode(result.Buffer, out var message));
        return Assert.IsType<DiscoveryReplyMessage>(message);
    }

    [Fact]
    public async Task Probe_IsAnsweredWithHostNameAndPorts()
    {
        var reply = await ProbeAsync();

        Assert.Equal(new DiscoveryReplyMessage(ProtocolDefaults.Version, "Test PC", 5001, 5002, false), reply);
    }

    [Fact]
    public async Task Probe_WhileClientConnected_ReportsBusy()
    {
        _gate.TryAcquire(IPAddress.Loopback, "phone", out _);

        var reply = await ProbeAsync();

        Assert.True(reply.IsBusy);
    }

    [Fact]
    public async Task GarbageDatagram_IsIgnoredAndBeaconKeepsWorking()
    {
        await SendAsync([1, 2, 3, 4, 5, 6]);

        var reply = await ProbeAsync();

        Assert.Equal("Test PC", reply.HostName);
    }

    [Fact]
    public async Task ReplyDatagram_IsNotAnsweredAsProbe()
    {
        await SendAsync(DiscoveryPacket.Encode(new DiscoveryReplyMessage(1, "x", 1, 2, false)));

        var reply = await ProbeAsync();

        Assert.Equal("Test PC", reply.HostName);
    }

    [Fact]
    public async Task StartAsync_WhenPortIsInUse_Throws()
    {
        var beacon = new DiscoveryBeacon(Options.Create(_settings), _gate, NullLogger<DiscoveryBeacon>.Instance);

        await Assert.ThrowsAsync<SocketException>(() => beacon.StartAsync(CancellationToken.None));
    }
}
