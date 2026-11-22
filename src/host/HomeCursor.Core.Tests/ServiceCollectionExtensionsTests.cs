using HomeCursor.Core.Diagnostics;
using HomeCursor.Core.Input;
using HomeCursor.Core.Networking;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Core.Updates;
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
        services.AddHomeCursorUpdates(new UpdateEnvironment(new Version(1, 3, 0), "HomeCursor.exe", "state.json"));

        return services.BuildServiceProvider(new ServiceProviderOptions { ValidateScopes = true, ValidateOnBuild = true });
    }

    [Fact]
    public void HostService_Resolves()
    {
        using var provider = BuildProvider();

        Assert.NotNull(provider.GetRequiredService<IHostService>());
    }

    [Fact]
    public void UpdateServices_Resolve()
    {
        using var provider = BuildProvider();

        Assert.IsType<ManifestUpdateChecker>(provider.GetRequiredService<IUpdateChecker>());
        Assert.IsType<ZipUpdateInstaller>(provider.GetRequiredService<IUpdateInstaller>());
        Assert.Same(provider.GetRequiredService<UpdateMonitor>(), provider.GetRequiredService<IUpdateMonitor>());
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
    public void PointerDiagnostics_AreOffByDefault()
    {
        using var provider = BuildProvider();

        Assert.IsType<NullPointerDiagnostics>(provider.GetRequiredService<IPointerDiagnostics>());
    }

    [Fact]
    public void PointerDiagnostics_WhenEnabled_ReportStats()
    {
        using var provider = BuildProvider(new HostSettings { DiagnosticsEnabled = true });

        Assert.IsType<PointerStatsReporter>(provider.GetRequiredService<IPointerDiagnostics>());
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

        public void Save(HostSettings settingsToSave)
        {
        }
    }
}
