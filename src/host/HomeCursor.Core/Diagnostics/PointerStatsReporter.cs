using System.Diagnostics;
using System.Globalization;
using HomeCursor.Core.Sessions;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Diagnostics;

/// <summary>
/// Sums up the pointer datagrams of a session and logs one line per second of activity, plus the totals when the
/// session ends. The summary shows where lag comes from: the gaps between datagrams (network or phone), lost and late
/// datagrams (network), time spent injecting input and the socket backlog (host).
/// </summary>
public sealed class PointerStatsReporter : IPointerDiagnostics, IDisposable
{
    /// <summary>A gap between two applied datagrams at least this long counts as a stall.</summary>
    private const double StallMilliseconds = 50;

    /// <summary>A longer gap is a pause (finger lifted), not motion, and stays out of the gap percentiles.</summary>
    private const double PauseMilliseconds = 500;

    private const double SlowInjectMilliseconds = 5;

    private static readonly TimeSpan WindowLength = TimeSpan.FromSeconds(1);

    private readonly object _sync = new();
    private readonly ISessionMonitor _monitor;
    private readonly ILogger<PointerStatsReporter> _logger;
    private readonly List<double> _gaps = [];
    private Tally _window = new();
    private Tally _total = new();
    private bool _hasWindow;
    private long _windowStart;
    private long _windowEnd;
    private bool _hasLastApplied;
    private long _lastApplied;

    public PointerStatsReporter(ISessionMonitor monitor, ILogger<PointerStatsReporter> logger)
    {
        _monitor = monitor;
        _logger = logger;
        _monitor.SessionStarted += OnSessionStarted;
        _monitor.SessionEnded += OnSessionEnded;
    }

    public void Record(PacketSample sample)
    {
        lock (_sync)
        {
            if (!_hasWindow)
            {
                _hasWindow = true;
                _windowStart = sample.Timestamp;
            }
            else if (Stopwatch.GetElapsedTime(_windowStart, sample.Timestamp) >= WindowLength)
            {
                FlushWindow();
                _windowStart = sample.Timestamp;
            }

            _windowEnd = sample.Timestamp;
            _window.Add(sample);

            if (sample.Outcome == PacketOutcome.Applied)
            {
                MeasureGap(sample.Timestamp);
            }
        }
    }

    public void Dispose()
    {
        _monitor.SessionStarted -= OnSessionStarted;
        _monitor.SessionEnded -= OnSessionEnded;
    }

    private void MeasureGap(long timestamp)
    {
        if (_hasLastApplied)
        {
            var gap = Stopwatch.GetElapsedTime(_lastApplied, timestamp).TotalMilliseconds;
            if (gap >= PauseMilliseconds)
            {
                _window.Pauses++;
            }
            else
            {
                _gaps.Add(gap);
                if (gap >= StallMilliseconds)
                {
                    _window.Stalls++;
                }
            }
        }

        _lastApplied = timestamp;
        _hasLastApplied = true;
    }

    private void FlushWindow()
    {
        if (_window.HasData)
        {
            var span = Stopwatch.GetElapsedTime(_windowStart, _windowEnd).TotalSeconds;
            var rate = span > 0 ? _window.Applied / span : 0;

            _gaps.Sort();
            _logger.Log(
                _window.HasAnomaly ? LogLevel.Warning : LogLevel.Information,
                "Pointer {Span:0.00}s: applied {Applied} ({Rate:0}/s), missed {Missed}, stale {Stale}, rejected {Rejected}, "
                + "undecodable {Undecodable}, gap ms p50/p95/p99/max {Gaps}, stalls>{StallMs}ms {Stalls}, pauses {Pauses}, "
                + "inject ms max {InjectMax:0.00}, backlog bytes max {Backlog}",
                span, _window.Applied, rate, _window.Missed, _window.Stale, _window.Rejected,
                _window.Undecodable, FormatGaps(), StallMilliseconds, _window.Stalls, _window.Pauses,
                _window.MaxInjectMilliseconds, _window.MaxBacklogBytes);

            _total.Merge(_window);
        }

        _window = new Tally();
        _gaps.Clear();
    }

    private string FormatGaps()
    {
        if (_gaps.Count == 0)
        {
            return "n/a";
        }

        return string.Create(
            CultureInfo.InvariantCulture,
            $"{Percentile(0.50):0.0}/{Percentile(0.95):0.0}/{Percentile(0.99):0.0}/{_gaps[^1]:0.0}");
    }

    /// <summary>Nearest-rank percentile of the sorted gaps.</summary>
    private double Percentile(double fraction) =>
        _gaps[Math.Max(0, (int)Math.Ceiling(fraction * _gaps.Count) - 1)];

    private void OnSessionStarted(object? sender, ClientSession session)
    {
        lock (_sync)
        {
            Reset();
        }
    }

    private void OnSessionEnded(object? sender, SessionEndedEventArgs e)
    {
        lock (_sync)
        {
            FlushWindow();

            _logger.LogInformation(
                "Session {SessionId} of '{ClientName}' ended ({Reason}); pointer totals: applied {Applied}, missed {Missed}, "
                + "stale {Stale}, rejected {Rejected}, undecodable {Undecodable}, stalls>{StallMs}ms {Stalls}, "
                + "inject ms max {InjectMax:0.00}, backlog bytes max {Backlog}",
                e.Session.SessionId, e.Session.ClientName, e.Reason, _total.Applied, _total.Missed,
                _total.Stale, _total.Rejected, _total.Undecodable, StallMilliseconds, _total.Stalls,
                _total.MaxInjectMilliseconds, _total.MaxBacklogBytes);

            Reset();
        }
    }

    private void Reset()
    {
        _window = new Tally();
        _total = new Tally();
        _gaps.Clear();
        _hasWindow = false;
        _hasLastApplied = false;
    }

    private sealed class Tally
    {
        public long Applied;
        public long Undecodable;
        public long NotPointer;
        public long Rejected;
        public long Stale;
        public long Missed;
        public long Stalls;
        public long Pauses;
        public int MaxBacklogBytes;
        public double MaxInjectMilliseconds;

        public bool HasData =>
            Applied + Undecodable + NotPointer + Rejected + Stale > 0;

        public bool HasAnomaly =>
            Missed > 0 || Stale > 0 || Rejected > 0 || Stalls > 0 || MaxInjectMilliseconds >= SlowInjectMilliseconds;

        public void Add(PacketSample sample)
        {
            switch (sample.Outcome)
            {
                case PacketOutcome.Applied:
                    Applied++;
                    break;
                case PacketOutcome.Undecodable:
                    Undecodable++;
                    break;
                case PacketOutcome.NotPointer:
                    NotPointer++;
                    break;
                case PacketOutcome.Rejected:
                    Rejected++;
                    break;
                case PacketOutcome.Stale:
                    Stale++;
                    break;
            }

            Missed += sample.MissedBefore;
            MaxBacklogBytes = Math.Max(MaxBacklogBytes, sample.BacklogBytes);
            MaxInjectMilliseconds = Math.Max(MaxInjectMilliseconds, sample.InjectTime.TotalMilliseconds);
        }

        public void Merge(Tally other)
        {
            Applied += other.Applied;
            Undecodable += other.Undecodable;
            NotPointer += other.NotPointer;
            Rejected += other.Rejected;
            Stale += other.Stale;
            Missed += other.Missed;
            Stalls += other.Stalls;
            Pauses += other.Pauses;
            MaxBacklogBytes = Math.Max(MaxBacklogBytes, other.MaxBacklogBytes);
            MaxInjectMilliseconds = Math.Max(MaxInjectMilliseconds, other.MaxInjectMilliseconds);
        }
    }
}
