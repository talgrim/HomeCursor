# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

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
