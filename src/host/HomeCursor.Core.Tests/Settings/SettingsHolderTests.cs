using HomeCursor.Core.Settings;

namespace HomeCursor.Core.Tests.Settings;

public sealed class SettingsHolderTests
{
    [Fact]
    public void Value_IsTheInitialSettings()
    {
        var initial = new HostSettings { HostName = "A" };

        Assert.Same(initial, new SettingsHolder(initial).Value);
    }

    [Fact]
    public void Update_ReplacesValueAndRaisesChanged()
    {
        var holder = new SettingsHolder(new HostSettings());
        var updated = new HostSettings { HostName = "B" };
        HostSettings? raised = null;
        holder.Changed += (_, settings) => raised = settings;

        holder.Update(updated);

        Assert.Same(updated, holder.Value);
        Assert.Same(updated, raised);
    }

    [Fact]
    public void Update_Null_Throws()
    {
        var holder = new SettingsHolder(new HostSettings());

        Assert.Throws<ArgumentNullException>(() => holder.Update(null!));
    }
}
