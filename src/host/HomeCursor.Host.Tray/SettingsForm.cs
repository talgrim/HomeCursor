using HomeCursor.Core.Settings;

namespace HomeCursor.Host.Tray;

/// <summary>
/// The settings window. It edits the settings in <see cref="ISettingsHolder"/> and hands the result to
/// <see cref="ISettingsApplier"/>; settings it has no field for (bind address, ping and heartbeat, manifest URL)
/// are carried over unchanged.
/// </summary>
internal sealed class SettingsForm : Form
{
    private readonly ISettingsHolder _settings;
    private readonly ISettingsApplier _applier;
    private readonly Action _restartApplication;
    private readonly ErrorProvider _errors = new() { BlinkStyle = ErrorBlinkStyle.NeverBlink };
    private readonly Dictionary<string, Control> _fields = [];

    private readonly TextBox _hostName = new() { MaxLength = 64, Width = 220 };
    private readonly CheckBox _notifyConnected = new() { Text = "When a client connects", AutoSize = true };
    private readonly CheckBox _notifyDisconnected = new() { Text = "When a client disconnects", AutoSize = true };
    private readonly NumericUpDown _tcpPort = CreatePortBox();
    private readonly NumericUpDown _udpPort = CreatePortBox();
    private readonly NumericUpDown _discoveryPort = CreatePortBox();
    private readonly CheckBox _checkForUpdates = new() { Text = "Check for updates automatically", AutoSize = true };
    private readonly CheckBox _diagnostics = new() { Text = "Write a diagnostics log", AutoSize = true };
    private readonly TextBox _logDirectory = new() { Width = 220 };
    private readonly Button _browse = new() { Text = "Browse…", AutoSize = true };
    private readonly Control _content;
    private readonly Button _ok = new() { Text = "OK", DialogResult = DialogResult.None, AutoSize = true };
    private readonly Button _apply = new() { Text = "Apply", AutoSize = true };
    private readonly Button _cancel = new() { Text = "Cancel", DialogResult = DialogResult.Cancel, AutoSize = true };

    public SettingsForm(ISettingsHolder settings, ISettingsApplier applier, Action restartApplication)
    {
        _settings = settings;
        _applier = applier;
        _restartApplication = restartApplication;

        Text = "HomeCursor settings";
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        MinimizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        Padding = new Padding(12);
        AcceptButton = _ok;
        CancelButton = _cancel;
        _errors.ContainerControl = this;

        _fields[nameof(HostSettings.HostName)] = _hostName;
        _fields[nameof(HostSettings.TcpPort)] = _tcpPort;
        _fields[nameof(HostSettings.UdpPort)] = _udpPort;
        _fields[nameof(HostSettings.DiscoveryPort)] = _discoveryPort;
        _fields[nameof(HostSettings.DiagnosticsLogDirectory)] = _logDirectory;

        var logFolderRow = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = Padding.Empty };
        logFolderRow.Controls.AddRange(_logDirectory, _browse);

        var layout = new TableLayoutPanel { AutoSize = true, ColumnCount = 1, Dock = DockStyle.Fill };
        layout.Controls.Add(Group("General", Row("Name shown on the phone:", _hostName)));
        layout.Controls.Add(Group("Notifications", _notifyConnected, _notifyDisconnected));
        layout.Controls.Add(Group(
            "Network",
            Row("Control port (TCP):", _tcpPort),
            Row("Pointer port (UDP):", _udpPort),
            Row("Discovery port (UDP):", _discoveryPort),
            Hint("Changing a port restarts the host and disconnects the connected phone.")));
        layout.Controls.Add(Group("Updates", _checkForUpdates));
        layout.Controls.Add(Group(
            "Diagnostics",
            _diagnostics,
            Row("Log folder:", logFolderRow),
            Hint("Leave the folder empty to use the folder of HomeCursor.exe. Takes effect after a restart.")));
        _content = layout;

        var buttons = new FlowLayoutPanel
        {
            AutoSize = true,
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.RightToLeft,
            Margin = new Padding(0, 8, 0, 0)
        };
        buttons.Controls.AddRange(_cancel, _apply, _ok);

        var root = new TableLayoutPanel { AutoSize = true, ColumnCount = 1, Dock = DockStyle.Fill };
        root.Controls.Add(layout);
        root.Controls.Add(buttons);
        Controls.Add(root);

        _diagnostics.CheckedChanged += (_, _) => UpdateDiagnosticsFields();
        _browse.Click += (_, _) => BrowseForLogFolder();
        _ok.Click += async (_, _) => await RunGuardedAsync(closeOnSuccess: true);
        _apply.Click += async (_, _) => await RunGuardedAsync(closeOnSuccess: false);

        ShowValues(_settings.Value);
    }

    private static NumericUpDown CreatePortBox() => new() { Minimum = 1, Maximum = ushort.MaxValue, Width = 80 };

    private static Label Hint(string text) =>
        new() { Text = text, AutoSize = true, MaximumSize = new Size(380, 0), ForeColor = SystemColors.GrayText };

    private static Control Row(string label, Control control)
    {
        var row = new FlowLayoutPanel { AutoSize = true, WrapContents = false, Margin = Padding.Empty };
        row.Controls.Add(new Label { Text = label, AutoSize = true, Width = 150, Margin = new Padding(0, 6, 0, 0) });
        row.Controls.Add(control);
        return row;
    }

    private static GroupBox Group(string title, params Control[] controls)
    {
        var panel = new FlowLayoutPanel
        {
            AutoSize = true,
            Dock = DockStyle.Fill,
            FlowDirection = FlowDirection.TopDown,
            WrapContents = false
        };
        panel.Controls.AddRange(controls);

        var group = new GroupBox { Text = title, AutoSize = true, Dock = DockStyle.Fill, Padding = new Padding(8, 4, 8, 8) };
        group.Controls.Add(panel);
        return group;
    }

    private void ShowValues(HostSettings settings)
    {
        _hostName.Text = settings.HostName;
        _notifyConnected.Checked = settings.NotifyOnClientConnection;
        _notifyDisconnected.Checked = settings.NotifyOnClientDisconnection;
        _tcpPort.Value = Math.Clamp(settings.TcpPort, (int)_tcpPort.Minimum, (int)_tcpPort.Maximum);
        _udpPort.Value = Math.Clamp(settings.UdpPort, (int)_udpPort.Minimum, (int)_udpPort.Maximum);
        _discoveryPort.Value = Math.Clamp(settings.DiscoveryPort, (int)_discoveryPort.Minimum, (int)_discoveryPort.Maximum);
        _checkForUpdates.Checked = settings.CheckForUpdates;
        _diagnostics.Checked = settings.DiagnosticsEnabled;
        _logDirectory.Text = settings.DiagnosticsLogDirectory ?? string.Empty;
        UpdateDiagnosticsFields();
    }

    private HostSettings Read() =>
        _settings.Value with
        {
            HostName = _hostName.Text.Trim(),
            NotifyOnClientConnection = _notifyConnected.Checked,
            NotifyOnClientDisconnection = _notifyDisconnected.Checked,
            TcpPort = (int)_tcpPort.Value,
            UdpPort = (int)_udpPort.Value,
            DiscoveryPort = (int)_discoveryPort.Value,
            CheckForUpdates = _checkForUpdates.Checked,
            DiagnosticsEnabled = _diagnostics.Checked,
            DiagnosticsLogDirectory = string.IsNullOrWhiteSpace(_logDirectory.Text) ? null : _logDirectory.Text.Trim()
        };

    private void UpdateDiagnosticsFields()
    {
        _logDirectory.Enabled = _diagnostics.Checked;
        _browse.Enabled = _diagnostics.Checked;
    }

    private void BrowseForLogFolder()
    {
        using var dialog = new FolderBrowserDialog
        {
            Description = "Folder for the diagnostics log",
            SelectedPath = _logDirectory.Text
        };

        if (dialog.ShowDialog(this) == DialogResult.OK)
        {
            _logDirectory.Text = dialog.SelectedPath;
        }
    }

    /// <summary>Runs <see cref="ApplyAsync"/> so that an unexpected failure is shown instead of ending the application.</summary>
    private async Task RunGuardedAsync(bool closeOnSuccess)
    {
        try
        {
            if (await ApplyAsync() && closeOnSuccess)
            {
                Close();
            }
        }
        catch (Exception ex)
        {
            _content.Enabled = true;
            MessageBox.Show(this, ex.Message, "HomeCursor settings", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    /// <returns><c>true</c> when the settings are in effect and saved.</returns>
    private async Task<bool> ApplyAsync()
    {
        var settings = Read();
        if (!ShowErrors(settings.GetErrors()))
        {
            return false;
        }

        SettingsApplyResult result;
        _content.Enabled = false;
        UseWaitCursor = true;
        try
        {
            result = await _applier.ApplyAsync(settings);
        }
        catch (SettingsException ex)
        {
            MessageBox.Show(this, ex.Message, "HomeCursor settings", MessageBoxButtons.OK, MessageBoxIcon.Error);
            ShowValues(_settings.Value);
            return false;
        }
        finally
        {
            UseWaitCursor = false;
            _content.Enabled = true;
        }

        if (!result.Saved)
        {
            MessageBox.Show(
                this,
                $"The settings are in effect, but they could not be saved:\n\n{result.SaveError}\n\n" +
                "They are lost when HomeCursor restarts.",
                "HomeCursor settings",
                MessageBoxButtons.OK,
                MessageBoxIcon.Warning);
            return false;
        }

        if (result.AppRestartRequired)
        {
            var answer = MessageBox.Show(
                this,
                "The diagnostics settings take effect after HomeCursor restarts. Restart now?\n\n" +
                "A connected phone is disconnected.",
                "HomeCursor settings",
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question);
            if (answer == DialogResult.Yes)
            {
                _restartApplication();
            }
        }

        return true;
    }

    /// <summary>Marks the fields with a problem and returns whether there were none.</summary>
    private bool ShowErrors(IReadOnlyList<SettingsError> errors)
    {
        foreach (var field in _fields.Values)
        {
            _errors.SetError(field, string.Empty);
        }

        var unmapped = new List<string>();
        foreach (var error in errors)
        {
            if (_fields.TryGetValue(error.Property, out var field))
            {
                _errors.SetError(field, error.Message);
            }
            else
            {
                unmapped.Add(error.Message);
            }
        }

        if (unmapped.Count > 0)
        {
            MessageBox.Show(
                this, string.Join('\n', unmapped), "HomeCursor settings", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }

        return errors.Count == 0;
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _errors.Dispose();
        }

        base.Dispose(disposing);
    }
}
