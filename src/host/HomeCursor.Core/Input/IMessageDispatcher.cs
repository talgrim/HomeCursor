using HomeCursor.Protocol.Messages;

namespace HomeCursor.Core.Input;

/// <summary>
/// Routes messages received from the connected client to the code that acts on them.
/// </summary>
public interface IMessageDispatcher
{
    /// <summary>Handles one message; messages that carry no input are ignored.</summary>
    void Dispatch(ProtocolMessage message);

    /// <summary>Releases every mouse button and key still held down, for example after a client disconnects.</summary>
    void ReleaseAll();
}
