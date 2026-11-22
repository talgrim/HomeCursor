namespace HomeCursor.Core.Updates;

/// <summary>Facts about the running host that updating needs.</summary>
/// <param name="CurrentVersion">Version of the running host.</param>
/// <param name="ExecutablePath">Path of the running executable, which an update replaces.</param>
/// <param name="StateFilePath">File that remembers when the last update check ran.</param>
public sealed record UpdateEnvironment(Version CurrentVersion, string ExecutablePath, string StateFilePath);
