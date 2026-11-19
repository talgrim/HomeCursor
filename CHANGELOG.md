# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.2.0](https://github.com/talgrim/HomeCursor/compare/v1.1.0...v1.2.0) (2026-11-19)


### Features

* **client:** add brand theme with light and dark schemes ([7959c34](https://github.com/talgrim/HomeCursor/commit/7959c345c052c7ca6c5627158ddac768bdfc9007))
* **client:** add connection chip and launcher icon ([c6366a6](https://github.com/talgrim/HomeCursor/commit/c6366a60e163435ebae23180c3dd59363f5b8681))
* **client:** add guided tutorial of the trackpad ([2095a34](https://github.com/talgrim/HomeCursor/commit/2095a3479ff002c8aeed3171e2bf9807ea1299cf))
* **client:** add guided tutorial of the trackpad (1.1.0) ([4eedcc9](https://github.com/talgrim/HomeCursor/commit/4eedcc9f0cbe857d6cb890fb80b939e8cd342770))
* **client:** add host list and connection UI ([1e62cca](https://github.com/talgrim/HomeCursor/commit/1e62cca55c72d2cb60158289d9de205b28e6b404))
* **client:** add InputSender over TCP and UDP ([11a061b](https://github.com/talgrim/HomeCursor/commit/11a061b59da8582a36225c736fbc9b4da3475b73))
* **client:** add middle mouse button and rework the button strip ([b1515d7](https://github.com/talgrim/HomeCursor/commit/b1515d7b3b05ed9003f78fb186d973013a8ecdcc))
* **client:** add middle mouse button and rework the button strip ([c477554](https://github.com/talgrim/HomeCursor/commit/c4775543e1d64728ba955bfb8e38549fdc5a2d62))
* **client:** add motion accumulator and gesture interpreter ([c5d5eb6](https://github.com/talgrim/HomeCursor/commit/c5d5eb6d27f9ac609d9710f7df1581389a8e5def))
* **client:** add protocol codec and framing ([be87e6b](https://github.com/talgrim/HomeCursor/commit/be87e6b35f5123f9de3ab5f5c82237765ca2fe25))
* **client:** add shortcut list and on-screen keyboard ([cf9b548](https://github.com/talgrim/HomeCursor/commit/cf9b548732f2468fc1158333fb219c15cb7300f7))
* **client:** add TCP host connection ([127d89a](https://github.com/talgrim/HomeCursor/commit/127d89a25ece192a99f72bd77b8eca1cce5de7c4))
* **client:** add trackpad screen ([9a7390d](https://github.com/talgrim/HomeCursor/commit/9a7390d5b4a7a03e694212ec1d0c96380156c8d6))
* **client:** add trackpad settings store ([8fceb9a](https://github.com/talgrim/HomeCursor/commit/8fceb9a4cb2f20d8f44916966eadd422be926376))
* **client:** add UDP host discovery ([5914ca1](https://github.com/talgrim/HomeCursor/commit/5914ca168e7b70a6e029a4780318246d409facc8))
* **client:** dark/light theme and visual redesign ([176ea5c](https://github.com/talgrim/HomeCursor/commit/176ea5c30753601263f15287b159ba42816ea2d7))
* **client:** discover hosts, connect and reconnect to the last host ([7706d99](https://github.com/talgrim/HomeCursor/commit/7706d99b70ab78a381cccdfea7a7d143d8f3b13e))
* **client:** persist last host and add connection coordinator ([c88ca55](https://github.com/talgrim/HomeCursor/commit/c88ca55af744d146bb72050746424e4420e7ee99))
* **client:** port pointer messages to Kotlin protocol ([6925e25](https://github.com/talgrim/HomeCursor/commit/6925e25735ee41034462b90137ad3f2640734790))
* **client:** redesign host list and connecting screen ([28d95aa](https://github.com/talgrim/HomeCursor/commit/28d95aa919c5f3e1a8a0c8ec42b34cd4cb817f31))
* **client:** redesign shortcuts sheet and editor ([dd5d270](https://github.com/talgrim/HomeCursor/commit/dd5d270077cbc1a5fa9325af292430fed42e7a69))
* **client:** restyle trackpad, keyboard and mouse buttons ([a6766c6](https://github.com/talgrim/HomeCursor/commit/a6766c68cc5b96867d90c20a2b3d5ddddcb89e0f))
* **client:** scaffold Android app with Compose ([7f6cf9b](https://github.com/talgrim/HomeCursor/commit/7f6cf9b99168d37b759ec57985168a34da8d2eef))
* **client:** scaffold Android app with Compose ([510f1e5](https://github.com/talgrim/HomeCursor/commit/510f1e5a27d4a7cf5e9e3df4b7563a5c542f39aa))
* **client:** send key and text messages to the host ([0ed4dd0](https://github.com/talgrim/HomeCursor/commit/0ed4dd04debe59dc231383d335638a2607dcf574))
* **client:** trackpad for moving the pointer, clicking and scrolling ([5525e0e](https://github.com/talgrim/HomeCursor/commit/5525e0e05dc102a55500ed96dcf810d830d385a1))
* **client:** wire trackpad into session and view model ([0472905](https://github.com/talgrim/HomeCursor/commit/0472905a2b458b96446330c3273def7f5fdfdb75))
* **core:** add host settings with JSON file provider ([9abefc8](https://github.com/talgrim/HomeCursor/commit/9abefc80cd421eeec5887ec56d8c26a0731fb251))
* **core:** add TCP control, UDP pointer and discovery loops ([cff75fb](https://github.com/talgrim/HomeCursor/commit/cff75fbbbba7cc35eb9294bef911111afe6d22f3))
* **core:** allow a single client session at a time ([0fa33b0](https://github.com/talgrim/HomeCursor/commit/0fa33b07926fb876d07bf019df6bacc869494031))
* **core:** dispatch client messages to an input injector ([5933832](https://github.com/talgrim/HomeCursor/commit/59338326d9636724ab68d0950c22f4f831ba0da9))
* **core:** expose client session events with end reason ([652292f](https://github.com/talgrim/HomeCursor/commit/652292f4f135cc0f39e19542815dfae78659bded))
* **core:** run host loops with Starting and Faulted statuses ([d102e98](https://github.com/talgrim/HomeCursor/commit/d102e9862be57c6b7660bec0c8b2abfdee335c54))
* **host-windows:** inject input with SendInput ([685acae](https://github.com/talgrim/HomeCursor/commit/685acaebba7dcca725d7b424edf0c86ff4ee3c61))
* **host:** add dependency injection ([001c25a](https://github.com/talgrim/HomeCursor/commit/001c25a50cad70fd2b63633b937ec6fa78e3f8c7))
* **host:** add dependency injection ([2737c58](https://github.com/talgrim/HomeCursor/commit/2737c58ad9b14f49d3f606e6ddba6d0acb4c4de3))
* **host:** add opt-in diagnostics log for pointer lag ([1ce7d76](https://github.com/talgrim/HomeCursor/commit/1ce7d7693221018677260b5025f038b1187374b2))
* **host:** add opt-in diagnostics log for pointer lag ([8a051d1](https://github.com/talgrim/HomeCursor/commit/8a051d1e491168fb36d2553fad5ced462ba0073e))
* **host:** receive client messages over the local network ([0c4a83b](https://github.com/talgrim/HomeCursor/commit/0c4a83bf98844a1e5c7dfccfa2e0294efd6e0acf))
* **host:** scaffold core library and tray shell ([35a3c6a](https://github.com/talgrim/HomeCursor/commit/35a3c6aa21be57e32d16dad644c89abec7649e4e))
* **host:** scaffold core library and tray shell ([d250ae2](https://github.com/talgrim/HomeCursor/commit/d250ae249b5b11851a4b60d6668dc038d959ce11))
* **host:** ship settings.json with the app ([7a26f61](https://github.com/talgrim/HomeCursor/commit/7a26f617dfc4096759d1f17dd9d49b74e89a47b4))
* **host:** show connected client and notify on connect/disconnect ([741ab32](https://github.com/talgrim/HomeCursor/commit/741ab324e4e21e741396eefbfa45ccdf14ca9026))
* on-screen keyboard and shortcut list ([46df68c](https://github.com/talgrim/HomeCursor/commit/46df68cc4133ebff30e2223cdb0bd984435a81d6))
* **protocol:** add space, letter, digit and punctuation keys ([4c89d0c](https://github.com/talgrim/HomeCursor/commit/4c89d0c69b1f6664b0c5dac92f674cb9ff763cc1))
* **protocol:** add wire protocol project ([0a5ffe1](https://github.com/talgrim/HomeCursor/commit/0a5ffe1006284e96298c2b99ab6372ca3b073cd3))
* **tray:** show connected client and notify on connect/disconnect ([7b5df13](https://github.com/talgrim/HomeCursor/commit/7b5df1314e64c2e2965bc0662138e394993cb36c))
* **tray:** wire up the host, logging and error reporting ([1f8e944](https://github.com/talgrim/HomeCursor/commit/1f8e944b80530476ee19b62ab35e3fd62c4ddddb))


### Bug Fixes

* **client:** hold a tap's click back so tap-and-drag is one press ([7cc00ad](https://github.com/talgrim/HomeCursor/commit/7cc00adaef52744044585fe8f2b14ca2dfc052cc))
* **host:** type newline and tab in text as Enter and Tab keys ([7179507](https://github.com/talgrim/HomeCursor/commit/717950740e5143ca42fabd123f2bb7c77048c5bb))

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
