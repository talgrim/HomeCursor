using HomeCursor.Protocol;
using HomeCursor.Protocol.Messages;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Input;

public sealed class InputMessageDispatcher : IMessageDispatcher
{
    private readonly IInputInjector _injector;
    private readonly ILogger<InputMessageDispatcher> _logger;
    private readonly object _sync = new();
    private readonly HashSet<MouseButton> _heldButtons = [];
    private readonly HashSet<SpecialKey> _heldKeys = [];

    public InputMessageDispatcher(IInputInjector injector, ILogger<InputMessageDispatcher> logger)
    {
        _injector = injector;
        _logger = logger;
    }

    public void Dispatch(ProtocolMessage message)
    {
        try
        {
            switch (message)
            {
                case MouseMoveMessage m:
                    _injector.MoveMouse(m.Dx, m.Dy);
                    break;
                case MouseButtonMessage m:
                    Track(_heldButtons, m.Button, m.IsDown);
                    _injector.SetMouseButton(m.Button, m.IsDown);
                    break;
                case ScrollMessage m:
                    _injector.Scroll(m.Horizontal, m.Vertical);
                    break;
                case KeyMessage m:
                    Track(_heldKeys, m.Key, m.IsDown);
                    _injector.SetKey(m.Key, m.IsDown);
                    break;
                case TextMessage m:
                    _injector.TypeText(m.Text);
                    break;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to inject input for {MessageType}", message.GetType().Name);
        }
    }

    public void ReleaseAll()
    {
        MouseButton[] buttons;
        SpecialKey[] keys;
        lock (_sync)
        {
            buttons = [.. _heldButtons];
            keys = [.. _heldKeys];
            _heldButtons.Clear();
            _heldKeys.Clear();
        }

        try
        {
            foreach (var button in buttons)
            {
                _injector.SetMouseButton(button, false);
            }

            foreach (var key in keys)
            {
                _injector.SetKey(key, false);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to release held input");
        }
    }

    private void Track<T>(HashSet<T> held, T value, bool isDown)
    {
        lock (_sync)
        {
            if (isDown)
            {
                held.Add(value);
            }
            else
            {
                held.Remove(value);
            }
        }
    }
}
