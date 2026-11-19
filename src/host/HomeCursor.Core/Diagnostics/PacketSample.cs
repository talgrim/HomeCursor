namespace HomeCursor.Core.Diagnostics;

/// <summary>One received pointer datagram, as seen by <see cref="IPointerDiagnostics"/>.</summary>
/// <param name="Outcome">What happened to the datagram.</param>
/// <param name="Timestamp">When the receive loop got it (<see cref="System.Diagnostics.Stopwatch.GetTimestamp"/>).</param>
/// <param name="MissedBefore">How many datagrams the sequence numbers show as lost just before this one.</param>
/// <param name="BacklogBytes">Bytes still waiting in the socket buffer after this datagram was read.</param>
/// <param name="InjectTime">How long the dispatcher took to apply the datagram.</param>
public readonly record struct PacketSample(
    PacketOutcome Outcome,
    long Timestamp,
    int MissedBefore = 0,
    int BacklogBytes = 0,
    TimeSpan InjectTime = default);
