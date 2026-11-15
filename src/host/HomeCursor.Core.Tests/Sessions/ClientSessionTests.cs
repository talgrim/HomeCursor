using System.Net;
using HomeCursor.Core.Sessions;

namespace HomeCursor.Core.Tests.Sessions;

public class ClientSessionTests
{
    private static ClientSession CreateSession() => new(1, IPAddress.Loopback, "test");

    [Fact]
    public void TryAcceptSequence_FirstSequence_IsAccepted()
    {
        Assert.True(CreateSession().TryAcceptSequence(100));
    }

    [Fact]
    public void TryAcceptSequence_IncreasingSequences_AreAccepted()
    {
        var session = CreateSession();

        Assert.True(session.TryAcceptSequence(1));
        Assert.True(session.TryAcceptSequence(2));
        Assert.True(session.TryAcceptSequence(10));
    }

    [Fact]
    public void TryAcceptSequence_DuplicateOrOlderSequence_IsRejected()
    {
        var session = CreateSession();
        session.TryAcceptSequence(10);

        Assert.False(session.TryAcceptSequence(10));
        Assert.False(session.TryAcceptSequence(9));
    }

    [Fact]
    public void TryAcceptSequence_AfterWrapAround_IsAccepted()
    {
        var session = CreateSession();
        session.TryAcceptSequence(uint.MaxValue - 1);

        Assert.True(session.TryAcceptSequence(2));
        Assert.False(session.TryAcceptSequence(uint.MaxValue));
    }
}
