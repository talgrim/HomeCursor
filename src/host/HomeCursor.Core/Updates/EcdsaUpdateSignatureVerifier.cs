using System.Security.Cryptography;

namespace HomeCursor.Core.Updates;

/// <summary>
/// Checks ECDSA P-256 / SHA-256 signatures (DER encoded, as <c>openssl dgst -sha256 -sign</c> writes them)
/// against a public key.
/// </summary>
public sealed class EcdsaUpdateSignatureVerifier : IUpdateSignatureVerifier
{
    private readonly byte[] _publicKey;

    /// <param name="publicKeyBase64">Base64 of the DER SubjectPublicKeyInfo of the key.</param>
    public EcdsaUpdateSignatureVerifier(string publicKeyBase64) => _publicKey = Convert.FromBase64String(publicKeyBase64);

    public bool IsValid(Version version, string sha256, byte[] signature)
    {
        try
        {
            using var key = ECDsa.Create();
            key.ImportSubjectPublicKeyInfo(_publicKey, out _);
            return key.VerifyData(
                UpdateSignature.GetPayload(version, sha256),
                signature,
                HashAlgorithmName.SHA256,
                DSASignatureFormat.Rfc3279DerSequence);
        }
        catch (CryptographicException)
        {
            // A signature that is not even well-formed is simply not valid.
            return false;
        }
    }
}
