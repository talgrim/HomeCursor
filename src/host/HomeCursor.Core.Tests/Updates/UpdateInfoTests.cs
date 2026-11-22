using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Updates;

public sealed class UpdateInfoTests
{
    [Fact]
    public void SignatureUri_IsTheDownloadAddressPlusSig()
    {
        var info = new UpdateInfo(new Version(1, 4, 0), new Uri("https://example.test/files/homecursor-host-1.4.0-win-x64.zip"), new string('a', 64), 1);

        Assert.Equal(new Uri("https://example.test/files/homecursor-host-1.4.0-win-x64.zip.sig"), info.SignatureUri);
    }
}
