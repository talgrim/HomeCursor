namespace HomeCursor.Core.Updates;

public interface IUpdateInstaller
{
    /// <summary>
    /// Downloads <paramref name="update"/>, checks it and replaces the running executable on disk.
    /// The running process keeps working; the caller restarts the host to run the new version.
    /// </summary>
    /// <exception cref="UpdateException">The update could not be downloaded, did not match its checksum or could not be applied.</exception>
    Task InstallAsync(UpdateInfo update, CancellationToken cancellationToken = default);
}
