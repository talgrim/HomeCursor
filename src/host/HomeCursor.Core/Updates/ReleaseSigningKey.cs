namespace HomeCursor.Core.Updates;

/// <summary>
/// The key that signs host releases. The public half is built in, so a host only installs an update that the
/// release pipeline signed; the private half is the repository secret <c>UPDATE_SIGNING_KEY</c>
/// (see <c>scripts/sign-host-update.sh</c> and the README for how to make or replace the pair).
/// </summary>
public static class ReleaseSigningKey
{
    /// <summary>ECDSA P-256 public key, base64 of the DER SubjectPublicKeyInfo.</summary>
    public const string PublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAErl/SXLoNqHC/xWplCDyBJ8YtMajZHQ04uwgV6WKcUNX1sICiY6oY5BWp4n3g/Ma0mBDuN6z6q+NgZdkU6rCgIw==";
}
