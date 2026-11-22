using System.ComponentModel;
using System.Diagnostics;
using Microsoft.Extensions.Logging;

namespace HomeCursor.Host.Tray;

/// <summary>Starts a second instance of the host that takes over once this one has exited.</summary>
internal static class ApplicationRestarter
{
    /// <summary>
    /// Command-line argument of an instance started by another one. It waits for the single-instance mutex
    /// instead of giving up at once, because the instance that started it is still shutting down.
    /// </summary>
    public const string RestartedArgument = "--after-update";

    /// <returns><c>false</c> when the new process could not be started; the caller then stays running.</returns>
    public static bool TryStart(string executablePath, ILogger logger)
    {
        try
        {
            Process.Start(new ProcessStartInfo(executablePath, RestartedArgument) { UseShellExecute = false });
            return true;
        }
        catch (Exception ex) when (ex is Win32Exception or InvalidOperationException)
        {
            logger.LogError(ex, "HomeCursor could not be started again");
            return false;
        }
    }
}
