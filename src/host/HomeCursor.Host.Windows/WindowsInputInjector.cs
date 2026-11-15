using System.ComponentModel;
using System.Runtime.InteropServices;
using HomeCursor.Core.Input;
using HomeCursor.Protocol;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Host.Windows;

/// <summary>
/// Injects mouse and keyboard input with the Win32 <c>SendInput</c> API. Input cannot be injected into
/// windows of elevated processes unless the host is elevated too (UIPI).
/// </summary>
public sealed class WindowsInputInjector : IInputInjector
{
    private const uint InputMouse = 0;
    private const uint InputKeyboard = 1;

    private const uint MouseEventMove = 0x0001;
    private const uint MouseEventLeftDown = 0x0002;
    private const uint MouseEventLeftUp = 0x0004;
    private const uint MouseEventRightDown = 0x0008;
    private const uint MouseEventRightUp = 0x0010;
    private const uint MouseEventMiddleDown = 0x0020;
    private const uint MouseEventMiddleUp = 0x0040;
    private const uint MouseEventWheel = 0x0800;
    private const uint MouseEventHWheel = 0x1000;

    private const uint KeyEventExtended = 0x0001;
    private const uint KeyEventKeyUp = 0x0002;
    private const uint KeyEventUnicode = 0x0004;

    private readonly ILogger<WindowsInputInjector> _logger;

    public WindowsInputInjector(ILogger<WindowsInputInjector> logger)
    {
        _logger = logger;
    }

    public void MoveMouse(int dx, int dy) =>
        Send(Mouse(MouseEventMove, dx, dy, 0));

    public void SetMouseButton(MouseButton button, bool isDown)
    {
        var flag = (button, isDown) switch
        {
            (MouseButton.Left, true) => MouseEventLeftDown,
            (MouseButton.Left, false) => MouseEventLeftUp,
            (MouseButton.Right, true) => MouseEventRightDown,
            (MouseButton.Right, false) => MouseEventRightUp,
            (MouseButton.Middle, true) => MouseEventMiddleDown,
            (MouseButton.Middle, false) => MouseEventMiddleUp,
            _ => throw new ArgumentOutOfRangeException(nameof(button), button, "Unsupported mouse button.")
        };

        Send(Mouse(flag, 0, 0, 0));
    }

    public void Scroll(int horizontal, int vertical)
    {
        var inputs = new List<Input>(2);
        if (vertical != 0)
        {
            inputs.Add(Mouse(MouseEventWheel, 0, 0, unchecked((uint)vertical)));
        }

        if (horizontal != 0)
        {
            inputs.Add(Mouse(MouseEventHWheel, 0, 0, unchecked((uint)horizontal)));
        }

        Send([.. inputs]);
    }

    public void SetKey(SpecialKey key, bool isDown)
    {
        var flags = isDown ? 0u : KeyEventKeyUp;
        if (WindowsVirtualKeys.IsExtended(key))
        {
            flags |= KeyEventExtended;
        }

        Send(Keyboard(WindowsVirtualKeys.GetVirtualKey(key), 0, flags));
    }

    public void TypeText(string text)
    {
        var inputs = new List<Input>(text.Length * 2);
        for (var i = 0; i < text.Length; i++)
        {
            var c = text[i];
            if (c == '\r' && i + 1 < text.Length && text[i + 1] == '\n')
            {
                continue; // CRLF is one Enter, sent for the '\n'
            }

            // Unicode events carry no virtual key, so line breaks and tabs must be real key presses.
            if (c is '\r' or '\n')
            {
                AddKeyPress(inputs, SpecialKey.Enter);
            }
            else if (c == '\t')
            {
                AddKeyPress(inputs, SpecialKey.Tab);
            }
            else
            {
                inputs.Add(Keyboard(0, c, KeyEventUnicode));
                inputs.Add(Keyboard(0, c, KeyEventUnicode | KeyEventKeyUp));
            }
        }

        Send([.. inputs]);
    }

    private static void AddKeyPress(List<Input> inputs, SpecialKey key)
    {
        var virtualKey = WindowsVirtualKeys.GetVirtualKey(key);
        inputs.Add(Keyboard(virtualKey, 0, 0));
        inputs.Add(Keyboard(virtualKey, 0, KeyEventKeyUp));
    }

    private void Send(params Input[] inputs)
    {
        if (inputs.Length == 0)
        {
            return;
        }

        var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<Input>());
        if (sent != inputs.Length)
        {
            _logger.LogWarning(
                "SendInput injected {Sent} of {Expected} events: {Error}",
                sent, inputs.Length, new Win32Exception(Marshal.GetLastWin32Error()).Message);
        }
    }

    private static Input Mouse(uint flags, int dx, int dy, uint data) => new()
    {
        Type = InputMouse,
        Union = new InputUnion { Mouse = new MouseInput { Dx = dx, Dy = dy, MouseData = data, Flags = flags } }
    };

    private static Input Keyboard(ushort virtualKey, ushort scanCode, uint flags) => new()
    {
        Type = InputKeyboard,
        Union = new InputUnion { Keyboard = new KeyboardInput { VirtualKey = virtualKey, ScanCode = scanCode, Flags = flags } }
    };

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint inputCount, Input[] inputs, int inputSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint Type;
        public InputUnion Union;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MouseInput Mouse;

        [FieldOffset(0)]
        public KeyboardInput Keyboard;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int Dx;
        public int Dy;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }
}
