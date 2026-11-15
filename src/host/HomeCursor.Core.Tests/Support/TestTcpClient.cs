using System.Net;
using System.Net.Sockets;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;

namespace HomeCursor.Core.Tests.Support;

internal sealed class TestTcpClient : IDisposable
{
    private readonly TcpClient _client;
    private readonly NetworkStream _stream;

    private TestTcpClient(TcpClient client)
    {
        _client = client;
        _stream = client.GetStream();
    }

    public static async Task<TestTcpClient> ConnectAsync(int port)
    {
        var client = new TcpClient();
        await client.ConnectAsync(IPAddress.Loopback, port);
        return new TestTcpClient(client);
    }

    public Task SendAsync(ProtocolMessage message) =>
        MessageFraming.WriteAsync(_stream, message, CancellationToken.None);

    public Task SendRawAsync(byte[] bytes) =>
        _stream.WriteAsync(bytes).AsTask();

    /// <summary>Returns the next message, or <c>null</c> when the host closed the connection.</summary>
    public async Task<ProtocolMessage?> ReceiveAsync(int timeoutMilliseconds = 5000)
    {
        using var cts = new CancellationTokenSource(timeoutMilliseconds);
        try
        {
            return await MessageFraming.ReadAsync(_stream, cts.Token);
        }
        catch (IOException)
        {
            return null;
        }
    }

    public async Task<WelcomeMessage> HandshakeAsync(string name = "test")
    {
        await SendAsync(new HelloMessage(ProtocolDefaults.Version, name));
        return Assert.IsType<WelcomeMessage>(await ReceiveAsync());
    }

    public void Dispose() => _client.Dispose();
}
