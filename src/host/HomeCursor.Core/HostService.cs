using Microsoft.Extensions.Logging;

namespace HomeCursor.Core;

/// <summary>
/// Starts and stops the configured <see cref="IHostLoop"/>s and reports the resulting <see cref="HostStatus"/>.
/// </summary>
public sealed class HostService : IHostService
{
    private readonly IReadOnlyList<IHostLoop> _loops;
    private readonly ILogger<HostService> _logger;
    private readonly SemaphoreSlim _lifecycleLock = new(1, 1);
    private readonly List<IHostLoop> _startedLoops = [];
    private volatile HostStatus _status = HostStatus.Stopped;

    public HostService(IEnumerable<IHostLoop> loops, ILogger<HostService> logger)
    {
        _loops = loops.ToList();
        _logger = logger;
    }

    public HostStatus Status => _status;

    public event EventHandler<HostStatus>? StatusChanged;

    public async Task StartAsync(CancellationToken cancellationToken = default)
    {
        await _lifecycleLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (_status == HostStatus.Running)
            {
                return;
            }

            SetStatus(HostStatus.Starting);

            try
            {
                foreach (var loop in _loops)
                {
                    await loop.StartAsync(cancellationToken).ConfigureAwait(false);
                    _startedLoops.Add(loop);
                    _logger.LogInformation("Started {Loop}", loop.Name);
                }
            }
            catch (OperationCanceledException)
            {
                await StopStartedLoopsAsync().ConfigureAwait(false);
                SetStatus(HostStatus.Stopped);
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Host failed to start");
                await StopStartedLoopsAsync().ConfigureAwait(false);
                SetStatus(HostStatus.Faulted);
                throw;
            }

            SetStatus(HostStatus.Running);
        }
        finally
        {
            _lifecycleLock.Release();
        }
    }

    public async Task StopAsync(CancellationToken cancellationToken = default)
    {
        await _lifecycleLock.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            await StopStartedLoopsAsync().ConfigureAwait(false);
            SetStatus(HostStatus.Stopped);
        }
        finally
        {
            _lifecycleLock.Release();
        }
    }

    private async Task StopStartedLoopsAsync()
    {
        for (var i = _startedLoops.Count - 1; i >= 0; i--)
        {
            var loop = _startedLoops[i];
            try
            {
                await loop.StopAsync(CancellationToken.None).ConfigureAwait(false);
                _logger.LogInformation("Stopped {Loop}", loop.Name);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to stop {Loop}", loop.Name);
            }
        }

        _startedLoops.Clear();
    }

    private void SetStatus(HostStatus status)
    {
        if (_status == status)
        {
            return;
        }

        _status = status;
        StatusChanged?.Invoke(this, status);
    }
}
