using HomeCursor.Core;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using Microsoft.Extensions.Options;

namespace HomeCursor.Host.Tray;

/// <summary>
/// Tray-only application shell: no main window, just a notify icon with a context menu.
/// </summary>
internal sealed class TrayApplicationContext : ApplicationContext
{
    // NotifyIcon.Text throws for anything longer than 63 characters.
    private const int MaxTooltipLength = 63;
    private const string NoClientText = "No client connected";

    private readonly IHostService _host;
    private readonly ISessionMonitor _sessions;
    private readonly bool _notifyOnClientConnection;
    private readonly bool _notifyOnClientDisconnection;
    private readonly TrayIcons _icons = new();
    private readonly NotifyIcon _notifyIcon;
    private readonly ToolStripMenuItem _statusItem;
    private readonly ToolStripMenuItem _clientItem;
    private readonly SynchronizationContext _uiContext;
    private bool _exiting;

    public TrayApplicationContext(IHostService host, ISessionMonitor sessions, IOptions<HostSettings> settings)
    {
        _host = host;
        _sessions = sessions;
        _notifyOnClientConnection = settings.Value.NotifyOnClientConnection;
        _notifyOnClientDisconnection = settings.Value.NotifyOnClientDisconnection;
        _uiContext = new WindowsFormsSynchronizationContext();

        _statusItem = new ToolStripMenuItem { Enabled = false };
        _clientItem = new ToolStripMenuItem { Enabled = false };
        var exitItem = new ToolStripMenuItem("Exit", image: null, (_, _) => ExitThread());

        var menu = new ContextMenuStrip();
        menu.Items.AddRange(_statusItem, _clientItem, new ToolStripSeparator(), exitItem);

        _notifyIcon = new NotifyIcon
        {
            Icon = _icons.Idle,
            ContextMenuStrip = menu,
            Visible = true
        };

        // The host raises its events from background threads; WinForms controls must be touched on the UI thread.
        _host.StatusChanged += OnHostStatusChanged;
        _sessions.SessionStarted += OnSessionStarted;
        _sessions.SessionEnded += OnSessionEnded;
        Refresh();

        _ = StartHostAsync();
    }

    protected override void ExitThreadCore()
    {
        _exiting = true;
        _host.StatusChanged -= OnHostStatusChanged;
        _sessions.SessionStarted -= OnSessionStarted;
        _sessions.SessionEnded -= OnSessionEnded;

        // Run on the thread pool so the blocking wait can never deadlock against the UI thread.
        Task.Run(() => _host.StopAsync()).GetAwaiter().GetResult();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _icons.Dispose();
        base.ExitThreadCore();
    }

    private void OnHostStatusChanged(object? sender, HostStatus status) =>
        _uiContext.Post(_ => Refresh(), null);

    private void OnSessionStarted(object? sender, ClientSession session) =>
        _uiContext.Post(_ => ShowClientConnected(session), null);

    private void OnSessionEnded(object? sender, SessionEndedEventArgs args) =>
        _uiContext.Post(_ => ShowClientDisconnected(args), null);

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
        if (_exiting)
        {
            return;
        }

        _notifyIcon.ShowBalloonTip(
            timeout: 10_000,
            tipTitle: "HomeCursor could not start",
            tipText: exception.Message,
            tipIcon: ToolTipIcon.Error);
    }

    private void ShowClientConnected(ClientSession session)
    {
        if (_exiting)
        {
            return;
        }

        Refresh();

        if (_notifyOnClientConnection)
        {
            _notifyIcon.ShowBalloonTip(
                timeout: 5_000,
                tipTitle: "Client connected",
                tipText: Describe(session),
                tipIcon: ToolTipIcon.Info);
        }
    }

    private void ShowClientDisconnected(SessionEndedEventArgs args)
    {
        // The host stopping also ends the session; the app is closing then, so there is nothing to tell.
        if (_exiting)
        {
            return;
        }

        Refresh();

        if (_notifyOnClientDisconnection)
        {
            _notifyIcon.ShowBalloonTip(
                timeout: 5_000,
                tipTitle: "Client disconnected",
                tipText: $"{Describe(args.Session)}\n{args.Reason.Describe()}",
                tipIcon: ToolTipIcon.Info);
        }
    }

    /// <summary>Brings the menu, icon and tooltip in line with the current host status and client.</summary>
    private void Refresh()
    {
        if (_exiting)
        {
            return;
        }

        // Read the current session instead of trusting event order: events are posted asynchronously.
        var client = _sessions.Current;

        _statusItem.Text = $"Status: {_host.Status}";
        _clientItem.Text = client is null ? NoClientText : $"Connected: {Describe(client)}";
        _notifyIcon.Icon = client is null ? _icons.Idle : _icons.Connected;
        _notifyIcon.Text = Truncate(
            client is null ? $"HomeCursor - {_host.Status}" : $"HomeCursor - {Describe(client)}",
            MaxTooltipLength);
    }

    private static string Describe(ClientSession session) =>
        string.IsNullOrWhiteSpace(session.ClientName)
            ? session.RemoteAddress.ToString()
            : $"{session.ClientName} ({session.RemoteAddress})";

    private static string Truncate(string text, int maxLength) =>
        text.Length <= maxLength ? text : string.Concat(text.AsSpan(0, maxLength - 1), "…");
}
