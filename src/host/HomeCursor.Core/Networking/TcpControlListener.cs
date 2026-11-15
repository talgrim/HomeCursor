using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using HomeCursor.Core.Input;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Networking;

/// <summary>
/// Accepts TCP connections, performs the Hello/Welcome handshake and then receives control messages.
/// Only one client may be connected at a time; the first one wins and later ones are rejected as busy.
/// </summary>
public sealed class TcpControlListener : HostLoopBase
{
    private readonly HostSettings _settings;
    private readonly ISessionGate _gate;
    private readonly IMessageDispatcher _dispatcher;
    private readonly ILogger<TcpControlListener> _logger;
    private readonly ConcurrentDictionary<Task, byte> _handlers = new();
    private TcpListener? _listener;

    public TcpControlListener(
        IOptions<HostSettings> settings,
        ISessionGate gate,
        IMessageDispatcher dispatcher,
        ILogger<TcpControlListener> logger)
        : base(logger)
    {
        _settings = settings.Value;
        _gate = gate;
        _dispatcher = dispatcher;
        _logger = logger;
    }

    public override string Name => "TCP control listener";

    protected override void Bind()
    {
        var listener = new TcpListener(_settings.GetBindAddress(), _settings.TcpPort);
        listener.Start();
        _listener = listener;
        _logger.LogInformation("Listening for clients on TCP {Address}:{Port}", _settings.BindAddress, _settings.TcpPort);
    }

    protected override async Task RunIterationAsync(CancellationToken cancellationToken)
    {
        var client = await _listener!.AcceptTcpClientAsync(cancellationToken).ConfigureAwait(false);

        var handler = HandleClientAsync(client, cancellationToken);
        _handlers.TryAdd(handler, 0);
        _ = handler.ContinueWith(task => _handlers.TryRemove(task, out _), TaskScheduler.Default);
    }

    protected override async Task UnbindAsync()
    {
        _listener?.Stop();
        _listener = null;

        await Task.WhenAll(_handlers.Keys).ConfigureAwait(false);
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken cancellationToken)
    {
        using (client)
        {
            ClientSession? session = null;
            var endReason = SessionEndReason.ClientDisconnected;
            try
            {
                if (client.Client.RemoteEndPoint is not IPEndPoint remote)
                {
                    return;
                }

                client.NoDelay = true;
                client.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.KeepAlive, true);
                var stream = client.GetStream();

                var hello = await ReadAsync(stream, cancellationToken).ConfigureAwait(false) as HelloMessage;
                if (hello is null)
                {
                    _logger.LogWarning("Client {Remote} did not start with Hello, closing", remote);
                    return;
                }

                if (hello.ProtocolVersion != ProtocolDefaults.Version)
                {
                    _logger.LogWarning(
                        "Client {Remote} uses protocol version {Version}, expected {Expected}",
                        remote, hello.ProtocolVersion, ProtocolDefaults.Version);
                    await MessageFraming.WriteAsync(
                        stream, new RejectMessage(RejectReason.UnsupportedVersion), cancellationToken).ConfigureAwait(false);
                    return;
                }

                if (!_gate.TryAcquire(remote.Address, hello.ClientName, out session))
                {
                    _logger.LogInformation("Rejected client {Remote}: another client is connected", remote);
                    await MessageFraming.WriteAsync(
                        stream, new RejectMessage(RejectReason.Busy), cancellationToken).ConfigureAwait(false);
                    return;
                }

                var welcome = new WelcomeMessage(
                    ProtocolDefaults.Version,
                    session.SessionId,
                    (ushort)_settings.UdpPort,
                    (byte)_settings.PingIntervalSeconds);
                await MessageFraming.WriteAsync(stream, welcome, cancellationToken).ConfigureAwait(false);

                await ReceiveLoopAsync(stream, cancellationToken).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                endReason = SessionEndReason.HostStopping;
            }
            catch (OperationCanceledException)
            {
                endReason = SessionEndReason.TimedOut;
                _logger.LogWarning(
                    "Client sent nothing for {Timeout}, closing", _settings.GetHeartbeatTimeout());
            }
            catch (ProtocolException ex)
            {
                endReason = SessionEndReason.ProtocolError;
                _logger.LogWarning("Closing connection after protocol error: {Message}", ex.Message);
            }
            catch (Exception ex) when (ex is IOException or SocketException)
            {
                endReason = SessionEndReason.ConnectionLost;
                _logger.LogInformation("Connection lost: {Message}", ex.Message);
            }
            catch (Exception ex)
            {
                endReason = SessionEndReason.Error;
                _logger.LogError(ex, "Unexpected error while serving a client");
            }
            finally
            {
                if (session is not null)
                {
                    _dispatcher.ReleaseAll();
                    _gate.Release(session, endReason);
                }
            }
        }
    }

    private async Task ReceiveLoopAsync(NetworkStream stream, CancellationToken cancellationToken)
    {
        while (true)
        {
            var message = await ReadAsync(stream, cancellationToken).ConfigureAwait(false);
            switch (message)
            {
                case null:
                    _logger.LogInformation("Client disconnected");
                    return;
                case PingMessage:
                    await MessageFraming.WriteAsync(stream, new PongMessage(), cancellationToken).ConfigureAwait(false);
                    break;
                case HelloMessage:
                    throw new ProtocolException("Unexpected Hello after the handshake.");
                default:
                    _dispatcher.Dispatch(message);
                    break;
            }
        }
    }

    /// <summary>Reads one message, failing with <see cref="OperationCanceledException"/> after the heartbeat timeout.</summary>
    private async Task<ProtocolMessage?> ReadAsync(NetworkStream stream, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(_settings.GetHeartbeatTimeout());
        return await MessageFraming.ReadAsync(stream, timeout.Token).ConfigureAwait(false);
    }
}
