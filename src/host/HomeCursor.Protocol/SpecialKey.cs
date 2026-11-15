namespace HomeCursor.Protocol;

/// <summary>
/// Non-text keys. Regular characters are sent with <see cref="Messages.TextMessage"/>.
/// </summary>
public enum SpecialKey : byte
{
    Enter = 1,
    Backspace = 2,
    Tab = 3,
    Escape = 4,
    Delete = 5,
    Insert = 6,
    Left = 7,
    Right = 8,
    Up = 9,
    Down = 10,
    Home = 11,
    End = 12,
    PageUp = 13,
    PageDown = 14,

    Shift = 15,
    Control = 16,
    Alt = 17,
    Windows = 18,

    F1 = 32,
    F2 = 33,
    F3 = 34,
    F4 = 35,
    F5 = 36,
    F6 = 37,
    F7 = 38,
    F8 = 39,
    F9 = 40,
    F10 = 41,
    F11 = 42,
    F12 = 43
}
