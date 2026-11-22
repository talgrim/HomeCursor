using System.Text.Json;

namespace HomeCursor.Core.Updates;

/// <summary>
/// Reads the release manifest: <c>{"files": [{"product", "version", "suffix", "file", "sha256", "size", ...}]}</c>.
/// </summary>
public static class UpdateManifestParser
{
    /// <summary>
    /// Finds the entry with the highest version for a product and file suffix. Entries that are malformed
    /// (no valid version, no SHA-256, a file name that is not a plain name) are skipped.
    /// </summary>
    /// <param name="manifestUri">Address of the manifest; files are served next to it.</param>
    /// <exception cref="JsonException">The manifest is not valid JSON.</exception>
    public static UpdateInfo? FindLatest(string json, Uri manifestUri, string product, string suffix)
    {
        using var document = JsonDocument.Parse(json);
        if (document.RootElement.ValueKind != JsonValueKind.Object ||
            !document.RootElement.TryGetProperty("files", out var files) ||
            files.ValueKind != JsonValueKind.Array)
        {
            return null;
        }

        UpdateInfo? best = null;
        foreach (var entry in files.EnumerateArray())
        {
            var candidate = TryRead(entry, manifestUri, product, suffix);
            if (candidate is not null && (best is null || candidate.Version > best.Version))
            {
                best = candidate;
            }
        }

        return best;
    }

    private static UpdateInfo? TryRead(JsonElement entry, Uri manifestUri, string product, string suffix)
    {
        if (entry.ValueKind != JsonValueKind.Object ||
            GetString(entry, "product") != product ||
            GetString(entry, "suffix") != suffix ||
            !Version.TryParse(GetString(entry, "version"), out var version) ||
            GetString(entry, "file") is not { } file ||
            GetString(entry, "sha256") is not { } sha256 ||
            !IsSha256(sha256) ||
            !IsPlainFileName(file))
        {
            return null;
        }

        var size = entry.TryGetProperty("size", out var sizeElement) && sizeElement.TryGetInt64(out var value) ? value : 0;
        return new UpdateInfo(UpdateVersions.Normalize(version), new Uri(manifestUri, file), sha256, size);
    }

    private static string? GetString(JsonElement entry, string name) =>
        entry.TryGetProperty(name, out var property) && property.ValueKind == JsonValueKind.String
            ? property.GetString()
            : null;

    private static bool IsSha256(string text) =>
        text.Length == 64 && text.All(char.IsAsciiHexDigit);

    // The file is resolved next to the manifest, so it must not be able to point anywhere else.
    private static bool IsPlainFileName(string name) =>
        name.Length > 0 && name.IndexOfAny(['/', '\\', ':', '?', '#', '%']) < 0 && name != "." && name != "..";
}
