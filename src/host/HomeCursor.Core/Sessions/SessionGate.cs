using System.Diagnostics.CodeAnalysis;
using System.Net;
using System.Security.Cryptography;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Sessions;

public sealed class SessionGate : ISessionGate, ISessionMonitor
{
    private readonly object _sync = new();
    private readonly ILogger<SessionGate> _logger;
    private ClientSession? _current;

    public SessionGate(ILogger<SessionGate> logger)
    {
        _logger = logger;
    }

    public event EventHandler<ClientSession>? SessionStarted;

    public event EventHandler<SessionEndedEventArgs>? SessionEnded;

    public ClientSession? Current
    {
        get
        {
            lock (_sync)
            {
                return _current;
            }
        }
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
        Raise(SessionStarted, session);
        return true;
    }

    public void Release(ClientSession session, SessionEndReason reason)
    {
        lock (_sync)
        {
            if (!ReferenceEquals(_current, session))
            {
                return;
            }

            _current = null;
        }

        _logger.LogInformation(
            "Session {SessionId} of '{ClientName}' released ({Reason})", session.SessionId, session.ClientName, reason);
        Raise(SessionEnded, new SessionEndedEventArgs(session, reason));
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

    private void Raise<TArgs>(EventHandler<TArgs>? handlers, TArgs args)
    {
        if (handlers is null)
        {
            return;
        }

        // Invoke subscribers one by one so a faulty one can neither break the session nor starve the others.
        foreach (var handler in handlers.GetInvocationList().Cast<EventHandler<TArgs>>())
        {
            try
            {
                handler(this, args);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "A session event subscriber threw an exception");
            }
        }
    }

    private static IPAddress Normalize(IPAddress address) =>
        address.IsIPv4MappedToIPv6 ? address.MapToIPv4() : address;
}
