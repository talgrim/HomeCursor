using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Updates;

public sealed class ReleaseSigningKeyTests
{
    // Made by scripts/sign-host-update.sh with the release key, for a zip containing the text "fixture zip bytes".
    private const string FixtureHash = "2a0b19afdfe6ba2d01a822bf194bf3a6e59fb0cf1dd907141a6f081a735bfd88";

    private const string FixtureSignature =
        "MEQCIDoaHdL+/+ritdAg1Bk4iLHGAWx+krJdtG4nyAB/i6ucAiAFLSyHakouxRoftne1pJMbrMdLEEowjQsL4f/VhAmpmA==";

    [Fact]
    public void PublicKey_VerifiesASignatureMadeByTheReleaseScript()
    {
        var verifier = new EcdsaUpdateSignatureVerifier(ReleaseSigningKey.PublicKey);

        Assert.True(verifier.IsValid(new Version(1, 4, 0), FixtureHash, Convert.FromBase64String(FixtureSignature)));
    }

    [Fact]
    public void PublicKey_RejectsTheSameSignatureForAnotherVersion()
    {
        var verifier = new EcdsaUpdateSignatureVerifier(ReleaseSigningKey.PublicKey);

        Assert.False(verifier.IsValid(new Version(1, 5, 0), FixtureHash, Convert.FromBase64String(FixtureSignature)));
    }
}
