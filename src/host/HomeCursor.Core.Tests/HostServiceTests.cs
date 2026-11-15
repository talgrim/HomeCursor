namespace HomeCursor.Core.Tests;

public class HostServiceTests
{
    [Fact]
    public void NewHost_IsStopped()
    {
        var host = new HostService();

        Assert.Equal(HostStatus.Stopped, host.Status);
    }

    [Fact]
    public async Task StartAsync_SetsRunningAndRaisesStatusChanged()
    {
        var host = new HostService();
        HostStatus? raised = null;
        host.StatusChanged += (_, status) => raised = status;

        await host.StartAsync();

        Assert.Equal(HostStatus.Running, host.Status);
        Assert.Equal(HostStatus.Running, raised);
    }

    [Fact]
    public async Task StopAsync_AfterStart_SetsStopped()
    {
        var host = new HostService();
        await host.StartAsync();

        await host.StopAsync();

        Assert.Equal(HostStatus.Stopped, host.Status);
    }

    [Fact]
    public async Task StartAsync_WhenAlreadyRunning_DoesNotRaiseStatusChangedAgain()
    {
        var host = new HostService();
        await host.StartAsync();
        var raisedCount = 0;
        host.StatusChanged += (_, _) => raisedCount++;

        await host.StartAsync();

        Assert.Equal(0, raisedCount);
    }
}
