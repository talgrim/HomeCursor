# HomeCursor

Control your PC's mouse and keyboard from your phone over the local network.

HomeCursor is made of two parts:

| Part | Tech | Role |
|------|------|------|
| **Host** | C# (.NET) | Runs on the PC, accepts connections from the client and injects mouse and keyboard input. |
| **Client** | Kotlin (Android) | Runs on the phone and acts as a touchpad and keyboard. |

Both devices must be on the same local network. No cloud, no account, no internet connection is required.

> **Status:** early development. Nothing is implemented yet.

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
│   │   ├── HomeCursor.Core/        # Host logic: networking, protocol, input injection
│   │   ├── HomeCursor.Host.Tray/   # WinForms tray-only shell
│   │   └── HomeCursor.Core.Tests/  # xUnit tests for Core
│   └── client/                     # Kotlin Android client (planned)
├── docs/                           # Protocol and design notes (planned)
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

Open the `src/client/` directory in Android Studio and run the app.

## Contributing

- Branch from `develop` and open pull requests against `develop`. `main` is reserved for releases.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

## License

To be decided.
