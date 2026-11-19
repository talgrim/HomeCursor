using System.Diagnostics;
using System.Net;
using HomeCursor.Core.Diagnostics;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Tests.Support;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Diagnostics;

public sealed class PointerStatsReporterTests : IDisposable
{
    private readonly SessionGate _gate = new(NullLogger<SessionGate>.Instance);
    private readonly RecordingLogger<PointerStatsReporter> _logger = new();
    private readonly PointerStatsReporter _reporter;

    public PointerStatsReporterTests()
    {
        _reporter = new PointerStatsReporter(_gate, _logger);
    }

    public void Dispose() => _reporter.Dispose();

    private static long At(double milliseconds) => (long)(milliseconds * Stopwatch.Frequency / 1000.0);

    private void Applied(double atMilliseconds, int missed = 0, int backlog = 0, double injectMilliseconds = 0) =>
        _reporter.Record(new PacketSample(
            PacketOutcome.Applied, At(atMilliseconds), missed, backlog, TimeSpan.FromMilliseconds(injectMilliseconds)));

    [Fact]
    public void NothingIsLogged_BeforeTheWindowEnds()
    {
        Applied(0);
        Applied(8);

        Assert.Empty(_logger.Entries);
    }

    [Fact]
    public void WindowSummary_IsLoggedWhenTheNextWindowStarts()
    {
        Applied(0);
        Applied(8);
        Applied(16);
        Applied(1100);

        var entry = Assert.Single(_logger.Entries);
        Assert.Equal(LogLevel.Information, entry.Level);
        Assert.Contains("applied 3", entry.Message);
        Assert.Contains("missed 0", entry.Message);
    }

    [Fact]
    public void SteadyMotion_ReportsGapPercentiles()
    {
        for (var i = 0; i < 100; i++)
        {
            Applied(i * 8);
        }

        Applied(1100);

        Assert.Contains("gap ms p50/p95/p99/max 8.0/8.0/8.0/8.0", Assert.Single(_logger.Entries).Message);
    }

    [Fact]
    public void GapOfFiftyMillisecondsOrMore_IsAStallAndWarns()
    {
        Applied(0);
        Applied(8);
        Applied(108);
        Applied(1200);

        var entry = Assert.Single(_logger.Entries);
        Assert.Equal(LogLevel.Warning, entry.Level);
        Assert.Contains("stalls>50ms 1", entry.Message);
        Assert.Contains("/100.0", entry.Message);
    }

    [Fact]
    public void LongPause_IsNotAStallAndStaysOutOfTheGaps()
    {
        Applied(0);
        Applied(8);
        Applied(2000);
        Applied(2008);
        Applied(3200);

        Assert.All(_logger.Entries, entry => Assert.Equal(LogLevel.Information, entry.Level));
        Assert.Contains(_logger.Entries, entry => entry.Message.Contains("pauses 1"));
        Assert.DoesNotContain(_logger.Entries, entry => entry.Message.Contains("stalls>50ms 1"));
    }

    [Fact]
    public void MissedStaleAndRejectedPackets_AreCountedAndWarn()
    {
        Applied(0, missed: 2);
        _reporter.Record(new PacketSample(PacketOutcome.Stale, At(10)));
        _reporter.Record(new PacketSample(PacketOutcome.Rejected, At(20)));
        Applied(1100);

        var entry = Assert.Single(_logger.Entries);
        Assert.Equal(LogLevel.Warning, entry.Level);
        Assert.Contains("missed 2", entry.Message);
        Assert.Contains("stale 1", entry.Message);
        Assert.Contains("rejected 1", entry.Message);
    }

    [Fact]
    public void SlowInjectionAndBacklog_AreReportedAsMaximum()
    {
        Applied(0, backlog: 40, injectMilliseconds: 1);
        Applied(8, backlog: 120, injectMilliseconds: 7.5);
        Applied(1100);

        var entry = Assert.Single(_logger.Entries);
        Assert.Equal(LogLevel.Warning, entry.Level);
        Assert.Contains("inject ms max 7.50", entry.Message);
        Assert.Contains("backlog bytes max 120", entry.Message);
    }

    [Fact]
    public void SessionEnd_LogsTheRestOfTheWindowAndTheTotals()
    {
        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "phone", out var session));
        Applied(0);
        Applied(8, missed: 1);
        Applied(1100);
        Applied(1108);

        _gate.Release(session, SessionEndReason.ConnectionLost);

        var totals = _logger.Entries[^1].Message;
        Assert.Contains("'phone'", totals);
        Assert.Contains("ConnectionLost", totals);
        Assert.Contains("applied 4", totals);
        Assert.Contains("missed 1", totals);
    }

    [Fact]
    public void NewSession_StartsFromZero()
    {
        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "first", out var first));
        Applied(0);
        _gate.Release(first, SessionEndReason.ClientDisconnected);
        _logger.Entries.Clear();

        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "second", out var second));
        Applied(5000);
        _gate.Release(second, SessionEndReason.ClientDisconnected);

        Assert.Contains("applied 1", _logger.Entries[^1].Message);
    }

    [Fact]
    public void AfterDispose_SessionEventsAreIgnored()
    {
        _reporter.Dispose();
        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "phone", out var session));

        _gate.Release(session, SessionEndReason.ClientDisconnected);

        Assert.Empty(_logger.Entries);
    }
}
