using System.Diagnostics.CodeAnalysis;
using System.Net;

namespace HomeCursor.Core.Sessions;

/// <summary>
/// Allows a single client at a time: the first client to acquire the gate wins and every other client
/// is refused until the session is released.
/// </summary>
public interface ISessionGate
{
    bool IsBusy { get; }

    bool TryAcquire(IPAddress remoteAddress, string clientName, [NotNullWhen(true)] out ClientSession? session);

    void Release(ClientSession session);

    /// <summary>Returns the active session if it matches the given id and sender address.</summary>
    ClientSession? FindActive(uint sessionId, IPAddress remoteAddress);
}
