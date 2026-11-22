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

public sealed class LiveSettingsTests
{
    [Fact]
    public async Task DiscoveryBeacon_HostNameChangedWhileRunning_AnswersWithTheNewName()
    {
        var port = TestPorts.FreeUdpPort();
        var holder = new SettingsHolder(new HostSettings { BindAddress = "127.0.0.1", HostName = "Old", DiscoveryPort = port });
        var beacon = new DiscoveryBeacon(
            holder, new SessionGate(NullLogger<SessionGate>.Instance), NullLogger<DiscoveryBeacon>.Instance);
        using var client = new UdpClient(new IPEndPoint(IPAddress.Loopback, 0));
        await beacon.StartAsync(CancellationToken.None);
        try
        {
            holder.Update(holder.Value with { HostName = "New" });

            var probe = DiscoveryPacket.Encode(new DiscoveryProbeMessage(ProtocolDefaults.Version));
            await client.SendAsync(probe, probe.Length, new IPEndPoint(IPAddress.Loopback, port));
            using var cts = new CancellationTokenSource(5000);
            var received = await client.ReceiveAsync(cts.Token);

            Assert.True(DiscoveryPacket.TryDecode(received.Buffer, out var message));
            Assert.Equal("New", Assert.IsType<DiscoveryReplyMessage>(message).HostName);
        }
        finally
        {
            await beacon.StopAsync(CancellationToken.None);
        }
    }
}
