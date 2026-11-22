using HomeCursor.Core.Settings;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace HomeCursor.Core.Updates;

public static class UpdateServiceCollectionExtensions
{
    /// <summary>
    /// Registers the self-update services: the release check, the installer and the monitor that runs the
    /// checks on a schedule. Needs <see cref="Microsoft.Extensions.Options.IOptions{HostSettings}"/> and logging, like
    /// <see cref="ServiceCollectionExtensions.AddHomeCursorCore"/>.
    /// </summary>
    public static IServiceCollection AddHomeCursorUpdates(this IServiceCollection services, UpdateEnvironment environment)
    {
        services.TryAddSingleton(TimeProvider.System);
        services.AddSingleton(environment);
        services.AddSingleton<IUpdateStateStore, JsonFileUpdateStateStore>();

        // One shared client: it is only used for a manifest and one download now and then.
        services.AddSingleton(_ => CreateHttpClient(environment));

        services.AddSingleton<IUpdateChecker, ManifestUpdateChecker>();
        services.AddSingleton<IUpdateSignatureVerifier>(new EcdsaUpdateSignatureVerifier(ReleaseSigningKey.PublicKey));
        services.AddSingleton<IUpdateInstaller, ZipUpdateInstaller>();
        services.AddSingleton<UpdateMonitor>();
        services.AddSingleton<IUpdateMonitor>(sp => sp.GetRequiredService<UpdateMonitor>());

        return services;
    }

    private static HttpClient CreateHttpClient(UpdateEnvironment environment)
    {
        // The timeout covers receiving the response headers; the body of a download is streamed without a limit.
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd($"HomeCursor/{environment.CurrentVersion}");
        return client;
    }
}
