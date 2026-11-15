namespace HomeCursor.Core;

/// <summary>
/// A unit of background work owned by <see cref="IHostService"/>, for example a socket receive loop.
/// </summary>
public interface IHostLoop
{
    string Name { get; }

    /// <summary>
    /// Acquires resources (for example binds a socket) and starts working in the background.
    /// Throws when the resources cannot be acquired, in which case nothing is left running.
    /// </summary>
    Task StartAsync(CancellationToken cancellationToken);

    /// <summary>Stops the background work and releases its resources.</summary>
    Task StopAsync(CancellationToken cancellationToken);
}
