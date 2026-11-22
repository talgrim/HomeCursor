using System.Security.Cryptography;
using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Updates;

public sealed class EcdsaUpdateSignatureVerifierTests : IDisposable
{
    private static readonly Version Release = new(1, 4, 0);
    private static readonly string Hash = new('a', 64);

    private readonly ECDsa _key = ECDsa.Create(ECCurve.NamedCurves.nistP256);

    public void Dispose() => _key.Dispose();

    private EcdsaUpdateSignatureVerifier Verifier => new(Convert.ToBase64String(_key.ExportSubjectPublicKeyInfo()));

    private static byte[] Sign(ECDsa key, Version version, string hash) =>
        key.SignData(UpdateSignature.GetPayload(version, hash), HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence);

    [Fact]
    public void IsValid_SignatureOfTheSameVersionAndHash_IsTrue()
    {
        Assert.True(Verifier.IsValid(Release, Hash, Sign(_key, Release, Hash)));
    }

    [Fact]
    public void IsValid_HashInOtherCase_IsStillTrue()
    {
        Assert.True(Verifier.IsValid(Release, Hash.ToUpperInvariant(), Sign(_key, Release, Hash)));
    }

    [Fact]
    public void IsValid_OtherHash_IsFalse()
    {
        Assert.False(Verifier.IsValid(Release, new string('b', 64), Sign(_key, Release, Hash)));
    }

    [Fact]
    public void IsValid_OtherVersion_IsFalse()
    {
        Assert.False(Verifier.IsValid(new Version(1, 5, 0), Hash, Sign(_key, Release, Hash)));
    }

    [Fact]
    public void IsValid_SignedByAnotherKey_IsFalse()
    {
        using var other = ECDsa.Create(ECCurve.NamedCurves.nistP256);

        Assert.False(Verifier.IsValid(Release, Hash, Sign(other, Release, Hash)));
    }

    [Theory]
    [InlineData(new byte[0])]
    [InlineData(new byte[] { 1, 2, 3 })]
    public void IsValid_MalformedSignature_IsFalse(byte[] signature)
    {
        Assert.False(Verifier.IsValid(Release, Hash, signature));
    }
}
