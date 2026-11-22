using Microsoft.Extensions.Logging;

namespace HomeCursor.Core.Settings;

public sealed class SettingsApplier : ISettingsApplier
{
    private readonly ISettingsHolder _holder;
    private readonly ISettingsProvider _provider;
    private readonly IHostService _host;
    private readonly ILogger<SettingsApplier> _logger;
    private readonly SemaphoreSlim _applying = new(1, 1);

    public SettingsApplier(
        ISettingsHolder holder, ISettingsProvider provider, IHostService host, ILogger<SettingsApplier> logger)
    {
        _holder = holder;
        _provider = provider;
        _host = host;
        _logger = logger;
    }

    public async Task<SettingsApplyResult> ApplyAsync(HostSettings settings, CancellationToken cancellationToken = default)
    {
        var errors = settings.GetErrors();
        if (errors.Count > 0)
        {
            throw new SettingsException(errors[0].Message);
        }

        await _applying.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var previous = _holder.Value;

            // Listeners bind once when they start, so new addresses need a restart; a host that failed to start
            // (for example on a port that was taken) gets another try with whatever the user entered.
            var restartHost = HasNetworkChanged(previous, settings) || _host.Status == HostStatus.Faulted;
            _holder.Update(settings);

            if (restartHost)
            {
                await RestartHostAsync(previous, cancellationToken).ConfigureAwait(false);
            }

            var appRestartRequired = HasDiagnosticsChanged(previous, settings);
            return Save(settings, appRestartRequired);
        }
        finally
        {
            _applying.Release();
        }
    }

    private async Task RestartHostAsync(HostSettings previous, CancellationToken cancellationToken)
    {
        try
        {
            await _host.StopAsync(cancellationToken).ConfigureAwait(false);
            await _host.StartAsync(cancellationToken).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Host could not run with the new settings, going back to the previous ones");
            _holder.Update(previous);
            await TryStartAgainAsync().ConfigureAwait(false);
            throw new SettingsException($"The host could not start with the new settings: {ex.Message}", ex);
        }
    }

    private async Task TryStartAgainAsync()
    {
        try
        {
            await _host.StartAsync().ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Host could not start again with the previous settings");
        }
    }

    private SettingsApplyResult Save(HostSettings settings, bool appRestartRequired)
    {
        try
        {
            _provider.Save(settings);
            return new SettingsApplyResult(appRestartRequired);
        }
        catch (SettingsException ex)
        {
            _logger.LogWarning(ex, "Settings are in effect but could not be saved");
            return new SettingsApplyResult(appRestartRequired, ex.Message);
        }
    }

    private static bool HasNetworkChanged(HostSettings before, HostSettings after) =>
        before.BindAddress != after.BindAddress ||
        before.TcpPort != after.TcpPort ||
        before.UdpPort != after.UdpPort ||
        before.DiscoveryPort != after.DiscoveryPort;

    private static bool HasDiagnosticsChanged(HostSettings before, HostSettings after) =>
        before.DiagnosticsEnabled != after.DiagnosticsEnabled ||
        NormalizeFolder(before.DiagnosticsLogDirectory) != NormalizeFolder(after.DiagnosticsLogDirectory);

    // Empty, blank and missing all mean the folder of the executable.
    private static string NormalizeFolder(string? folder) => string.IsNullOrWhiteSpace(folder) ? string.Empty : folder.Trim();
}
