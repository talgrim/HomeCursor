using HomeCursor.Core.Diagnostics;
using HomeCursor.Core.Input;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core;

public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Registers the platform-independent host services: session gate and monitor, message dispatcher, the host loops and <see cref="IHostService"/>.
    /// The caller must also register <see cref="IInputInjector"/>, logging and either <see cref="ISettingsProvider"/>,
    /// an <see cref="ISettingsHolder"/> or <see cref="IOptions{HostSettings}"/> itself.
    /// </summary>
    public static IServiceCollection AddHomeCursorCore(this IServiceCollection services)
    {
        services.TryAddSingleton<ISettingsHolder>(
            sp => new SettingsHolder(sp.GetRequiredService<ISettingsProvider>().Load()));
        services.TryAddSingleton<IOptions<HostSettings>>(sp => sp.GetRequiredService<ISettingsHolder>());

        services.AddSingleton<SessionGate>();
        services.AddSingleton<ISessionGate>(sp => sp.GetRequiredService<SessionGate>());
        services.AddSingleton<ISessionMonitor>(sp => sp.GetRequiredService<SessionGate>());
        services.AddSingleton<IMessageDispatcher, InputMessageDispatcher>();
        services.AddSingleton<IPointerDiagnostics>(sp =>
            sp.GetRequiredService<IOptions<HostSettings>>().Value.DiagnosticsEnabled
                ? new PointerStatsReporter(
                    sp.GetRequiredService<ISessionMonitor>(), sp.GetRequiredService<ILogger<PointerStatsReporter>>())
                : NullPointerDiagnostics.Instance);

        // Registration order is the start order; the host service stops the loops in reverse.
        services.AddSingleton<IHostLoop, DiscoveryBeacon>();
        services.AddSingleton<IHostLoop, UdpPointerReceiver>();
        services.AddSingleton<IHostLoop, TcpControlListener>();

        services.AddSingleton<IHostService, HostService>();
        services.AddSingleton<ISettingsApplier, SettingsApplier>();

        return services;
    }
}
