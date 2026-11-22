namespace HomeCursor.Core.Updates;

public interface IUpdateSignatureVerifier
{
    /// <summary>Whether <paramref name="signature"/> is a valid release signature for a zip of <paramref name="version"/> with this SHA-256.</summary>
    bool IsValid(Version version, string sha256, byte[] signature);
}
