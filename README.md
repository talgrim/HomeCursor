# HomeCursor

Control your PC's mouse and keyboard from your phone over the local network.

HomeCursor is made of two parts:

| Part | Tech | Role |
|------|------|------|
| **Host** | C# (.NET) | Runs on the PC, accepts connections from the client and injects mouse and keyboard input. |
| **Client** | Kotlin (Android) | Runs on the phone and acts as a touchpad and keyboard. |

Both devices must be on the same local network. No cloud, no account, no internet connection is required.

> **Status:** early development. The host can receive messages from a client on the local network and inject mouse and keyboard input (see [docs/protocol.md](docs/protocol.md)); the Android client can discover a host on the network, connect to it, reconnect to it on the next start and use the phone as a touchpad (the keyboard is not implemented yet). The connection is **not authenticated or encrypted** yet, so use it only on networks you trust.

## Features

- Automatic discovery of hosts on the local network, and reconnecting to the last one
- Touchpad on the phone (see below)

### Using the touchpad

| Gesture | Action |
|---------|--------|
| Drag one finger | Move the pointer (faster swipes move it further) |
| Tap | Left click (sent about a quarter of a second after the tap, see below) |
| Tap twice | Double click |
| Tap with two fingers | Right click |
| Drag two fingers | Scroll, vertically and horizontally |
| Tap, then touch again and drag | Drag with the left button held (select text, move windows) |
| **Left** / **Right** buttons | Hold a mouse button; combine with a finger on the pad to drag |

A tap waits briefly to see whether you touch again to drag. That is why its click comes a moment late: sending it at
once would make the start of the drag a second press, which Windows treats as a double click (a window's title bar
would maximise instead of moving).

The menu button opens the settings: pointer speed (0.5× to 3×, 1× by default) and natural scrolling (off by default: fingers
moving down scroll down, like a mouse wheel), plus Disconnect and Forget this PC.

## Planned features

- On-screen keyboard with text input and special keys
- Pairing with a PIN or QR code so only trusted phones can connect
- Encrypted LAN communication

## Repository layout

```
HomeCursor/
├── src/
│   ├── host/                       # C# host (HomeCursor.Host.sln)
│   │   ├── HomeCursor.Protocol/    # Wire protocol: messages and binary codec
│   │   ├── HomeCursor.Core/        # Host logic: networking loops, sessions, settings, input dispatch
│   │   ├── HomeCursor.Host.Windows/ # Windows input injection (SendInput)
│   │   ├── HomeCursor.Host.Tray/   # WinForms tray-only shell
│   │   └── *.Tests/                # xUnit tests for Protocol and Core
│   └── client/                     # Kotlin + Jetpack Compose Android client (Gradle)
├── docs/                           # Protocol specification (protocol.md)
└── README.md
```

## Getting started

### Host

Requirements: [.NET SDK](https://dotnet.microsoft.com/download) (version to be decided), Windows.

```bash
cd src/host
dotnet run --project HomeCursor.Host.Tray
```

### Client

Requirements: [Android Studio](https://developer.android.com/studio), an Android device or emulator.

Open the `src/client/` directory in Android Studio and run the app, or build from the command line:

```bash
cd src/client
./gradlew assembleDebug   # gradlew.bat on Windows
```

## Contributing

- Branch from `develop` and open pull requests against `develop`. `main` is reserved for releases.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

## License

To be decided.
