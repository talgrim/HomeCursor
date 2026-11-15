using HomeCursor.Protocol;

namespace HomeCursor.Host.Windows;

/// <summary>
/// Maps protocol keys to Windows virtual-key codes.
/// </summary>
internal static class WindowsVirtualKeys
{
    public static ushort GetVirtualKey(SpecialKey key) => key switch
    {
        SpecialKey.Enter => 0x0D,
        SpecialKey.Backspace => 0x08,
        SpecialKey.Tab => 0x09,
        SpecialKey.Escape => 0x1B,
        SpecialKey.Delete => 0x2E,
        SpecialKey.Insert => 0x2D,
        SpecialKey.Left => 0x25,
        SpecialKey.Up => 0x26,
        SpecialKey.Right => 0x27,
        SpecialKey.Down => 0x28,
        SpecialKey.Home => 0x24,
        SpecialKey.End => 0x23,
        SpecialKey.PageUp => 0x21,
        SpecialKey.PageDown => 0x22,
        SpecialKey.Shift => 0x10,
        SpecialKey.Control => 0x11,
        SpecialKey.Alt => 0x12,
        SpecialKey.Windows => 0x5B,
        >= SpecialKey.F1 and <= SpecialKey.F12 => (ushort)(0x70 + (key - SpecialKey.F1)),
        _ => throw new ArgumentOutOfRangeException(nameof(key), key, "Unsupported key.")
    };

    /// <summary>Keys that Windows distinguishes from their numpad twins by the extended-key flag.</summary>
    public static bool IsExtended(SpecialKey key) => key is
        SpecialKey.Delete or SpecialKey.Insert or
        SpecialKey.Left or SpecialKey.Up or SpecialKey.Right or SpecialKey.Down or
        SpecialKey.Home or SpecialKey.End or SpecialKey.PageUp or SpecialKey.PageDown or
        SpecialKey.Windows;
}
