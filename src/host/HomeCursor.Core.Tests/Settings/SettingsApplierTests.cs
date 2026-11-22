using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Settings;

public sealed class SettingsApplierTests
{
    private readonly HostSettings _initial = new() { TcpPort = 6001, UdpPort = 6002, DiscoveryPort = 6003 };
    private readonly SettingsHolder _holder;
    private readonly FakeSettingsProvider _provider = new();
    private readonly FakeHostService _host = new();
    private readonly SettingsApplier _applier;

    public SettingsApplierTests()
    {
        _holder = new SettingsHolder(_initial);
        _applier = new SettingsApplier(_holder, _provider, _host, NullLogger<SettingsApplier>.Instance);
    }

    [Fact]
    public async Task ApplyAsync_InvalidSettings_ThrowsAndChangesNothing()
    {
        var invalid = _initial with { TcpPort = 0 };

        await Assert.ThrowsAsync<SettingsException>(() => _applier.ApplyAsync(invalid));

        Assert.Same(_initial, _holder.Value);
        Assert.Empty(_provider.Saved);
        Assert.Empty(_host.Journal);
    }

    [Fact]
    public async Task ApplyAsync_NoNetworkChange_UpdatesAndSavesWithoutRestartingTheHost()
    {
        var updated = _initial with { HostName = "Study", NotifyOnClientConnection = false, CheckForUpdates = false };

        var result = await _applier.ApplyAsync(updated);

        Assert.Same(updated, _holder.Value);
        Assert.Same(updated, Assert.Single(_provider.Saved));
        Assert.Empty(_host.Journal);
        Assert.True(result.Saved);
        Assert.False(result.AppRestartRequired);
    }

    [Theory]
    [InlineData(nameof(HostSettings.TcpPort))]
    [InlineData(nameof(HostSettings.UdpPort))]
    [InlineData(nameof(HostSettings.DiscoveryPort))]
    public async Task ApplyAsync_PortChanged_RestartsTheHostWithTheNewSettings(string port)
    {
        var updated = port switch
        {
            nameof(HostSettings.TcpPort) => _initial with { TcpPort = 7001 },
            nameof(HostSettings.UdpPort) => _initial with { UdpPort = 7002 },
            _ => _initial with { DiscoveryPort = 7003 }
        };

        await _applier.ApplyAsync(updated);

        Assert.Equal(["stop", "start"], _host.Journal);
        Assert.Same(updated, _holder.Value);
        Assert.Same(updated, Assert.Single(_provider.Saved));
    }

    [Fact]
    public async Task ApplyAsync_HostCannotStartOnNewPort_GoesBackToPreviousSettingsAndSavesNothing()
    {
        _host.StartFailures.Enqueue(new InvalidOperationException("port in use"));
        var updated = _initial with { TcpPort = 7001 };

        var ex = await Assert.ThrowsAsync<SettingsException>(() => _applier.ApplyAsync(updated));

        Assert.Contains("port in use", ex.Message);
        Assert.Same(_initial, _holder.Value);
        Assert.Equal(["stop", "start", "start"], _host.Journal);
        Assert.Equal(HostStatus.Running, _host.Status);
        Assert.Empty(_provider.Saved);
    }

    [Fact]
    public async Task ApplyAsync_HostHadFaulted_StartsItAgain()
    {
        _host.Status = HostStatus.Faulted;

        await _applier.ApplyAsync(_initial with { HostName = "Study" });

        Assert.Equal(["stop", "start"], _host.Journal);
    }

    [Fact]
    public async Task ApplyAsync_DiagnosticsSwitched_RequiresAppRestart()
    {
        var result = await _applier.ApplyAsync(_initial with { DiagnosticsEnabled = true });

        Assert.True(result.AppRestartRequired);
    }

    [Fact]
    public async Task ApplyAsync_LogFolderChanged_RequiresAppRestart()
    {
        var result = await _applier.ApplyAsync(_initial with { DiagnosticsLogDirectory = @"C:\Logs" });

        Assert.True(result.AppRestartRequired);
    }

    [Fact]
    public async Task ApplyAsync_LogFolderNullBecomesEmpty_DoesNotRequireAppRestart()
    {
        var result = await _applier.ApplyAsync(_initial with { DiagnosticsLogDirectory = "  " });

        Assert.False(result.AppRestartRequired);
    }

    [Fact]
    public async Task ApplyAsync_SaveFails_KeepsSettingsInEffectAndReportsTheError()
    {
        _provider.SaveFailure = new SettingsException("read-only");
        var updated = _initial with { HostName = "Study" };

        var result = await _applier.ApplyAsync(updated);

        Assert.Same(updated, _holder.Value);
        Assert.False(result.Saved);
        Assert.Equal("read-only", result.SaveError);
    }

    [Fact]
    public async Task ApplyAsync_RaisesChangedOncePerSuccessfulApply()
    {
        var raised = 0;
        _holder.Changed += (_, _) => raised++;

        await _applier.ApplyAsync(_initial with { HostName = "Study" });

        Assert.Equal(1, raised);
    }
}
