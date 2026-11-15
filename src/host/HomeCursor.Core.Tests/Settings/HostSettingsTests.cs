using HomeCursor.Core.Settings;

namespace HomeCursor.Core.Tests.Settings;

public class HostSettingsTests
{
    [Fact]
    public void Defaults_AreValid()
    {
        new HostSettings().Validate();
    }

    public static TheoryData<HostSettings> InvalidSettings => new()
    {
        new HostSettings { BindAddress = "not-an-ip" },
        new HostSettings { TcpPort = 0 },
        new HostSettings { UdpPort = 70000 },
        new HostSettings { DiscoveryPort = -1 },
        new HostSettings { UdpPort = 5000, DiscoveryPort = 5000 },
        new HostSettings { HostName = " " },
        new HostSettings { HostName = new string('x', 65) },
        new HostSettings { PingIntervalSeconds = 0 },
        new HostSettings { PingIntervalSeconds = 256, HeartbeatTimeoutSeconds = 1000 },
        new HostSettings { PingIntervalSeconds = 10, HeartbeatTimeoutSeconds = 10 }
    };

    [Theory]
    [MemberData(nameof(InvalidSettings))]
    public void Validate_InvalidSettings_Throws(HostSettings settings)
    {
        Assert.Throws<SettingsException>(settings.Validate);
    }

    [Fact]
    public void TcpAndUdpPort_MayBeEqual()
    {
        new HostSettings { TcpPort = 6000, UdpPort = 6000 }.Validate();
    }

    [Fact]
    public void NotifyOnClientConnection_DefaultsToTrue()
    {
        Assert.True(new HostSettings().NotifyOnClientConnection);
    }

    [Fact]
    public void NotifyOnClientDisconnection_DefaultsToTrue()
    {
        Assert.True(new HostSettings().NotifyOnClientDisconnection);
    }
}
