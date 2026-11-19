namespace HomeCursor.Core.Sessions;

public sealed class SessionEndedEventArgs(ClientSession session, SessionEndReason reason) : EventArgs
{
    public ClientSession Session { get; } = session;

    public SessionEndReason Reason { get; } = reason;
}
