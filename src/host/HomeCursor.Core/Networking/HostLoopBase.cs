using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Networking;

/// <summary>
/// Template for a background loop: binds on start, repeats <see cref="RunIterationAsync"/> until stopped,
/// and survives errors inside one iteration.
/// </summary>
public abstract class HostLoopBase : IHostLoop
{
    private static readonly TimeSpan ErrorBackoff = TimeSpan.FromMilliseconds(250);

    private CancellationTokenSource? _cts;
    private Task? _runTask;

    protected HostLoopBase(ILogger logger)
    {
        Logger = logger;
    }

    public abstract string Name { get; }

    protected ILogger Logger { get; }

    public Task StartAsync(CancellationToken cancellationToken)
    {
        if (_runTask is not null)
        {
            throw new InvalidOperationException($"{Name} is already started.");
        }

        Bind();

        _cts = new CancellationTokenSource();
        var token = _cts.Token;
        _runTask = Task.Run(() => RunAsync(token), CancellationToken.None);
        return Task.CompletedTask;
    }

    public async Task StopAsync(CancellationToken cancellationToken)
    {
        if (_cts is null || _runTask is null)
        {
            return;
        }

        await _cts.CancelAsync().ConfigureAwait(false);
        try
        {
            await _runTask.WaitAsync(cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            await UnbindAsync().ConfigureAwait(false);
            _cts.Dispose();
            _cts = null;
            _runTask = null;
        }
    }

    /// <summary>Acquires resources. Must throw without leaking anything when that is impossible.</summary>
    protected abstract void Bind();

    /// <summary>Performs one unit of work, for example accepts one connection or receives one datagram.</summary>
    protected abstract Task RunIterationAsync(CancellationToken cancellationToken);

    /// <summary>Releases resources after the loop has stopped.</summary>
    protected abstract Task UnbindAsync();

    private async Task RunAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                await RunIterationAsync(cancellationToken).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception ex)
            {
                Logger.LogError(ex, "{Loop} failed, retrying", Name);
                try
                {
                    await Task.Delay(ErrorBackoff, cancellationToken).ConfigureAwait(false);
                }
                catch (OperationCanceledException)
                {
                    return;
                }
            }
        }
    }
}
