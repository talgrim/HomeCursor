# HomeCursor protocol (version 1)

How a client (phone) talks to the host (PC) on the local network. The reference implementation is
`src/host/HomeCursor.Protocol`; this document is the language-neutral specification for other clients.

> **Security:** version 1 has **no authentication and no encryption**. Any device on the LAN can connect and
> control the PC's mouse and keyboard while the host is running and no other client is connected.
> Only run the host on networks you trust. Pairing and encryption are planned.

## Transports

| Purpose | Transport | Default port | Configurable |
|---------|-----------|--------------|--------------|
| Control (handshake, keep-alive, clicks, keys, text) | TCP | 47800 | `TcpPort` |
| Pointer movement and scrolling | UDP | 47801 | `UdpPort` |
| Host discovery | UDP broadcast | 47802 | `DiscoveryPort` |

Everything is little-endian. A **message** is one type byte followed by its fields (below).
`string` is a `UInt16` byte count followed by that many UTF-8 bytes (at most 2048). A message is at most 8192 bytes.
`bool` is one byte, 0 or 1.

## Messages

| Type | Name | Direction | Fields |
|-----:|------|-----------|--------|
| 1 | Hello | client → host | `byte protocolVersion`, `string clientName` |
| 2 | Welcome | host → client | `byte protocolVersion`, `UInt32 sessionId`, `UInt16 udpPort`, `byte pingIntervalSeconds` |
| 3 | Reject | host → client | `byte reason` (1 = Busy, 2 = UnsupportedVersion) |
| 4 | Ping | client → host | none |
| 5 | Pong | host → client | none |
| 16 | MouseMove | client → host | `Int16 dx`, `Int16 dy` (relative, pixels) |
| 17 | MouseButton | client → host | `byte button` (1 = Left, 2 = Right, 3 = Middle), `bool isDown` |
| 18 | Scroll | client → host | `Int16 horizontal`, `Int16 vertical` (1/120 of a wheel notch; positive = up / right) |
| 19 | Key | client → host | `byte key` (see below), `bool isDown` |
| 20 | Text | client → host | `string text` (typed character by character) |
| 32 | DiscoveryProbe | client → host | `byte protocolVersion` |
| 33 | DiscoveryReply | host → client | `byte protocolVersion`, `string hostName`, `UInt16 tcpPort`, `UInt16 udpPort`, `bool isBusy` |

Key codes: Enter 1, Backspace 2, Tab 3, Escape 4, Delete 5, Insert 6, Left 7, Right 8, Up 9, Down 10, Home 11,
End 12, PageUp 13, PageDown 14, Shift 15, Control 16, Alt 17, Windows 18, F1-F12 32-43.
Hold a modifier by sending `isDown = true`, then `isDown = false` when released.

## TCP control connection

Each TCP frame is a `UInt16` length followed by one message of that length (1 to 8192 bytes).

1. The client connects and immediately sends **Hello** with `protocolVersion = 1`.
2. The host answers with **Welcome**, or with **Reject** and closes the connection:
   - `Busy`: another client is already connected. **The first client to connect wins**; others are refused until it disconnects.
   - `UnsupportedVersion`: the versions differ.
3. The client sends **Ping** every `pingIntervalSeconds` (default 5); the host answers **Pong**.
   If the host receives nothing for `HeartbeatTimeoutSeconds` (default 15) it closes the connection and frees the slot.
4. After Welcome the client may send MouseMove, MouseButton, Scroll, Key and Text over the same connection.
5. The host closes the connection on any malformed frame, an unexpected second Hello, or when it stops.
   On disconnect the host releases every mouse button and key that was still held down.

The first message must be Hello; the host waits at most `HeartbeatTimeoutSeconds` for it.

## UDP pointer datagrams

High-rate pointer events can be sent as UDP datagrams to `udpPort` (from Welcome) instead of TCP:

```
UInt32 sessionId   (from Welcome)
UInt32 sequence    (starts anywhere, increases by 1 per datagram, wraps)
message            (MouseMove or Scroll only)
```

The host ignores a datagram unless **all** of these hold: it comes from the same IP address as the TCP connection,
`sessionId` matches the active session, the message is MouseMove or Scroll, and `sequence` is newer than every
sequence already accepted (duplicates and late datagrams are dropped; comparison is wrap-around safe).
There are no replies. Clicks, keys and text must use TCP because they must not be lost.

## Discovery

The client broadcasts a datagram to `255.255.255.255:<discoveryPort>` (and/or the subnet broadcast address):

```
"HCUR"             (4 ASCII bytes)
message            (DiscoveryProbe)
```

Each host replies by unicast to the sender's address and port with the same `"HCUR"` prefix and a **DiscoveryReply**.
`isBusy = true` means a client is already connected and a new connection would be rejected.
The client then connects to `<reply source address>:<tcpPort>`.

## Host settings

The Windows host reads `settings.json` from its own directory at startup. The file ships with the app (source:
`src/host/HomeCursor.Host.Tray/settings.json`); if it is missing, it is recreated with defaults:

```json
{
  "BindAddress": "0.0.0.0",
  "TcpPort": 47800,
  "UdpPort": 47801,
  "DiscoveryPort": 47802,
  "HostName": "MY-PC",
  "PingIntervalSeconds": 5,
  "HeartbeatTimeoutSeconds": 15,
  "NotifyOnClientConnection": true,
  "NotifyOnClientDisconnection": true
}
```

Every property is optional. Ports must be 1-65535, `UdpPort` must differ from `DiscoveryPort`, and
`HeartbeatTimeoutSeconds` must be greater than `PingIntervalSeconds`. `NotifyOnClientConnection` and `NotifyOnClientDisconnection` (both default `true`) show a tray notification when a client connects or disconnects;
the tray menu and icon always show the connected client. Invalid settings stop the host at startup with a message.

## Platform notes (Windows host)

- The first start may trigger a Windows Firewall prompt. Allow **private networks**; public profiles block inbound traffic by default.
- Input cannot be injected into windows of elevated (administrator) programs, the UAC prompt or the lock screen
  unless the host itself runs elevated.
