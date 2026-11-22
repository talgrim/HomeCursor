namespace HomeCursor.Core.Updates;

/// <summary>A released host version that can be downloaded.</summary>
/// <param name="Version">Version of the release.</param>
/// <param name="DownloadUri">Where the zip with the new executable is served.</param>
/// <param name="Sha256">Expected SHA-256 of the zip, as hex.</param>
/// <param name="Size">Size of the zip in bytes.</param>
public sealed record UpdateInfo(Version Version, Uri DownloadUri, string Sha256, long Size);
