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
    private readonly SynchronizationContext _uiContext;

    public TrayApplicationContext(IHostService host)
    {
        _host = host;
        _uiContext = new WindowsFormsSynchronizationContext();

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

        // The host raises StatusChanged from background threads; WinForms controls must be touched on the UI thread.
        _host.StatusChanged += (_, _) => _uiContext.Post(_ => UpdateStatus(), null);
        UpdateStatus();

        _ = StartHostAsync();
    }

    protected override void ExitThreadCore()
    {
        // Run on the thread pool so the blocking wait can never deadlock against the UI thread.
        Task.Run(() => _host.StopAsync()).GetAwaiter().GetResult();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        base.ExitThreadCore();
    }

    private async Task StartHostAsync()
    {
        try
        {
            await _host.StartAsync().ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _uiContext.Post(_ => ShowStartError(ex), null);
        }
    }

    private void ShowStartError(Exception exception)
    {
        _notifyIcon.ShowBalloonTip(
            timeout: 10_000,
            tipTitle: "HomeCursor could not start",
            tipText: exception.Message,
            tipIcon: ToolTipIcon.Error);
    }

    private void UpdateStatus()
    {
        _statusItem.Text = $"Status: {_host.Status}";
        _notifyIcon.Text = $"HomeCursor - {_host.Status}";
    }
}
