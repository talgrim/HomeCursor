# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

HomeCursor lets a phone control a PC's mouse and keyboard over the local network (no cloud, no accounts). Two parts: a **C# host** (Windows, in `src/host`) and a **Kotlin Android client** (Jetpack Compose, in `src/client`). The project is in early development: `HostService` is a placeholder, the client is an empty Compose shell, and there is no networking, protocol or input injection yet.

## Commands

All host commands run from `src/host` (solution: `HomeCursor.Host.sln`, .NET 9).

```bash
dotnet build HomeCursor.Host.sln
dotnet test HomeCursor.Host.sln
dotnet test --filter "FullyQualifiedName~HostServiceTests.StopAsync_AfterStart_SetsStopped"   # single test
dotnet run --project HomeCursor.Host.Tray    # Windows only
```

There is no lint step configured.

The client is a Gradle project in `src/client` (AGP 9 with built-in Kotlin, version catalog in `gradle/libs.versions.toml`). From that directory: `./gradlew assembleDebug` to build, `./gradlew test` for unit tests. It needs a JDK and the Android SDK (`local.properties` with `sdk.dir` is gitignored). There is no Linux JDK in WSL; on this machine build via Windows using Android Studio's bundled JBR as `JAVA_HOME`, e.g. `cmd.exe /c gradlew.bat assembleDebug`.

## Architecture

- `HomeCursor.Core` holds all host logic behind abstractions (`IHostService` and friends). It is UI-agnostic and must not reference WinForms.
- `HomeCursor.Host.Tray` is a thin tray-only WinForms shell (`ApplicationContext` + `NotifyIcon`, no main window). It only depends on Core abstractions, so Core can later be driven from a console app or tests. It is a normal user process on purpose: input injection must happen in the logged-in user's session, so a Windows Service (Session 0) is not suitable.
- `HomeCursor.Core.Tests` is xUnit and references Core only.

## Environment notes

- Development happens partly under WSL. `dotnet new winforms` fails on Linux, and the Tray project builds there only because its csproj sets `EnableWindowsTargeting`. Building works on WSL, but running the tray app requires Windows, so verify tray behavior on Windows.
- Git on this checkout converts LF to CRLF (`core.autocrlf`); there is no `.gitattributes` yet.

## Repository conventions

- Default branch is `develop`; `main` is releases only.
- Commit tests in a separate commit from the code they cover.
