using HomeCursor.Core.Tests.Support;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests;

public class HostServiceTests
{
    private readonly List<string> _journal = [];

    private HostService CreateHost(params IHostLoop[] loops) => new(loops, NullLogger<HostService>.Instance);

    private FakeHostLoop Loop(string name) => new(name, _journal);

    [Fact]
    public void NewHost_IsStopped()
    {
        var host = CreateHost();

        Assert.Equal(HostStatus.Stopped, host.Status);
    }

    [Fact]
    public async Task StartAsync_SetsRunningAndRaisesStatusChanged()
    {
        var host = CreateHost();
        var raised = new List<HostStatus>();
        host.StatusChanged += (_, status) => raised.Add(status);

        await host.StartAsync();

        Assert.Equal(HostStatus.Running, host.Status);
        Assert.Equal([HostStatus.Starting, HostStatus.Running], raised);
    }

    [Fact]
    public async Task StopAsync_AfterStart_SetsStopped()
    {
        var host = CreateHost();
        await host.StartAsync();

        await host.StopAsync();

        Assert.Equal(HostStatus.Stopped, host.Status);
    }

    [Fact]
    public async Task StartAsync_WhenAlreadyRunning_DoesNotRaiseStatusChangedAgain()
    {
        var host = CreateHost();
        await host.StartAsync();
        var raisedCount = 0;
        host.StatusChanged += (_, _) => raisedCount++;

        await host.StartAsync();

        Assert.Equal(0, raisedCount);
    }

    [Fact]
    public async Task StartAsync_WhenAlreadyRunning_DoesNotStartLoopsAgain()
    {
        var host = CreateHost(Loop("a"));
        await host.StartAsync();

        await host.StartAsync();

        Assert.Equal(["start a"], _journal);
    }

    [Fact]
    public async Task StartAsync_StartsLoopsInOrder()
    {
        var host = CreateHost(Loop("a"), Loop("b"));

        await host.StartAsync();

        Assert.Equal(["start a", "start b"], _journal);
    }

    [Fact]
    public async Task StopAsync_StopsLoopsInReverseOrder()
    {
        var host = CreateHost(Loop("a"), Loop("b"));
        await host.StartAsync();
        _journal.Clear();

        await host.StopAsync();

        Assert.Equal(["stop b", "stop a"], _journal);
    }

    [Fact]
    public async Task StartAsync_WhenLoopFails_SetsFaultedStopsStartedLoopsAndRethrows()
    {
        var failure = new InvalidOperationException("port in use");
        var host = CreateHost(Loop("a"), new FakeHostLoop("b", _journal) { StartException = failure }, Loop("c"));

        var thrown = await Assert.ThrowsAsync<InvalidOperationException>(() => host.StartAsync());

        Assert.Same(failure, thrown);
        Assert.Equal(HostStatus.Faulted, host.Status);
        Assert.Equal(["start a", "start b", "stop a"], _journal);
    }

    [Fact]
    public async Task StartAsync_AfterFault_CanStartAgain()
    {
        var failing = new FakeHostLoop("a", _journal) { StartException = new InvalidOperationException() };
        var host = CreateHost(failing);
        await Assert.ThrowsAsync<InvalidOperationException>(() => host.StartAsync());
        failing.StartException = null;

        await host.StartAsync();

        Assert.Equal(HostStatus.Running, host.Status);
    }

    [Fact]
    public async Task StartAsync_WhenCancelled_SetsStoppedAndStopsStartedLoops()
    {
        var host = CreateHost(Loop("a"), new FakeHostLoop("b", _journal) { StartException = new OperationCanceledException() });

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => host.StartAsync());

        Assert.Equal(HostStatus.Stopped, host.Status);
        Assert.Equal(["start a", "start b", "stop a"], _journal);
    }

    [Fact]
    public async Task StopAsync_WhenLoopFailsToStop_StillStopsOtherLoops()
    {
        var host = CreateHost(Loop("a"), new FakeHostLoop("b", _journal) { StopException = new InvalidOperationException() });
        await host.StartAsync();
        _journal.Clear();

        await host.StopAsync();

        Assert.Equal(["stop b", "stop a"], _journal);
        Assert.Equal(HostStatus.Stopped, host.Status);
    }
}
