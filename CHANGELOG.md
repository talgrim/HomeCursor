# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

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
