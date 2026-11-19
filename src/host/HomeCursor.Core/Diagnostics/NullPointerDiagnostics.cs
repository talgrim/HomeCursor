namespace HomeCursor.Core.Diagnostics;

/// <summary>Used when diagnostics are switched off.</summary>
public sealed class NullPointerDiagnostics : IPointerDiagnostics
{
    public static NullPointerDiagnostics Instance { get; } = new();

    private NullPointerDiagnostics()
    {
    }

    public void Record(PacketSample sample)
    {
    }
}
