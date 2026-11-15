using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;

namespace HomeCursor.Host.Tray;

/// <summary>Draws the tray icons at runtime so no icon assets have to ship with the app.</summary>
internal sealed class TrayIcons : IDisposable
{
    private const int Size = 32;

    public TrayIcons()
    {
        Idle = Create(Color.FromArgb(120, 130, 140));
        Connected = Create(Color.FromArgb(46, 160, 67));
    }

    /// <summary>Host running, no client connected.</summary>
    public Icon Idle { get; }

    /// <summary>A client is connected.</summary>
    public Icon Connected { get; }

    public void Dispose()
    {
        Idle.Dispose();
        Connected.Dispose();
    }

    private static Icon Create(Color color)
    {
        using var bitmap = new Bitmap(Size, Size);
        using (var graphics = Graphics.FromImage(bitmap))
        {
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            graphics.Clear(Color.Transparent);

            using var fill = new SolidBrush(color);
            using var outline = new Pen(Color.White, 2f);
            graphics.FillEllipse(fill, 3, 3, Size - 6, Size - 6);
            graphics.DrawEllipse(outline, 3, 3, Size - 6, Size - 6);
        }

        var handle = bitmap.GetHicon();
        try
        {
            // Icon.FromHandle does not own the handle, so clone it before destroying the original.
            using var borrowed = Icon.FromHandle(handle);
            return (Icon)borrowed.Clone();
        }
        finally
        {
            DestroyIcon(handle);
        }
    }

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DestroyIcon(IntPtr handle);
}
