using HomeCursor.Core;
using HomeCursor.Core.Diagnostics;
using HomeCursor.Core.Input;
using HomeCursor.Core.Settings;
using HomeCursor.Core.Updates;
using HomeCursor.Host.Tray;
using HomeCursor.Host.Windows;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;

internal static class Program
{
    private static readonly TimeSpan PreviousInstanceTimeout = TimeSpan.FromSeconds(15);

    [STAThread]
    private static void Main(string[] args)
    {
        // An updated host is started by the one it replaces, which may still be shutting down.
        var restartedByUpdate = args.Contains(UpdateTrayController.RestartedArgument);

        using var mutex = new Mutex(initiallyOwned: false, "HomeCursor.Host.Tray");
        if (!TryBecomeFirstInstance(mutex, restartedByUpdate ? PreviousInstanceTimeout : TimeSpan.Zero))
        {
            return;
        }

        try
        {
            Run();
        }
        finally
        {
            mutex.ReleaseMutex();
        }
    }

    private static bool TryBecomeFirstInstance(Mutex mutex, TimeSpan timeout)
    {
        try
        {
            return mutex.WaitOne(timeout);
        }
        catch (AbandonedMutexException)
        {
            // The previous instance ended without releasing it; the mutex is ours now.
            return true;
        }
    }

    private static void Run()
    {
        ApplicationConfiguration.Initialize();

        // settings.json ships next to the executable (copied from the project on build).
        var settingsPath = Path.Combine(AppContext.BaseDirectory, "settings.json");

        // Load settings first so a bad settings file is reported before anything starts, and so the
        // logging setup can depend on them.
        HostSettings settings;
        try
        {
            settings = new JsonFileSettingsProvider(settingsPath, NullLogger<JsonFileSettingsProvider>.Instance).Load();
        }
        catch (SettingsException ex)
        {
            MessageBox.Show(ex.Message, "HomeCursor", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return;
        }

        var updateEnvironment = CreateUpdateEnvironment();
        ExecutableReplacer.DeleteLeftover(updateEnvironment.ExecutablePath);

        using var provider = BuildServiceProvider(settings, updateEnvironment);

        LogStartup(provider.GetRequiredService<ILogger<TrayApplicationContext>>(), settingsPath, settings);
        Application.Run(provider.GetRequiredService<TrayApplicationContext>());
    }

    private static UpdateEnvironment CreateUpdateEnvironment() =>
        new(
            typeof(Program).Assembly.GetName().Version ?? new Version(0, 0),
            Environment.ProcessPath ?? Path.Combine(AppContext.BaseDirectory, "HomeCursor.exe"),
            Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "HomeCursor", "update-state.json"));

    private static ServiceProvider BuildServiceProvider(HostSettings settings, UpdateEnvironment updateEnvironment)
    {
        var services = new ServiceCollection();

        services.AddLogging(builder =>
        {
            builder.SetMinimumLevel(LogLevel.Debug).AddDebug();

            if (settings.DiagnosticsEnabled)
            {
                // Registered through a factory so the container disposes it and the queued lines are written out.
                builder.Services.AddSingleton<ILoggerProvider>(_ => new RollingFileLoggerProvider(GetLogDirectory(settings)));
            }
        });

        services.AddSingleton(Options.Create(settings));
        services.AddSingleton<IInputInjector, WindowsInputInjector>();
        services.AddHomeCursorCore();
        services.AddHomeCursorUpdates(updateEnvironment);

        services.AddSingleton<TrayApplicationContext>();

        return services.BuildServiceProvider(new ServiceProviderOptions
        {
            ValidateScopes = true,
#if DEBUG
            ValidateOnBuild = true,
#endif
        });
    }

    private static string GetLogDirectory(HostSettings settings) =>
        string.IsNullOrWhiteSpace(settings.DiagnosticsLogDirectory)
            ? AppContext.BaseDirectory
            : settings.DiagnosticsLogDirectory;

    private static void LogStartup(ILogger logger, string settingsPath, HostSettings settings)
    {
        var version = typeof(Program).Assembly.GetName().Version;
        logger.LogInformation(
            "HomeCursor host {Version} on {OS} (.NET {Runtime}), settings {SettingsPath}",
            version, Environment.OSVersion, Environment.Version, settingsPath);

        if (settings.DiagnosticsEnabled)
        {
            logger.LogInformation("Diagnostics log enabled, writing to {Directory}", GetLogDirectory(settings));
        }
    }
}
