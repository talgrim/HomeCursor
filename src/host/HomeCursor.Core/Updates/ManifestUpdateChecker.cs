using System.Text.Json;
using HomeCursor.Core.Settings;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Updates;

/// <summary>Looks for a newer host release in the release manifest.</summary>
public sealed class ManifestUpdateChecker : IUpdateChecker
{
    public const string Product = "homecursor-host";
    public const string Suffix = "-win-x64.zip";

    private readonly HttpClient _http;
    private readonly HostSettings _settings;
    private readonly UpdateEnvironment _environment;
    private readonly ILogger<ManifestUpdateChecker> _logger;

    public ManifestUpdateChecker(
        HttpClient http,
        IOptions<HostSettings> settings,
        UpdateEnvironment environment,
        ILogger<ManifestUpdateChecker> logger)
    {
        _http = http;
        _settings = settings.Value;
        _environment = environment;
        _logger = logger;
    }

    public async Task<UpdateInfo?> CheckAsync(CancellationToken cancellationToken = default)
    {
        var manifestUri = new Uri(_settings.UpdateManifestUrl);

        UpdateInfo? latest;
        try
        {
            var json = await _http.GetStringAsync(manifestUri, cancellationToken).ConfigureAwait(false);
            latest = UpdateManifestParser.FindLatest(json, manifestUri, Product, Suffix);
        }
        catch (Exception ex) when (ex is HttpRequestException or JsonException or TaskCanceledException
                                       && !cancellationToken.IsCancellationRequested)
        {
            throw new UpdateException($"Could not read the release manifest: {ex.Message}", ex);
        }

        if (latest is null)
        {
            _logger.LogWarning("The release manifest lists no {Product} release", Product);
            return null;
        }

        if (!UpdateVersions.IsNewer(latest.Version, _environment.CurrentVersion))
        {
            _logger.LogInformation(
                "Host {Current} is up to date (latest release {Latest})", _environment.CurrentVersion, latest.Version);
            return null;
        }

        _logger.LogInformation("Host update available: {Current} -> {Latest}", _environment.CurrentVersion, latest.Version);
        return latest;
    }
}
