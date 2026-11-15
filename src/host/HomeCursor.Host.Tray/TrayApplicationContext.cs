using HomeCursor.Core;

namespace HomeCursor.Host.Tray;

/// <summary>
/// Tray-only application shell: no main window, just a notify icon with a context menu.
/// </summary>
internal sealed class TrayApplicationContext : ApplicationContext
{
    private readonly IHostService _host;
    private readonly NotifyIcon _notifyIcon;
    private readonly ToolStripMenuItem _statusItem;

    public TrayApplicationContext(IHostService host)
    {
        _host = host;

        _statusItem = new ToolStripMenuItem { Enabled = false };
        var exitItem = new ToolStripMenuItem("Exit", image: null, (_, _) => ExitThread());

        var menu = new ContextMenuStrip();
        menu.Items.AddRange(_statusItem, new ToolStripSeparator(), exitItem);

        _notifyIcon = new NotifyIcon
        {
            Icon = SystemIcons.Exclamation,
            Text = $"HomeCursor - {_host.Status}",
            ContextMenuStrip = menu,
            Visible = true
        };

        _host.StatusChanged += (_, _) => UpdateStatus();
        UpdateStatus();

        _ = _host.StartAsync();
    }

    protected override void ExitThreadCore()
    {
        _host.StopAsync().GetAwaiter().GetResult();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        base.ExitThreadCore();
    }

    private void UpdateStatus()
    {
        _statusItem.Text = $"Status: {_host.Status}";
        _notifyIcon.Text = $"HomeCursor - {_host.Status}";
    }
}
