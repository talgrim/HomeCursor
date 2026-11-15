using HomeCursor.Core;
using HomeCursor.Core.Settings;
using HomeCursor.Host.Tray;
using HomeCursor.Host.Windows;
using Microsoft.Extensions.Logging;

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

        using var loggerFactory = LoggerFactory.Create(builder => builder.SetMinimumLevel(LogLevel.Debug).AddDebug());

        HostSettings settings;
        try
        {
            var settingsPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "HomeCursor", "settings.json");
            settings = new JsonFileSettingsProvider(
                settingsPath, loggerFactory.CreateLogger<JsonFileSettingsProvider>()).Load();
        }
        catch (SettingsException ex)
        {
            MessageBox.Show(ex.Message, "HomeCursor", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return;
        }

        var injector = new WindowsInputInjector(loggerFactory.CreateLogger<WindowsInputInjector>());
        var host = HostServiceFactory.Create(settings, injector, loggerFactory);

        Application.Run(new TrayApplicationContext(host));
    }
}
