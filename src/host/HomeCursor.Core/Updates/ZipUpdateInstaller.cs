using System.IO.Compression;
using System.Security.Cryptography;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Updates;

/// <summary>
/// Installs a release zip: downloads it, verifies its SHA-256 and its signature (<see cref="IUpdateSignatureVerifier"/>),
/// extracts the executable and swaps it in with <see cref="ExecutableReplacer"/>. Everything else in the zip (settings, licence) is left alone,
/// so the user's settings survive.
/// </summary>
public sealed class ZipUpdateInstaller : IUpdateInstaller
{
    public const string ExecutableEntryName = "HomeCursor.exe";

    // A signature is a few dozen bytes; anything bigger is not one.
    private const int MaxSignatureBytes = 1024;

    private readonly HttpClient _http;
    private readonly UpdateEnvironment _environment;
    private readonly IUpdateSignatureVerifier _signatures;
    private readonly ILogger<ZipUpdateInstaller> _logger;

    public ZipUpdateInstaller(
        HttpClient http,
        UpdateEnvironment environment,
        IUpdateSignatureVerifier signatures,
        ILogger<ZipUpdateInstaller> logger)
    {
        _http = http;
        _environment = environment;
        _signatures = signatures;
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
            var sha256 = await VerifyChecksumAsync(update, zipPath, cancellationToken).ConfigureAwait(false);
            await VerifySignatureAsync(update, sha256, cancellationToken).ConfigureAwait(false);
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

    /// <returns>The SHA-256 of the zip as it was downloaded.</returns>
    private static async Task<string> VerifyChecksumAsync(UpdateInfo update, string zipPath, CancellationToken cancellationToken)
    {
        await using var stream = File.OpenRead(zipPath);
        var hash = Convert.ToHexString(await SHA256.HashDataAsync(stream, cancellationToken).ConfigureAwait(false));

        if (!hash.Equals(update.Sha256, StringComparison.OrdinalIgnoreCase))
        {
            throw new UpdateException("The downloaded update does not match its checksum and was discarded.");
        }

        return hash;
    }

    /// <summary>
    /// The checksum comes from the same server as the zip, so it proves nothing about who made the zip. The
    /// signature does: only the holder of the release key can make one that fits.
    /// </summary>
    private async Task VerifySignatureAsync(UpdateInfo update, string sha256, CancellationToken cancellationToken)
    {
        byte[] signature;
        try
        {
            using var response = await _http.GetAsync(update.SignatureUri, cancellationToken).ConfigureAwait(false);
            response.EnsureSuccessStatusCode();
            var text = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
            if (text.Length > MaxSignatureBytes)
            {
                throw new FormatException("The signature is too large.");
            }

            signature = Convert.FromBase64String(text.Trim());
        }
        catch (Exception ex) when (ex is HttpRequestException or FormatException)
        {
            throw new UpdateException("The update is not signed and was discarded.", ex);
        }

        if (!_signatures.IsValid(update.Version, sha256, signature))
        {
            throw new UpdateException("The update's signature is not valid and it was discarded.");
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
