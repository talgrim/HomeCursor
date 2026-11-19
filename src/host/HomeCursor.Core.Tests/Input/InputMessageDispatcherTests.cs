using HomeCursor.Core.Input;
using HomeCursor.Core.Tests.Support;
using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Input;

public class InputMessageDispatcherTests
{
    private readonly FakeInputInjector _injector = new();
    private readonly InputMessageDispatcher _dispatcher;

    public InputMessageDispatcherTests()
    {
        _dispatcher = new InputMessageDispatcher(_injector, NullLogger<InputMessageDispatcher>.Instance);
    }

    [Fact]
    public void Dispatch_MouseMove_MovesMouse()
    {
        _dispatcher.Dispatch(new MouseMoveMessage(3, -4));

        Assert.Equal(["Move(3,-4)"], _injector.Calls);
    }

    [Fact]
    public void Dispatch_MouseButton_SetsButton()
    {
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Left, true));

        Assert.Equal(["Button(Left,True)"], _injector.Calls);
    }

    [Fact]
    public void Dispatch_MiddleMouseButton_SetsMiddleButton()
    {
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Middle, true));
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Middle, false));

        Assert.Equal(["Button(Middle,True)", "Button(Middle,False)"], _injector.Calls);
    }

    [Fact]
    public void Dispatch_Scroll_Scrolls()
    {
        _dispatcher.Dispatch(new ScrollMessage(-120, 240));

        Assert.Equal(["Scroll(-120,240)"], _injector.Calls);
    }

    [Fact]
    public void Dispatch_Key_SetsKey()
    {
        _dispatcher.Dispatch(new KeyMessage(SpecialKey.Enter, true));

        Assert.Equal(["Key(Enter,True)"], _injector.Calls);
    }

    [Fact]
    public void Dispatch_Text_TypesText()
    {
        _dispatcher.Dispatch(new TextMessage("abc"));

        Assert.Equal(["Text(abc)"], _injector.Calls);
    }

    [Fact]
    public void Dispatch_NonInputMessage_IsIgnored()
    {
        _dispatcher.Dispatch(new PingMessage());
        _dispatcher.Dispatch(new HelloMessage(1, "x"));

        Assert.Empty(_injector.Calls);
    }

    [Fact]
    public void Dispatch_WhenInjectorThrows_DoesNotPropagate()
    {
        _injector.ExceptionToThrow = new InvalidOperationException();

        _dispatcher.Dispatch(new MouseMoveMessage(1, 1));
    }

    [Fact]
    public void ReleaseAll_ReleasesButtonsAndKeysStillHeld()
    {
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Left, true));
        _dispatcher.Dispatch(new KeyMessage(SpecialKey.Shift, true));

        _dispatcher.ReleaseAll();

        Assert.Contains("Button(Left,False)", _injector.Calls);
        Assert.Contains("Key(Shift,False)", _injector.Calls);
    }

    [Fact]
    public void ReleaseAll_ReleasesMiddleButtonStillHeld()
    {
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Middle, true));

        _dispatcher.ReleaseAll();

        Assert.Contains("Button(Middle,False)", _injector.Calls);
    }

    [Fact]
    public void ReleaseAll_DoesNotReleaseInputAlreadyReleased()
    {
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Left, true));
        _dispatcher.Dispatch(new MouseButtonMessage(MouseButton.Left, false));
        var callsBefore = _injector.Calls.Count;

        _dispatcher.ReleaseAll();

        Assert.Equal(callsBefore, _injector.Calls.Count);
    }

    [Fact]
    public void ReleaseAll_CalledTwice_ReleasesOnce()
    {
        _dispatcher.Dispatch(new KeyMessage(SpecialKey.Control, true));
        _dispatcher.ReleaseAll();
        var callsBefore = _injector.Calls.Count;

        _dispatcher.ReleaseAll();

        Assert.Equal(callsBefore, _injector.Calls.Count);
    }
}
