namespace HomeCursor.Core.Diagnostics;

/// <summary>
/// Observes the stream of pointer datagrams to help diagnose lag. Called on the receive loop for every datagram,
/// so implementations must be cheap.
/// </summary>
public interface IPointerDiagnostics
{
    void Record(PacketSample sample);
}
