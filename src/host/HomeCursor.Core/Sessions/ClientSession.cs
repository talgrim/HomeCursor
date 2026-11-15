using System.Net;

namespace HomeCursor.Core.Sessions;

/// <summary>
/// The one client that currently controls the host.
/// </summary>
public sealed class ClientSession
{
    private readonly object _sync = new();
    private uint _lastSequence;
    private bool _hasSequence;

    public ClientSession(uint sessionId, IPAddress remoteAddress, string clientName)
    {
        SessionId = sessionId;
        RemoteAddress = remoteAddress;
        ClientName = clientName;
    }

    public uint SessionId { get; }

    public IPAddress RemoteAddress { get; }

    public string ClientName { get; }

    /// <summary>
    /// Accepts a UDP sequence number only if it is newer than every one accepted before,
    /// which drops duplicated and out-of-order datagrams. Wrap-around safe.
    /// </summary>
    public bool TryAcceptSequence(uint sequence)
    {
        lock (_sync)
        {
            if (_hasSequence && (int)(sequence - _lastSequence) <= 0)
            {
                return false;
            }

            _lastSequence = sequence;
            _hasSequence = true;
            return true;
        }
    }
}
