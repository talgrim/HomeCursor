using System.Net;
using HomeCursor.Core.Sessions;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Sessions;

public class SessionGateTests
{
    private readonly SessionGate _gate = new(NullLogger<SessionGate>.Instance);

    [Fact]
    public void NewGate_IsNotBusy()
    {
        Assert.False(_gate.IsBusy);
    }

    [Fact]
    public void TryAcquire_WhenFree_SucceedsAndGateBecomesBusy()
    {
        var acquired = _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);

        Assert.True(acquired);
        Assert.Equal("phone", session!.ClientName);
        Assert.True(_gate.IsBusy);
    }

    [Fact]
    public void TryAcquire_WhenBusy_FirstClientWins()
    {
        _gate.TryAcquire(IPAddress.Loopback, "first", out var first);

        var acquired = _gate.TryAcquire(IPAddress.Parse("192.168.1.20"), "second", out var second);

        Assert.False(acquired);
        Assert.Null(second);
        Assert.Same(first, _gate.FindActive(first!.SessionId, IPAddress.Loopback));
    }

    [Fact]
    public void TryAcquire_AfterRelease_Succeeds()
    {
        _gate.TryAcquire(IPAddress.Loopback, "first", out var first);
        _gate.Release(first!, SessionEndReason.ClientDisconnected);

        var acquired = _gate.TryAcquire(IPAddress.Loopback, "second", out _);

        Assert.True(acquired);
    }

    [Fact]
    public void Release_OfStaleSession_DoesNotReleaseCurrentSession()
    {
        _gate.TryAcquire(IPAddress.Loopback, "first", out var first);
        _gate.Release(first!, SessionEndReason.ClientDisconnected);
        _gate.TryAcquire(IPAddress.Loopback, "second", out _);

        _gate.Release(first!, SessionEndReason.ClientDisconnected);

        Assert.True(_gate.IsBusy);
    }

    [Fact]
    public void FindActive_MatchingIdAndAddress_ReturnsSession()
    {
        _gate.TryAcquire(IPAddress.Parse("192.168.1.20"), "phone", out var session);

        var found = _gate.FindActive(session!.SessionId, IPAddress.Parse("192.168.1.20"));

        Assert.Same(session, found);
    }

    [Fact]
    public void FindActive_WrongSessionId_ReturnsNull()
    {
        _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);

        Assert.Null(_gate.FindActive(session!.SessionId + 1, IPAddress.Loopback));
    }

    [Fact]
    public void FindActive_WrongAddress_ReturnsNull()
    {
        _gate.TryAcquire(IPAddress.Parse("192.168.1.20"), "phone", out var session);

        Assert.Null(_gate.FindActive(session!.SessionId, IPAddress.Parse("192.168.1.21")));
    }

    [Fact]
    public void FindActive_Ipv4MappedAddress_MatchesIpv4Address()
    {
        _gate.TryAcquire(IPAddress.Parse("192.168.1.20"), "phone", out var session);

        var found = _gate.FindActive(session!.SessionId, IPAddress.Parse("192.168.1.20").MapToIPv6());

        Assert.Same(session, found);
    }

    [Fact]
    public void FindActive_WithoutSession_ReturnsNull()
    {
        Assert.Null(_gate.FindActive(1, IPAddress.Loopback));
    }

    [Fact]
    public void Current_ReflectsActiveSession()
    {
        Assert.Null(_gate.Current);

        _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);
        Assert.Same(session, _gate.Current);

        _gate.Release(session!, SessionEndReason.ClientDisconnected);
        Assert.Null(_gate.Current);
    }

    [Fact]
    public void TryAcquire_RaisesSessionStartedOnce()
    {
        var started = new List<ClientSession>();
        _gate.SessionStarted += (_, session) => started.Add(session);

        _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);

        Assert.Same(session, Assert.Single(started));
    }

    [Fact]
    public void TryAcquire_WhenBusy_DoesNotRaiseSessionStarted()
    {
        _gate.TryAcquire(IPAddress.Loopback, "first", out _);
        var raised = false;
        _gate.SessionStarted += (_, _) => raised = true;

        _gate.TryAcquire(IPAddress.Loopback, "second", out _);

        Assert.False(raised);
    }

    [Fact]
    public void Release_RaisesSessionEndedWithReason()
    {
        _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);
        var ended = new List<SessionEndedEventArgs>();
        _gate.SessionEnded += (_, args) => ended.Add(args);

        _gate.Release(session!, SessionEndReason.TimedOut);

        var args = Assert.Single(ended);
        Assert.Same(session, args.Session);
        Assert.Equal(SessionEndReason.TimedOut, args.Reason);
    }

    [Fact]
    public void Release_Twice_RaisesSessionEndedOnce()
    {
        _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);
        var count = 0;
        _gate.SessionEnded += (_, _) => count++;

        _gate.Release(session!, SessionEndReason.ClientDisconnected);
        _gate.Release(session!, SessionEndReason.ConnectionLost);

        Assert.Equal(1, count);
    }

    [Fact]
    public void Release_OfStaleSession_DoesNotRaiseSessionEnded()
    {
        _gate.TryAcquire(IPAddress.Loopback, "first", out var first);
        _gate.Release(first!, SessionEndReason.ClientDisconnected);
        _gate.TryAcquire(IPAddress.Loopback, "second", out _);
        var raised = false;
        _gate.SessionEnded += (_, _) => raised = true;

        _gate.Release(first!, SessionEndReason.ClientDisconnected);

        Assert.False(raised);
    }

    [Fact]
    public void ThrowingSubscriber_DoesNotBreakAcquireOrOtherSubscribers()
    {
        var secondCalled = false;
        _gate.SessionStarted += (_, _) => throw new InvalidOperationException("boom");
        _gate.SessionStarted += (_, _) => secondCalled = true;

        var acquired = _gate.TryAcquire(IPAddress.Loopback, "phone", out _);

        Assert.True(acquired);
        Assert.True(secondCalled);
        Assert.True(_gate.IsBusy);
    }

    [Fact]
    public void ThrowingSubscriber_DoesNotBreakRelease()
    {
        _gate.TryAcquire(IPAddress.Loopback, "phone", out var session);
        _gate.SessionEnded += (_, _) => throw new InvalidOperationException("boom");

        _gate.Release(session!, SessionEndReason.ClientDisconnected);

        Assert.False(_gate.IsBusy);
    }

    [Fact]
    public void EventHandlers_RunOutsideTheGateLock()
    {
        // A subscriber that queries the gate from another thread would deadlock if the lock were still held.
        _gate.SessionStarted += (_, _) =>
            Assert.True(Task.Run(() => _gate.IsBusy).Wait(TimeSpan.FromSeconds(2)));

        Assert.True(_gate.TryAcquire(IPAddress.Loopback, "phone", out _));
    }
}
