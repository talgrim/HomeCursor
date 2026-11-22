using HomeCursor.Core.Settings;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Core.Updates;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;

namespace HomeCursor.Core.Tests.Updates;

public sealed class UpdateMonitorTests
{
    private static readonly UpdateInfo Release = new(new Version(1, 4, 0), new Uri("https://example.test/a.zip"), new string('a', 64), 1);

    private readonly FakeUpdateChecker _checker = new() { Result = Release };
    private readonly InMemoryUpdateStateStore _store = new();

    private UpdateMonitor Create(bool checkForUpdates = true, TimeSpan? checkInterval = null, TimeSpan? retryInterval = null) =>
        new(
            _checker,
            _store,
            Options.Create(new HostSettings { CheckForUpdates = checkForUpdates }),
            NullLogger<UpdateMonitor>.Instance,
            TimeProvider.System,
            checkInterval,
            retryInterval);

    [Fact]
    public async Task CheckNowAsync_FindsUpdate_SetsAvailableRecordsTimeAndRaisesEvent()
    {
        using var monitor = Create();
        UpdateInfo? raised = null;
        monitor.UpdateFound += (_, update) => raised = update;

        var result = await monitor.CheckNowAsync();

        Assert.Same(Release, result);
        Assert.Same(Release, monitor.Available);
        Assert.Same(Release, raised);
        Assert.NotNull(_store.LastCheck);
    }

    [Fact]
    public async Task CheckNowAsync_SameUpdateTwice_RaisesEventOnce()
    {
        using var monitor = Create();
        var raised = 0;
        monitor.UpdateFound += (_, _) => raised++;

        await monitor.CheckNowAsync();
        await monitor.CheckNowAsync();

        Assert.Equal(1, raised);
    }

    [Fact]
    public async Task CheckNowAsync_NoUpdate_ClearsAvailable()
    {
        using var monitor = Create();
        await monitor.CheckNowAsync();

        _checker.Result = null;
        await monitor.CheckNowAsync();

        Assert.Null(monitor.Available);
    }

    [Fact]
    public async Task CheckNowAsync_CheckFails_ThrowsAndKeepsLastCheck()
    {
        using var monitor = Create();
        _checker.Failure = new UpdateException("offline");

        await Assert.ThrowsAsync<UpdateException>(() => monitor.CheckNowAsync());

        Assert.Null(_store.LastCheck);
    }

    [Fact]
    public async Task Start_NeverCheckedBefore_ChecksAtOnce()
    {
        using var monitor = Create();

        monitor.Start();

        await Wait.UntilAsync(() => _checker.Calls >= 1);
        Assert.Same(Release, monitor.Available);
    }

    [Fact]
    public async Task Start_CheckedRecently_WaitsForTheInterval()
    {
        _store.LastCheck = DateTimeOffset.UtcNow;
        using var monitor = Create(checkInterval: TimeSpan.FromHours(24));

        monitor.Start();
        await Task.Delay(200);

        Assert.Equal(0, _checker.Calls);
    }

    [Fact]
    public async Task Start_ChecksAgainAfterEachInterval()
    {
        using var monitor = Create(checkInterval: TimeSpan.FromMilliseconds(50));

        monitor.Start();

        await Wait.UntilAsync(() => _checker.Calls >= 3);
    }

    [Fact]
    public async Task Start_CheckFails_RetriesAfterRetryInterval()
    {
        _checker.Failure = new UpdateException("offline");
        using var monitor = Create(retryInterval: TimeSpan.FromMilliseconds(50));

        monitor.Start();

        await Wait.UntilAsync(() => _checker.Calls >= 3);
    }

    [Fact]
    public async Task Start_CheckForUpdatesOff_NeverChecks()
    {
        using var monitor = Create(checkForUpdates: false);

        monitor.Start();
        await Task.Delay(200);

        Assert.Equal(0, _checker.Calls);
    }

    [Fact]
    public async Task CheckNowAsync_WorksWhenCheckForUpdatesIsOff()
    {
        using var monitor = Create(checkForUpdates: false);

        Assert.Same(Release, await monitor.CheckNowAsync());
    }
}
