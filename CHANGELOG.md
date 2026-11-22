# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.4.0](https://github.com/talgrim/HomeCursor/compare/v1.3.0...v1.4.0) (2026-11-22)


### Features

* **client:** check for updates and install them from the app ([6ca3e80](https://github.com/talgrim/HomeCursor/commit/6ca3e80099831bd81740919a155cf58608fcd6dc))
* **client:** check for updates and install them from the app ([2076102](https://github.com/talgrim/HomeCursor/commit/207610245101645ad60afbee4426a55525356d6b))
* **host:** add a Settings window to the tray menu ([142d1eb](https://github.com/talgrim/HomeCursor/commit/142d1ebd7df47566b0496bb30d112b3f702a67b9))
* **host:** apply and save new settings while the host runs ([f5c26e8](https://github.com/talgrim/HomeCursor/commit/f5c26e8e1825e01b0e8444188a115d79cd248ee7))
* **host:** check for updates and update the host from the tray ([d643c99](https://github.com/talgrim/HomeCursor/commit/d643c9909db6abaa868ba86f5f051be94bb6c789))
* **host:** check for updates and update the host from the tray ([d5cd625](https://github.com/talgrim/HomeCursor/commit/d5cd625b4f77d8bd7a5092a24fd01ae4ce46f36a))
* **host:** let the update monitor follow the CheckForUpdates setting ([ae2fb6f](https://github.com/talgrim/HomeCursor/commit/ae2fb6f3632d3350f195beb93684f66a238ae3ee))
* **host:** list every invalid setting with GetErrors ([48b7d3d](https://github.com/talgrim/HomeCursor/commit/48b7d3d432062ea6adca5771f2b698a32f01cf9b))
* **host:** read settings through a swappable holder ([af9bda6](https://github.com/talgrim/HomeCursor/commit/af9bda664beada9f54f6773876375371d5c26fa7))
* **host:** save settings without losing comments or unknown keys ([885eb95](https://github.com/talgrim/HomeCursor/commit/885eb9583b78bde2ae72fff3dbfef65578c1f2a3))
* **host:** settings window in the tray menu ([d837af4](https://github.com/talgrim/HomeCursor/commit/d837af4d58bc156906ea1aa5faf5910681767700))
* **host:** sign host releases and verify the signature before updating ([4493878](https://github.com/talgrim/HomeCursor/commit/449387845dbc0172d9bdad13b18af4aff0e59a90))
* **host:** verify the release signature before installing an update ([f88c8f5](https://github.com/talgrim/HomeCursor/commit/f88c8f56a769fd9f124c60ff31f3d2bb73b80b08))


### Bug Fixes

* **host:** default NotifyOnClientDisconnection to false ([4ea2e32](https://github.com/talgrim/HomeCursor/commit/4ea2e32d53746d4299e8e37a40a757410bf1db72))

## [1.3.0](https://github.com/talgrim/HomeCursor/compare/v1.2.0...v1.3.0) (2026-11-22)


### Features

* **client:** keep keyboard modifiers held until tapped again ([309fcde](https://github.com/talgrim/HomeCursor/commit/309fcde6042d421f4fc0cb18f5b77b4dbc2d3d97))
* **client:** keep keyboard modifiers held until tapped again ([2ddb96c](https://github.com/talgrim/HomeCursor/commit/2ddb96cfbb6ad8479d4b73af001fcd595af450ff))
* **client:** make the shortcuts view a screen that survives lock and unlock ([e550d47](https://github.com/talgrim/HomeCursor/commit/e550d4706bcf615231c73d1b9da37b92427975c0))
* **client:** make the shortcuts view a screen that survives lock and unlock ([bee0a7a](https://github.com/talgrim/HomeCursor/commit/bee0a7a4c74a42512346930582650854812403d2))

## [1.2.0](https://github.com/talgrim/HomeCursor/compare/v1.1.0...v1.2.0) (2026-11-19)


### Features

* **client:** add guided tutorial of the trackpad (1.1.0) ([4eedcc9](https://github.com/talgrim/HomeCursor/commit/4eedcc9f0cbe857d6cb890fb80b939e8cd342770))
* **host:** add opt-in diagnostics log for pointer lag ([1ce7d76](https://github.com/talgrim/HomeCursor/commit/1ce7d7693221018677260b5025f038b1187374b2))
* **host:** add opt-in diagnostics log for pointer lag ([8a051d1](https://github.com/talgrim/HomeCursor/commit/8a051d1e491168fb36d2553fad5ced462ba0073e))

## [Unreleased]

## [1.1.0]

### Added

- Android client: guided tour of the touchpad (gestures, buttons, keyboard, settings) that starts the first time the touchpad is shown and can be skipped; a Tutorial item in the settings replays it.

## [1.0.0]

First public release.

### Added

- Windows tray host that accepts one client at a time and injects mouse and keyboard input.
- Android client with automatic host discovery, reconnecting to the last host, touchpad
  (move, click, double click, right click, scroll, drag, hold buttons) and keyboard
  (shortcut list in portrait, on-screen keyboard in landscape).
