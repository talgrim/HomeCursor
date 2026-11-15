namespace HomeCursor.Protocol;

public enum MessageType : byte
{
    Hello = 1,
    Welcome = 2,
    Reject = 3,
    Ping = 4,
    Pong = 5,

    MouseMove = 16,
    MouseButton = 17,
    Scroll = 18,
    Key = 19,
    Text = 20,

    DiscoveryProbe = 32,
    DiscoveryReply = 33
}
