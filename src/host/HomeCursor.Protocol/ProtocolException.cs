namespace HomeCursor.Protocol;

/// <summary>
/// Raised when received bytes do not form a valid protocol message.
/// </summary>
public sealed class ProtocolException : Exception
{
    public ProtocolException(string message)
        : base(message)
    {
    }

    public ProtocolException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
