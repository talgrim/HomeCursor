using HomeCursor.Core.Sessions;

namespace HomeCursor.Host.Tray;

internal static class SessionEndReasonText
{
    public static string Describe(this SessionEndReason reason) => reason switch
    {
        SessionEndReason.ClientDisconnected => "The client disconnected.",
        SessionEndReason.TimedOut => "The client stopped responding.",
        SessionEndReason.ConnectionLost => "The connection was lost.",
        SessionEndReason.ProtocolError => "The client sent invalid data.",
        SessionEndReason.HostStopping => "The host is shutting down.",
        SessionEndReason.Error => "An unexpected error occurred.",
        _ => "The session ended."
    };
}
