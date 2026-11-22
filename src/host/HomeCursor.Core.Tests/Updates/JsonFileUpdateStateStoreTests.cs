using HomeCursor.Core.Tests.Support;
using HomeCursor.Core.Updates;
using Microsoft.Extensions.Logging.Abstractions;

namespace HomeCursor.Core.Tests.Updates;

public sealed class JsonFileUpdateStateStoreTests
{
    [Fact]
    public void LastCheck_NoFile_IsNull()
    {
        using var dir = new TempDirectory();

        Assert.Null(new JsonFileUpdateStateStore(dir.File("state.json"), NullLogger<JsonFileUpdateStateStore>.Instance).LastCheck);
    }

    [Fact]
    public void SaveLastCheck_ThenRead_ReturnsSavedTime_AcrossInstances()
    {
        using var dir = new TempDirectory();
        var time = new DateTimeOffset(2026, 11, 22, 10, 30, 0, TimeSpan.Zero);
        var path = dir.File(Path.Combine("sub", "state.json"));

        new JsonFileUpdateStateStore(path, NullLogger<JsonFileUpdateStateStore>.Instance).SaveLastCheck(time);

        Assert.Equal(time, new JsonFileUpdateStateStore(path, NullLogger<JsonFileUpdateStateStore>.Instance).LastCheck);
    }

    [Fact]
    public void LastCheck_CorruptFile_IsNull()
    {
        using var dir = new TempDirectory();
        File.WriteAllText(dir.File("state.json"), "{{{");

        Assert.Null(new JsonFileUpdateStateStore(dir.File("state.json"), NullLogger<JsonFileUpdateStateStore>.Instance).LastCheck);
    }
}
