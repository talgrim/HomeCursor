using HomeCursor.Core.Settings;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Updates;

public sealed class UpdateMonitor : IUpdateMonitor, IDisposable
{
    public static readonly TimeSpan DefaultCheckInterval = TimeSpan.FromHours(24);
    public static readonly TimeSpan DefaultRetryInterval = TimeSpan.FromHours(1);

    private readonly IUpdateChecker _checker;
    private readonly IUpdateStateStore _store;
    private readonly IOptions<HostSettings> _options;
    private readonly ILogger<UpdateMonitor> _logger;
    private readonly TimeProvider _time;
    private readonly TimeSpan _checkInterval;
    private readonly TimeSpan _retryInterval;
    private readonly CancellationTokenSource _stop = new();
    private readonly object _gate = new();
    private Task? _loop;
    private int _disposed;

    public UpdateMonitor(
        IUpdateChecker checker,
        IUpdateStateStore store,
        IOptions<HostSettings> settings,
        ILogger<UpdateMonitor> logger,
        TimeProvider? time = null,
        TimeSpan? checkInterval = null,
        TimeSpan? retryInterval = null)
    {
        _checker = checker;
        _store = store;
        _options = settings;
        _logger = logger;
        _time = time ?? TimeProvider.System;
        _checkInterval = checkInterval ?? DefaultCheckInterval;
        _retryInterval = retryInterval ?? DefaultRetryInterval;
    }

    public UpdateInfo? Available { get; private set; }

    public event EventHandler<UpdateInfo>? UpdateFound;

    public void Start()
    {
        if (!_options.Value.CheckForUpdates)
        {
            _logger.LogInformation("Update checks are switched off");
            return;
        }

        lock (_gate)
        {
            _loop ??= Task.Run(() => RunAsync(_stop.Token));
        }
    }

    public async Task<UpdateInfo?> CheckNowAsync(CancellationToken cancellationToken = default)
    {
        var update = await _checker.CheckAsync(cancellationToken).ConfigureAwait(false);
        _store.SaveLastCheck(_time.GetUtcNow());

        var previous = Available;
        Available = update;
        if (update is not null && update.Version != previous?.Version)
        {
            UpdateFound?.Invoke(this, update);
        }

        return update;
    }

    public void Dispose()
    {
        // The container disposes a singleton registered under two service types once per registration.
        if (Interlocked.Exchange(ref _disposed, 1) == 0)
        {
            _stop.Cancel();
            _stop.Dispose();
        }
    }

    private async Task RunAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (true)
            {
                var wait = (_store.LastCheck ?? DateTimeOffset.MinValue) + _checkInterval - _time.GetUtcNow();
                if (wait > TimeSpan.Zero)
                {
                    await Task.Delay(wait, _time, cancellationToken).ConfigureAwait(false);
                }

                try
                {
                    await CheckNowAsync(cancellationToken).ConfigureAwait(false);
                }
                catch (UpdateException ex)
                {
                    // Typically no network at start-up. Try again sooner than the regular interval.
                    _logger.LogInformation("Update check failed: {Message}", ex.Message);
                    await Task.Delay(_retryInterval, _time, cancellationToken).ConfigureAwait(false);
                }
            }
        }
        catch (OperationCanceledException)
        {
            // Disposed.
        }
    }
}
