using HomeCursor.Core.Input;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Tests;

public sealed class ServiceCollectionExtensionsTests
{
    private static ServiceProvider BuildProvider(HostSettings? settings = null)
    {
        var services = new ServiceCollection();
        services.AddLogging();
        services.AddSingleton<ISettingsProvider>(new StubSettingsProvider(settings ?? new HostSettings()));
        services.AddSingleton<IInputInjector, FakeInputInjector>();
        services.AddHomeCursorCore();

        return services.BuildServiceProvider(new ServiceProviderOptions { ValidateScopes = true, ValidateOnBuild = true });
    }

    [Fact]
    public void HostService_Resolves()
    {
        using var provider = BuildProvider();

        Assert.NotNull(provider.GetRequiredService<IHostService>());
    }

    [Fact]
    public void HostLoops_AreRegisteredInStartOrder()
    {
        using var provider = BuildProvider();

        var loops = provider.GetServices<IHostLoop>().Select(loop => loop.GetType()).ToArray();

        Assert.Equal([typeof(DiscoveryBeacon), typeof(UdpPointerReceiver), typeof(TcpControlListener)], loops);
    }

    [Fact]
    public void Options_ComeFromSettingsProvider()
    {
        var settings = new HostSettings { TcpPort = 12345 };
        using var provider = BuildProvider(settings);

        Assert.Equal(12345, provider.GetRequiredService<IOptions<HostSettings>>().Value.TcpPort);
    }

    [Fact]
    public void SessionGateAndMonitor_AreTheSameInstance()
    {
        using var provider = BuildProvider();

        Assert.Same(provider.GetRequiredService<ISessionGate>(), provider.GetRequiredService<ISessionMonitor>());
    }

    [Fact]
    public void SharedServices_AreSingletons()
    {
        using var provider = BuildProvider();

        Assert.Same(provider.GetRequiredService<IMessageDispatcher>(), provider.GetRequiredService<IMessageDispatcher>());
        Assert.Same(provider.GetRequiredService<IHostService>(), provider.GetRequiredService<IHostService>());
    }

    private sealed class StubSettingsProvider(HostSettings settings) : ISettingsProvider
    {
        public HostSettings Load() => settings;
    }
}
