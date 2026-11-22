using System.Text;

namespace HomeCursor.Core.Updates;

/// <summary>What a release signature covers.</summary>
public static class UpdateSignature
{
    /// <summary>
    /// The text that is signed: the version and the SHA-256 of the zip. Signing the version as well keeps a
    /// correctly signed old zip from being passed off as a newer release.
    /// <c>scripts/sign-host-update.sh</c> builds the same text.
    /// </summary>
    public static byte[] GetPayload(Version version, string sha256) =>
        Encoding.UTF8.GetBytes($"HomeCursor.Host|{version}|{sha256.ToLowerInvariant()}");
}
