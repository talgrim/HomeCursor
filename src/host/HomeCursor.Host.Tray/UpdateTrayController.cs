using HomeCursor.Core.Sessions;
using HomeCursor.Core.Updates;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Host.Tray;

/// <summary>
/// The tray's part of self-update: the "Update" and "Check for updates" menu items, the balloon that announces
/// a new release and the restart into the new executable. Owns no icon or menu itself, so it can be added to
/// any <see cref="NotifyIcon"/>.
/// </summary>
internal sealed class UpdateTrayController : IDisposable
{
    private readonly NotifyIcon _notifyIcon;
    private readonly IUpdateMonitor _monitor;
    private readonly IUpdateInstaller _installer;
    private readonly ISessionMonitor _sessions;
    private readonly UpdateEnvironment _environment;
    private readonly SynchronizationContext _uiContext;
    private readonly Action _exitApplication;
    private readonly ILogger _logger;
    private readonly ToolStripMenuItem _installItem;
    private readonly ToolStripMenuItem _checkItem;
    private UpdateInfo? _offered;
    private bool _balloonOffersUpdate;
    private bool _busy;
    private bool _disposed;

    public UpdateTrayController(
        NotifyIcon notifyIcon,
        IUpdateMonitor monitor,
        IUpdateInstaller installer,
        ISessionMonitor sessions,
        UpdateEnvironment environment,
        SynchronizationContext uiContext,
        Action exitApplication,
        ILogger logger)
    {
        _notifyIcon = notifyIcon;
        _monitor = monitor;
        _installer = installer;
        _sessions = sessions;
        _environment = environment;
        _uiContext = uiContext;
        _exitApplication = exitApplication;
        _logger = logger;

        _installItem = new ToolStripMenuItem { Visible = false };
        _installItem.Click += (_, _) => _ = InstallAsync();
        _checkItem = new ToolStripMenuItem("Check for updates", image: null, (_, _) => _ = CheckByHandAsync());

        _monitor.UpdateFound += OnUpdateFound;
        _notifyIcon.BalloonTipClicked += OnBalloonTipClicked;
        _notifyIcon.BalloonTipClosed += OnBalloonTipClosed;
    }

    /// <summary>The items to put in the context menu, in order.</summary>
    public IReadOnlyList<ToolStripItem> MenuItems => [_installItem, _checkItem];

    /// <summary>Starts the scheduled update checks.</summary>
    public void Start() => _monitor.Start();

    public void Dispose()
    {
        _disposed = true;
        _monitor.UpdateFound -= OnUpdateFound;
        _notifyIcon.BalloonTipClicked -= OnBalloonTipClicked;
        _notifyIcon.BalloonTipClosed -= OnBalloonTipClosed;
    }

    private void OnUpdateFound(object? sender, UpdateInfo update) =>
        _uiContext.Post(_ => Offer(update, announce: true), null);

    private void Offer(UpdateInfo update, bool announce)
    {
        if (_disposed)
        {
            return;
        }

        _offered = update;
        _installItem.Text = $"Update to {update.Version}…";
        _installItem.Visible = true;

        if (announce && !_busy)
        {
            _balloonOffersUpdate = true;
            _notifyIcon.ShowBalloonTip(
                timeout: 10_000,
                tipTitle: "HomeCursor update available",
                tipText: $"Version {update.Version} is ready. Click here to update.",
                tipIcon: ToolTipIcon.Info);
        }
    }

    private void OnBalloonTipClicked(object? sender, EventArgs e)
    {
        if (_balloonOffersUpdate)
        {
            _balloonOffersUpdate = false;
            _ = InstallAsync();
        }
    }

    private void OnBalloonTipClosed(object? sender, EventArgs e) => _balloonOffersUpdate = false;

    private async Task CheckByHandAsync()
    {
        if (_busy)
        {
            return;
        }

        SetBusy(true, "Checking for updates…");
        try
        {
            var update = await _monitor.CheckNowAsync().ConfigureAwait(true);
            if (update is null)
            {
                ShowBalloon("HomeCursor is up to date", $"Version {_environment.CurrentVersion.ToString(3)} is the newest.", ToolTipIcon.Info);
            }
            else
            {
                Offer(update, announce: false);
                await InstallAfterConfirmationAsync(update).ConfigureAwait(true);
            }
        }
        catch (UpdateException ex)
        {
            _logger.LogWarning("Manual update check failed: {Message}", ex.Message);
            ShowBalloon("Could not check for updates", ex.Message, ToolTipIcon.Warning);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async Task InstallAsync()
    {
        if (_busy || _offered is not { } update)
        {
            return;
        }

        SetBusy(true, "Updating…");
        try
        {
            await InstallAfterConfirmationAsync(update).ConfigureAwait(true);
        }
        finally
        {
            SetBusy(false);
        }
    }

    // Runs inside the busy state of the caller.
    private async Task InstallAfterConfirmationAsync(UpdateInfo update)
    {
        if (!Confirm(update))
        {
            return;
        }

        try
        {
            _checkItem.Text = "Downloading update…";
            await _installer.InstallAsync(update).ConfigureAwait(true);
        }
        catch (UpdateException ex)
        {
            _logger.LogWarning(ex, "Update to {Version} failed", update.Version);
            ShowBalloon("Update failed", ex.Message, ToolTipIcon.Error);
            return;
        }

        Restart();
    }

    private bool Confirm(UpdateInfo update)
    {
        var clientNote = _sessions.Current is null ? string.Empty : "\n\nThe connected client will be disconnected.";
        var result = MessageBox.Show(
            $"Update HomeCursor from {_environment.CurrentVersion.ToString(3)} to {update.Version}?\n\n" +
            $"The host downloads the new version and restarts.{clientNote}",
            "HomeCursor update",
            MessageBoxButtons.YesNo,
            MessageBoxIcon.Question);
        return result == DialogResult.Yes;
    }

    private void Restart()
    {
        if (!ApplicationRestarter.TryStart(_environment.ExecutablePath, _logger))
        {
            ShowBalloon("Update installed", "Start HomeCursor again to use the new version.", ToolTipIcon.Warning);
        }

        // The new process waits for this one to release the single-instance mutex.
        _exitApplication();
    }

    private void SetBusy(bool busy, string? text = null)
    {
        _busy = busy;
        _checkItem.Enabled = !busy;
        _installItem.Enabled = !busy;
        _checkItem.Text = busy && text is not null ? text : "Check for updates";
    }

    private void ShowBalloon(string title, string text, ToolTipIcon icon)
    {
        if (!_disposed)
        {
            _notifyIcon.ShowBalloonTip(timeout: 8_000, tipTitle: title, tipText: text, tipIcon: icon);
        }
    }
}
