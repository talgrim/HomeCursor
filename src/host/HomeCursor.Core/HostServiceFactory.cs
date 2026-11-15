using HomeCursor.Core.Input;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core;

/// <summary>
/// Composition root for the host: wires the loops, session gate and dispatcher around a platform input injector.
/// </summary>
public static class HostServiceFactory
{
    public static IHostService Create(HostSettings settings, IInputInjector injector, ILoggerFactory loggerFactory)
    {
        var gate = new SessionGate(loggerFactory.CreateLogger<SessionGate>());
        var dispatcher = new InputMessageDispatcher(injector, loggerFactory.CreateLogger<InputMessageDispatcher>());

        IHostLoop[] loops =
        [
            new DiscoveryBeacon(settings, gate, loggerFactory.CreateLogger<DiscoveryBeacon>()),
            new UdpPointerReceiver(settings, gate, dispatcher, loggerFactory.CreateLogger<UdpPointerReceiver>()),
            new TcpControlListener(settings, gate, dispatcher, loggerFactory.CreateLogger<TcpControlListener>())
        ];

        return new HostService(loops, loggerFactory.CreateLogger<HostService>());
    }
}
