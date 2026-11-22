using HomeCursor.Core.Tests.Support;
using HomeCursor.Core.Updates;

namespace HomeCursor.Core.Tests.Updates;

public sealed class ExecutableReplacerTests
{
    [Fact]
    public void Replace_PutsNewFileInPlaceAndKeepsOldOne()
    {
        using var dir = new TempDirectory();
        File.WriteAllText(dir.File("app.exe"), "old");
        File.WriteAllText(dir.File("app.exe.new"), "new");

        ExecutableReplacer.Replace(dir.File("app.exe"), dir.File("app.exe.new"));

        Assert.Equal("new", File.ReadAllText(dir.File("app.exe")));
        Assert.Equal("old", File.ReadAllText(dir.File("app.exe.old")));
        Assert.False(File.Exists(dir.File("app.exe.new")));
    }

    [Fact]
    public void Replace_OverwritesLeftoverFromEarlierUpdate()
    {
        using var dir = new TempDirectory();
        File.WriteAllText(dir.File("app.exe"), "current");
        File.WriteAllText(dir.File("app.exe.old"), "ancient");
        File.WriteAllText(dir.File("app.exe.new"), "new");

        ExecutableReplacer.Replace(dir.File("app.exe"), dir.File("app.exe.new"));

        Assert.Equal("current", File.ReadAllText(dir.File("app.exe.old")));
    }

    [Fact]
    public void Replace_NewFileMissing_RestoresCurrentFile()
    {
        using var dir = new TempDirectory();
        File.WriteAllText(dir.File("app.exe"), "current");

        Assert.Throws<UpdateException>(() => ExecutableReplacer.Replace(dir.File("app.exe"), dir.File("missing.new")));

        Assert.Equal("current", File.ReadAllText(dir.File("app.exe")));
    }

    [Fact]
    public void Replace_CurrentFileMissing_ThrowsUpdateException()
    {
        using var dir = new TempDirectory();
        File.WriteAllText(dir.File("app.exe.new"), "new");

        Assert.Throws<UpdateException>(() => ExecutableReplacer.Replace(dir.File("app.exe"), dir.File("app.exe.new")));
    }

    [Fact]
    public void DeleteLeftover_RemovesOldFile_AndToleratesNone()
    {
        using var dir = new TempDirectory();
        File.WriteAllText(dir.File("app.exe.old"), "old");

        ExecutableReplacer.DeleteLeftover(dir.File("app.exe"));
        ExecutableReplacer.DeleteLeftover(dir.File("app.exe"));

        Assert.False(File.Exists(dir.File("app.exe.old")));
    }
}
