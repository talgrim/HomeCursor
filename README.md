# HomeCursor

Control your PC's mouse and keyboard from your phone over the local network.

HomeCursor is made of two parts:

| Part | Tech | Role |
|------|------|------|
| **Host** | C# (.NET) | Runs on the PC, accepts connections from the client and injects mouse and keyboard input. |
| **Client** | Kotlin (Android) | Runs on the phone and acts as a touchpad and keyboard. |

Both devices must be on the same local network. No cloud, no account, no internet connection is required.

> **Status:** early development. The host can receive messages from a client on the local network and inject mouse and keyboard input (see [docs/protocol.md](docs/protocol.md)); the Android client is not implemented yet. The connection is **not authenticated or encrypted** yet, so use it only on networks you trust.

## Planned features

- Touchpad: move, tap to click, two-finger scroll, right click
- On-screen keyboard with text input and special keys
- Automatic discovery of hosts on the local network
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
