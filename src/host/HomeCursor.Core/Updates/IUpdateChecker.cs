namespace HomeCursor.Core.Updates;

public interface IUpdateChecker
{
    /// <summary>Asks the release server for the newest host version.</summary>
    /// <returns>The newer release, or <c>null</c> when the running host is up to date.</returns>
    /// <exception cref="UpdateException">The release server could not be reached or answered with something unusable.</exception>
    Task<UpdateInfo?> CheckAsync(CancellationToken cancellationToken = default);
}
