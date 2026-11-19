using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Diagnostics;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Tests.Networking;

public sealed class UdpPointerReceiverDiagnosticsTests : IAsyncLifetime
{
    private readonly HostSettings _settings = new()
    {
        BindAddress = "127.0.0.1",
        UdpPort = TestPorts.FreeUdpPort()
    };

    private readonly SessionGate _gate = new(NullLogger<SessionGate>.Instance);
    private readonly RecordingPointerDiagnostics _diagnostics = new();
    private readonly UdpPointerReceiver _receiver;
    private readonly UdpClient _sender = new();

    public UdpPointerReceiverDiagnosticsTests()
    {
        _receiver = new UdpPointerReceiver(
            Options.Create(_settings), _gate, new RecordingDispatcher(), _diagnostics, NullLogger<UdpPointerReceiver>.Instance);
    }

    public Task InitializeAsync() => _receiver.StartAsync(CancellationToken.None);

    public async Task DisposeAsync()
    {
        _sender.Dispose();
        await _receiver.StopAsync(CancellationToken.None);
    }

    private Task SendAsync(uint sessionId, uint sequence, ProtocolMessage message)
    {
        var bytes = new UdpPacket(sessionId, sequence, message).Encode();
        return _sender.SendAsync(bytes, bytes.Length, new IPEndPoint(IPAddress.Loopback, _settings.UdpPort));
    }

    private ClientSession Acquire()
    {
        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "test", out var session));
        return session;
    }

    private async Task<PacketOutcome[]> OutcomesAsync(int count)
    {
        await Wait.UntilAsync(() => _diagnostics.Samples.Count >= count);
        return [.. _diagnostics.Samples.Select(sample => sample.Outcome)];
    }

    [Fact]
    public async Task AppliedPacket_ReportsMissedSequencesAndInjectionTime()
    {
        var session = Acquire();

        await SendAsync(session.SessionId, 1, new MouseMoveMessage(1, 1));
        await SendAsync(session.SessionId, 4, new MouseMoveMessage(1, 1));
        await OutcomesAsync(2);

        var samples = _diagnostics.Samples.ToArray();
        Assert.All(samples, sample => Assert.Equal(PacketOutcome.Applied, sample.Outcome));
        Assert.Equal(0, samples[0].MissedBefore);
        Assert.Equal(2, samples[1].MissedBefore);
        Assert.True(samples[1].Timestamp >= samples[0].Timestamp);
        Assert.True(samples[1].InjectTime >= TimeSpan.Zero);
    }

    [Fact]
    public async Task DroppedPackets_AreReportedWithTheirReason()
    {
        var session = Acquire();

        await SendAsync(session.SessionId, 5, new MouseMoveMessage(1, 1));
        await SendAsync(session.SessionId, 3, new MouseMoveMessage(1, 1));
        await SendAsync(session.SessionId + 1, 6, new MouseMoveMessage(1, 1));
        await SendAsync(session.SessionId, 7, new TextMessage("not pointer"));
        await _sender.SendAsync([1, 2, 3], 3, new IPEndPoint(IPAddress.Loopback, _settings.UdpPort));
        var outcomes = await OutcomesAsync(5);

        Assert.Equal(
            [PacketOutcome.Applied, PacketOutcome.Stale, PacketOutcome.Rejected, PacketOutcome.NotPointer, PacketOutcome.Undecodable],
            outcomes);
    }
}
