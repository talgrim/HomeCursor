using HomeCursor.Protocol;

namespace HomeCursor.Core.Input;

/// <summary>
/// Applies input to the machine the host runs on. Implementations are platform specific and must be thread-safe.
/// </summary>
public interface IInputInjector
{
    void MoveMouse(int dx, int dy);

    void SetMouseButton(MouseButton button, bool isDown);

    /// <summary>Amounts are in 1/120 of a wheel notch; positive scrolls up / right.</summary>
    void Scroll(int horizontal, int vertical);

    void SetKey(SpecialKey key, bool isDown);

    void TypeText(string text);
}
