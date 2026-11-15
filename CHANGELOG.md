# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

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
