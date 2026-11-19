using System.Collections.Concurrent;
using HomeCursor.Core.Diagnostics;

namespace HomeCursor.Core.Tests.Support;

internal sealed class RecordingPointerDiagnostics : IPointerDiagnostics
{
    private readonly ConcurrentQueue<PacketSample> _samples = new();

    public IReadOnlyCollection<PacketSample> Samples => _samples;

    public void Record(PacketSample sample) => _samples.Enqueue(sample);
}
