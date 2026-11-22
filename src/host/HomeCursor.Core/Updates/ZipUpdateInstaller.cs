using System.IO.Compression;
using System.Security.Cryptography;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Updates;

/// <summary>
/// Installs a release zip: downloads it, verifies its SHA-256, extracts the executable and swaps it in
/// with <see cref="ExecutableReplacer"/>. Everything else in the zip (settings, licence) is left alone,
/// so the user's settings survive.
/// </summary>
public sealed class ZipUpdateInstaller : IUpdateInstaller
{
    public const string ExecutableEntryName = "HomeCursor.exe";

    private readonly HttpClient _http;
    private readonly UpdateEnvironment _environment;
    private readonly ILogger<ZipUpdateInstaller> _logger;

    public ZipUpdateInstaller(HttpClient http, UpdateEnvironment environment, ILogger<ZipUpdateInstaller> logger)
    {
        _http = http;
        _environment = environment;
        _logger = logger;
    }

    public async Task InstallAsync(UpdateInfo update, CancellationToken cancellationToken = default)
    {
        var executablePath = _environment.ExecutablePath;
        if (string.Equals(Path.GetFileNameWithoutExtension(executablePath), "dotnet", StringComparison.OrdinalIgnoreCase))
        {
            throw new UpdateException("The host runs through the dotnet command, so there is no executable to replace.");
        }

        var workDirectory = Path.Combine(Path.GetTempPath(), "HomeCursor-update-" + Guid.NewGuid().ToString("N"));
        var newExecutablePath = executablePath + ".new";

        try
        {
            Directory.CreateDirectory(workDirectory);
            var zipPath = Path.Combine(workDirectory, "update.zip");

            await DownloadAsync(update, zipPath, cancellationToken).ConfigureAwait(false);
            await VerifyAsync(update, zipPath, cancellationToken).ConfigureAwait(false);
            Extract(zipPath, newExecutablePath);

            ExecutableReplacer.Replace(executablePath, newExecutablePath);
            _logger.LogInformation("Installed host {Version}, restart to run it", update.Version);
        }
        catch (Exception ex) when (ex is HttpRequestException or IOException or UnauthorizedAccessException
                                       or InvalidDataException)
        {
            throw new UpdateException($"Could not install the update: {ex.Message}", ex);
        }
        finally
        {
            TryDelete(newExecutablePath);
            TryDeleteDirectory(workDirectory);
        }
    }

    private async Task DownloadAsync(UpdateInfo update, string zipPath, CancellationToken cancellationToken)
    {
        _logger.LogInformation("Downloading {Uri}", update.DownloadUri);

        using var response = await _http
            .GetAsync(update.DownloadUri, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
            .ConfigureAwait(false);
        response.EnsureSuccessStatusCode();

        await using var source = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
        await using var target = File.Create(zipPath);
        await source.CopyToAsync(target, cancellationToken).ConfigureAwait(false);
    }

    private static async Task VerifyAsync(UpdateInfo update, string zipPath, CancellationToken cancellationToken)
    {
        await using var stream = File.OpenRead(zipPath);
        var hash = Convert.ToHexString(await SHA256.HashDataAsync(stream, cancellationToken).ConfigureAwait(false));

        if (!hash.Equals(update.Sha256, StringComparison.OrdinalIgnoreCase))
        {
            throw new UpdateException("The downloaded update does not match its checksum and was discarded.");
        }
    }

    private static void Extract(string zipPath, string destinationPath)
    {
        using var archive = ZipFile.OpenRead(zipPath);
        var entry = archive.Entries.FirstOrDefault(e =>
            e.FullName.Equals(ExecutableEntryName, StringComparison.OrdinalIgnoreCase));

        if (entry is null)
        {
            throw new UpdateException($"The update does not contain {ExecutableEntryName}.");
        }

        entry.ExtractToFile(destinationPath, overwrite: true);
    }

    private static void TryDelete(string path)
    {
        try
        {
            File.Delete(path);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Temporary file; leaving it behind is harmless.
        }
    }

    private static void TryDeleteDirectory(string path)
    {
        try
        {
            Directory.Delete(path, recursive: true);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Temporary directory; leaving it behind is harmless.
        }
    }
}
