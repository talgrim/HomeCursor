using HomeCursor.Core;
using HomeCursor.Core.Input;
using HomeCursor.Core.Settings;
using HomeCursor.Host.Tray;
using HomeCursor.Host.Windows;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var mutex = new Mutex(initiallyOwned: true, "HomeCursor.Host.Tray", out var isFirstInstance);
        if (!isFirstInstance)
        {
            return;
        }

        ApplicationConfiguration.Initialize();

        // settings.json ships next to the executable (copied from the project on build).
        var settingsPath = Path.Combine(AppContext.BaseDirectory, "settings.json");

        using var provider = BuildServiceProvider(settingsPath);

        try
        {
            // Resolve settings eagerly so a bad settings file is reported before anything starts.
            _ = provider.GetRequiredService<IOptions<HostSettings>>().Value;
        }
        catch (SettingsException ex)
        {
            MessageBox.Show(ex.Message, "HomeCursor", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return;
        }

        Application.Run(provider.GetRequiredService<TrayApplicationContext>());
    }

    private static ServiceProvider BuildServiceProvider(string settingsPath)
    {
        var services = new ServiceCollection();

        services.AddLogging(builder => builder.SetMinimumLevel(LogLevel.Debug).AddDebug());

        services.AddSingleton<ISettingsProvider>(sp =>
            new JsonFileSettingsProvider(settingsPath, sp.GetRequiredService<ILogger<JsonFileSettingsProvider>>()));
        services.AddSingleton<IInputInjector, WindowsInputInjector>();
        services.AddHomeCursorCore();

        services.AddSingleton<TrayApplicationContext>();

        return services.BuildServiceProvider(new ServiceProviderOptions
        {
            ValidateScopes = true,
#if DEBUG
            ValidateOnBuild = true,
#endif
        });
    }
}
