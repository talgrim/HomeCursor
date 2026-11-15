using System.Diagnostics.CodeAnalysis;
using System.Net;
using System.Security.Cryptography;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Sessions;

public sealed class SessionGate : ISessionGate
{
    private readonly object _sync = new();
    private readonly ILogger<SessionGate> _logger;
    private ClientSession? _current;

    public SessionGate(ILogger<SessionGate> logger)
    {
        _logger = logger;
    }

    public bool IsBusy
    {
        get
        {
            lock (_sync)
            {
                return _current is not null;
            }
        }
    }

    public bool TryAcquire(IPAddress remoteAddress, string clientName, [NotNullWhen(true)] out ClientSession? session)
    {
        lock (_sync)
        {
            if (_current is not null)
            {
                session = null;
                return false;
            }

            var sessionId = (uint)RandomNumberGenerator.GetInt32(1, int.MaxValue);
            session = new ClientSession(sessionId, Normalize(remoteAddress), clientName);
            _current = session;
        }

        _logger.LogInformation(
            "Client '{ClientName}' at {Address} acquired session {SessionId}", clientName, remoteAddress, session.SessionId);
        return true;
    }

    public void Release(ClientSession session)
    {
        lock (_sync)
        {
            if (!ReferenceEquals(_current, session))
            {
                return;
            }

            _current = null;
        }

        _logger.LogInformation("Session {SessionId} of '{ClientName}' released", session.SessionId, session.ClientName);
    }

    public ClientSession? FindActive(uint sessionId, IPAddress remoteAddress)
    {
        lock (_sync)
        {
            var current = _current;
            return current is not null
                && current.SessionId == sessionId
                && current.RemoteAddress.Equals(Normalize(remoteAddress))
                    ? current
                    : null;
        }
    }

    private static IPAddress Normalize(IPAddress address) =>
        address.IsIPv4MappedToIPv6 ? address.MapToIPv4() : address;
}
