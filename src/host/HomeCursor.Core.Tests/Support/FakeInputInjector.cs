using HomeCursor.Core.Input;
using HomeCursor.Protocol;

namespace HomeCursor.Core.Tests.Support;

internal sealed class FakeInputInjector : IInputInjector
{
    private readonly object _sync = new();
    private readonly List<string> _calls = [];

    public Exception? ExceptionToThrow { get; set; }

    public IReadOnlyList<string> Calls
    {
        get
        {
            lock (_sync)
            {
                return [.. _calls];
            }
        }
    }

    public void MoveMouse(int dx, int dy) => Record($"Move({dx},{dy})");

    public void SetMouseButton(MouseButton button, bool isDown) => Record($"Button({button},{isDown})");

    public void Scroll(int horizontal, int vertical) => Record($"Scroll({horizontal},{vertical})");

    public void SetKey(SpecialKey key, bool isDown) => Record($"Key({key},{isDown})");

    public void TypeText(string text) => Record($"Text({text})");

    private void Record(string call)
    {
        lock (_sync)
        {
            _calls.Add(call);
        }

        if (ExceptionToThrow is not null)
        {
            throw ExceptionToThrow;
        }
    }
}
