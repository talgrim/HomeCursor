# HomeCursor

Control your PC's mouse and keyboard from your phone over the local network.

HomeCursor is made of two parts:

| Part | Tech | Role |
|------|------|------|
| **Host** | C# (.NET) | Runs on the PC, accepts connections from the client and injects mouse and keyboard input. |
| **Client** | Kotlin (Android) | Runs on the phone and acts as a touchpad and keyboard. |

Both devices must be on the same local network. No cloud, no account, no internet connection is required.

> **Status:** version 1.1.0 (see [CHANGELOG.md](CHANGELOG.md)). The host receives messages from a client on the local network and injects mouse and keyboard input (see [docs/protocol.md](docs/protocol.md)); the Android client discovers a host on the network, connects to it, reconnects to it on the next start and works as a touchpad and keyboard (shortcut list in portrait, on-screen keyboard in landscape). The host serves one client at a time. The connection is **not authenticated or encrypted**, so use it only on networks you trust.

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
| **Left** / **Right** / **Mid** buttons (a mouse icon with the button's zone filled) | Hold a mouse button (Mid is the middle button, e.g. to pan or open links in a new tab); combine with a finger on the pad to drag |

A tap waits briefly to see whether you touch again to drag. That is why its click comes a moment late: sending it at
once would make the start of the drag a second press, which Windows treats as a double click (a window's title bar
would maximise instead of moving).

The gear in the upper right corner of the pad opens the settings: pointer speed (0.5× to 3×, 1× by default) and natural scrolling (off by default: fingers
moving down scroll down, like a mouse wheel), a Tutorial item that replays the guided tour, an Updates section (current version, **Check for updates**, a switch for the automatic check) plus Disconnect and Forget this PC. The tour (the gestures, the buttons and the settings, step by step, with a Skip option) starts by itself the first time the touchpad is shown after connecting.

The app checks for a newer version when it starts (at most once a day, switchable in the settings). A new version shows as a card on the PC list and a dot on the gear; **Update** downloads it and opens Android's installer. The first time, Android asks you to allow HomeCursor to install apps; every update is confirmed in the system dialog.

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
├── scripts/                        # Release helpers (publish-host.sh, generate-host-icon.py)
├── CHANGELOG.md
├── LICENSE
└── README.md
```

## Getting started

### Host

Requirements: [.NET 9 SDK](https://dotnet.microsoft.com/download), Windows (to run; building also works on Linux/WSL).

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

## Releasing

Releases are automated with [release-please](https://github.com/googleapis/release-please). Merge `develop` into
`main`; the `Release` workflow then opens (or updates) a release PR on `main` that bumps the version in
`HomeCursor.Host.Tray.csproj` and `src/client/app/build.gradle.kts` (`versionCode` is derived from the version) and
writes [CHANGELOG.md](CHANGELOG.md) from the Conventional Commits (`feat` = minor, `fix` = patch, `!` = major).
Merging that PR tags the release, builds the host zip and the signed APK and publishes both to
[wip.talgrim.dev](https://wip.talgrim.dev) (and so to the talgrim.dev download links). After that, merge `main` back
into `develop` so the bumped version is there too. Repository secrets used: `WIP_FLY_API_TOKEN`,
`ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The repository setting "Allow GitHub
Actions to create and approve pull requests" must be on for the release PR.

To build by hand:

**Host** (Windows zip with a self-contained single-file exe, no .NET install needed):

```bash
scripts/publish-host.sh          # writes artifacts/HomeCursor-Host-win-x64.zip
```

**Client** (signed release APK): create the release keystore once and copy `src/client/keystore.properties.example`
to `src/client/keystore.properties` with its details (both the keystore and the properties file are gitignored; back
the keystore up, an app can only be updated with the key it was first signed with). Then:

```bash
cd src/client
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

The host's first start makes Windows ask to allow it through the firewall (TCP 47800, UDP 47801 and 47802). The exe is
not code-signed, so Windows SmartScreen may warn on first run.

## Contributing

- Branch from `develop` and open pull requests against `develop`. `main` is reserved for releases.
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

## License

[MIT](LICENSE). Free to use.
