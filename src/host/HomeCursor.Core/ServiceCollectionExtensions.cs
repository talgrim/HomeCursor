using HomeCursor.Core.Input;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core;

public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Registers the platform-independent host services: session gate and monitor, message dispatcher, the host loops and <see cref="IHostService"/>.
    /// The caller must also register <see cref="IInputInjector"/>, <see cref="ISettingsProvider"/> and logging.
    /// </summary>
    public static IServiceCollection AddHomeCursorCore(this IServiceCollection services)
    {
        services.TryAddSingleton<IOptions<HostSettings>>(
            sp => Options.Create(sp.GetRequiredService<ISettingsProvider>().Load()));

        services.AddSingleton<SessionGate>();
        services.AddSingleton<ISessionGate>(sp => sp.GetRequiredService<SessionGate>());
        services.AddSingleton<ISessionMonitor>(sp => sp.GetRequiredService<SessionGate>());
        services.AddSingleton<IMessageDispatcher, InputMessageDispatcher>();

        // Registration order is the start order; the host service stops the loops in reverse.
        services.AddSingleton<IHostLoop, DiscoveryBeacon>();
        services.AddSingleton<IHostLoop, UdpPointerReceiver>();
        services.AddSingleton<IHostLoop, TcpControlListener>();

        services.AddSingleton<IHostService, HostService>();

        return services;
    }
}
