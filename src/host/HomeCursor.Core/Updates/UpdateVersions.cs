namespace HomeCursor.Core.Updates;

internal static class UpdateVersions
{
    /// <summary>
    /// Reduces a version to major.minor.patch. The assembly version has four parts (1.3.0.0) while release
    /// versions have three (1.3.0), and <see cref="Version"/> orders a missing part before a zero.
    /// </summary>
    public static Version Normalize(Version version) =>
        new(version.Major, version.Minor, Math.Max(version.Build, 0));

    public static bool IsNewer(Version candidate, Version current) =>
        Normalize(candidate) > Normalize(current);
}
