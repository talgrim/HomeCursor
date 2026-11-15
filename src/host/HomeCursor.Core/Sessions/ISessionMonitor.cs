namespace HomeCursor.Core.Sessions;

/// <summary>
/// Read-only view of the client session for observers such as a UI. Events are raised on the thread that
/// acquired or released the session (usually a thread-pool thread), never while a lock is held.
/// A throwing subscriber cannot break the session.
/// </summary>
public interface ISessionMonitor
{
    /// <summary>The connected client, or <c>null</c> when no client is connected.</summary>
    ClientSession? Current { get; }

    event EventHandler<ClientSession>? SessionStarted;

    event EventHandler<SessionEndedEventArgs>? SessionEnded;
}
