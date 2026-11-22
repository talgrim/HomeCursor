namespace HomeCursor.Core.Updates;

/// <summary>
/// Swaps the executable on disk. Windows does not allow overwriting a running executable, but it does allow
/// renaming it, so the running file becomes <c>.old</c> and the new file takes its name.
/// </summary>
public static class ExecutableReplacer
{
    public const string OldSuffix = ".old";

    public static string GetOldPath(string executablePath) => executablePath + OldSuffix;

    /// <summary>Puts <paramref name="newPath"/> in place of <paramref name="currentPath"/>, which is kept as <c>.old</c>.</summary>
    /// <exception cref="UpdateException">The files could not be swapped; the current executable is left where it was.</exception>
    public static void Replace(string currentPath, string newPath)
    {
        var oldPath = GetOldPath(currentPath);

        try
        {
            // A leftover from an earlier update may still be locked or may be gone already.
            File.Delete(oldPath);
            File.Move(currentPath, oldPath);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            throw new UpdateException($"Could not replace '{currentPath}': {ex.Message}", ex);
        }

        try
        {
            File.Move(newPath, currentPath);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // Put the original back so the host does not end up without an executable.
            File.Move(oldPath, currentPath);
            throw new UpdateException($"Could not put the new version in place: {ex.Message}", ex);
        }
    }

    /// <summary>Removes the executable an earlier update left behind. Never throws: it may still be locked.</summary>
    public static void DeleteLeftover(string currentPath)
    {
        try
        {
            File.Delete(GetOldPath(currentPath));
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            // The next start tries again.
        }
    }
}
