namespace HomeCursor.Core.Updates;

/// <summary>An update could not be checked for, downloaded or applied.</summary>
public sealed class UpdateException : Exception
{
    public UpdateException(string message)
        : base(message)
    {
    }

    public UpdateException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
