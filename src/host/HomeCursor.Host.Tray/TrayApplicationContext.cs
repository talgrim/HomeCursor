using HomeCursor.Core;
using HomeCursor.Core.Sessions;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Updates;
using Microsoft.Extensions.Logging;
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
    private readonly ISettingsHolder _settings;
    private readonly ISettingsApplier _settingsApplier;
    private readonly IUpdateMonitor _updateMonitor;
    private readonly UpdateEnvironment _updateEnvironment;
    private readonly ILogger<TrayApplicationContext> _logger;
    private readonly TrayIcons _icons = new();
    private readonly NotifyIcon _notifyIcon;
    private readonly ToolStripMenuItem _statusItem;
    private readonly ToolStripMenuItem _clientItem;
    private readonly SynchronizationContext _uiContext;
    private readonly UpdateTrayController _updates;
    private SettingsForm? _settingsForm;
    private bool _exiting;

    public TrayApplicationContext(
        IHostService host,
        ISessionMonitor sessions,
        ISettingsHolder settings,
        ISettingsApplier settingsApplier,
        IUpdateMonitor updateMonitor,
        IUpdateInstaller updateInstaller,
        UpdateEnvironment updateEnvironment,
        ILogger<UpdateTrayController> updateLogger,
        ILogger<TrayApplicationContext> logger)
    {
        _host = host;
        _sessions = sessions;
        _settings = settings;
        _settingsApplier = settingsApplier;
        _updateMonitor = updateMonitor;
        _updateEnvironment = updateEnvironment;
        _logger = logger;
        _uiContext = new WindowsFormsSynchronizationContext();

        _statusItem = new ToolStripMenuItem { Enabled = false };
        _clientItem = new ToolStripMenuItem { Enabled = false };
        var settingsItem = new ToolStripMenuItem("Settings…", image: null, (_, _) => ShowSettings());
        var exitItem = new ToolStripMenuItem("Exit", image: null, (_, _) => ExitThread());

        _notifyIcon = new NotifyIcon
        {
            Icon = _icons.Idle,
            Visible = true
        };

        _updates = new UpdateTrayController(
            _notifyIcon, updateMonitor, updateInstaller, sessions, updateEnvironment, _uiContext, ExitThread, updateLogger);

        var menu = new ContextMenuStrip();
        menu.Items.AddRange(_statusItem, _clientItem, new ToolStripSeparator());
        menu.Items.AddRange([.. _updates.MenuItems]);
        menu.Items.AddRange(settingsItem, new ToolStripSeparator(), exitItem);
        _notifyIcon.ContextMenuStrip = menu;

        // The host raises its events from background threads; WinForms controls must be touched on the UI thread.
        _host.StatusChanged += OnHostStatusChanged;
        _sessions.SessionStarted += OnSessionStarted;
        _sessions.SessionEnded += OnSessionEnded;
        _settings.Changed += OnSettingsChanged;
        Refresh();

        _ = StartHostAsync();
        _updates.Start();
    }

    protected override void ExitThreadCore()
    {
        _exiting = true;
        _host.StatusChanged -= OnHostStatusChanged;
        _sessions.SessionStarted -= OnSessionStarted;
        _sessions.SessionEnded -= OnSessionEnded;
        _settings.Changed -= OnSettingsChanged;
        _settingsForm?.Close();
        _updates.Dispose();

        // Run on the thread pool so the blocking wait can never deadlock against the UI thread.
        Task.Run(() => _host.StopAsync()).GetAwaiter().GetResult();
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
        _icons.Dispose();
        base.ExitThreadCore();
    }

    // CheckForUpdates may have been switched; the other settings are read when they are needed.
    private void OnSettingsChanged(object? sender, HostSettings settings) => _updateMonitor.Refresh();

    private void ShowSettings()
    {
        if (_settingsForm is { IsDisposed: false })
        {
            _settingsForm.Activate();
            return;
        }

        _settingsForm = new SettingsForm(_settings, _settingsApplier, RestartApplication);
        _settingsForm.FormClosed += (_, _) => _settingsForm = null;
        _settingsForm.Show();
    }

    private void RestartApplication()
    {
        if (ApplicationRestarter.TryStart(_updateEnvironment.ExecutablePath, _logger))
        {
            // The new process waits for this one to release the single-instance mutex.
            ExitThread();
            return;
        }

        MessageBox.Show(
            "HomeCursor could not restart itself. The settings are saved; start HomeCursor again to use them.",
            "HomeCursor settings",
            MessageBoxButtons.OK,
            MessageBoxIcon.Warning);
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

        if (_settings.Value.NotifyOnClientConnection)
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

        if (_settings.Value.NotifyOnClientDisconnection)
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
