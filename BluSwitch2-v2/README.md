# BluSwitch 2.0 (independent development project)

**Project folder:** `BluSwitch2-v2/` inside the existing `maccabluu/BluSwitch2` GitHub repository.

**Project branch:** `bluswitch2-v2-independent`. The existing BluSwitch2 app, Eden-based engine, source files, signing and app data are untouched.

This is a clean Android ARM64 starter for a future Switch 2 emulator, not a rebrand or update of the old APK. It has a new app ID `com.blustudio.bluswitch2.v2`, its own app storage, a console-like library, a persistent Android folder picker and an original native read-only NSO header probe. It does not yet execute Switch 2 titles.

## Status

- **Working source:** standalone Gradle Android project, SAF folder registration and read-only extracted-folder scanning; native C++ header-check interface.
- **Not implemented:** Switch 2 guest CPU, kernel services, title execution, graphics emulation, save compatibility and playable games.
- **Not done:** no APK build, GitHub Actions execution or device tests.

For the project comparison and source decisions read [research](docs/engine-comparison.md).

## Separation rules

1. All BluSwitch 2.0 source lives under `BluSwitch2-v2/`. Do not change `bluswitch-ui/`, `scripts/` or existing workflows.
2. Use `com.blustudio.bluswitch2.v2`, not the old package.
3. Do not reuse old signing material, user data paths or saves.
4. No GitHub Actions workflow is created here. No APK build without explicit permission.
5. No copyrighted keys, games, firmware or third-party game covers included.
6. Preserve upstream licence and attribution when introducing external GPL code.

## Source layout

- `app/src/main/java/.../MainActivity.java`: independent console/library UI.
- `app/src/main/java/.../GameCatalog.java`: SAF tree access and extracted-title structure scanning.
- `app/src/main/java/.../NativeCore.java`: isolated JNI entrypoint.
- `app/src/main/cpp/`: original native-format probe.
- `docs/engine-comparison.md`: researched projects and decisions.

## Local development (when a build is authorised)

Open `BluSwitch2-v2/` in Android Studio, install Android SDK 35 and NDK/CMake, and sync the Gradle project. It targets Android 13+ and arm64-v8a. A successful build would only prove the new app shell and native probe compile, **not** Switch 2 emulation.

Licence: GPL-3.0-or-later.

## Independent native engine work

The new `engine_core.cpp` now contains **untested** original ARM64 decoder and bounded guest-memory source, with a synthetic three-instruction test available from the home screen. This is a small real CPU execution experiment, **not Switch 2 gameplay**. See [native architecture](docs/native-engine-architecture.md).


## Independent CPU improvements and isolated test build

Experimental AArch64 interpreter includes MOVZ, MOVK, MOVN, 64-bit ADD/SUB immediate, load/store of 64-bit guest values, B/BL, CBZ/CBNZ, NOP and RET. There are still no Switch 2 system services, GPU execution, full game loading or working Switch 2 games.

`tests/test_engine_core.cpp` checks arithmetic, memory bounds, branching and failure behaviour using synthetic instructions. The branch-specific GitHub Actions workflow compiles these tests before building a **separate** debug APK for `com.blustudio.bluswitch2.v2`. The workflow does not build the original BluSwitch2 application or its Eden core. A successful APK build proves the project shell compiles, not Switch 2 game compatibility.
