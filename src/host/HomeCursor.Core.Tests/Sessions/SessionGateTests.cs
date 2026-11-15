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
        _gate.Release(first!);

        var acquired = _gate.TryAcquire(IPAddress.Loopback, "second", out _);

        Assert.True(acquired);
    }

    [Fact]
    public void Release_OfStaleSession_DoesNotReleaseCurrentSession()
    {
        _gate.TryAcquire(IPAddress.Loopback, "first", out var first);
        _gate.Release(first!);
        _gate.TryAcquire(IPAddress.Loopback, "second", out _);

        _gate.Release(first!);

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
}
