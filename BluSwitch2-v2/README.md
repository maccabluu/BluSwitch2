# BluSwitch 2.0 (independent development project)

**Project folder:** `BluSwitch2-v2/` inside the existing `maccabluu/BluSwitch2` GitHub repository.

**Project branch:** `bluswitch2-v2-independent`. The existing BluSwitch2 app, Eden-based engine, source files, signing and app data are untouched.

This is a clean Android ARM64 starter for a future Switch 2 emulator, not a rebrand or update of the old APK. It has a new app ID `com.blustudio.bluswitch2.v2`, its own app storage, a console-like library, a persistent Android folder picker and an original native read-only NSO header probe. It does not yet execute Switch 2 titles.

## Status

- **Working source:** standalone Gradle Android project, SAF folder registration and read-only extracted-folder scanning; native C++ header-check interface.
- **Not implemented:** complete Switch 2 CPU, system services, title execution, graphics, save compatibility and playable games.
- **Previously verified:** separate test APK built and original ARM64 CPU self-test passed on AYN Thor Pro. New ELF loader source has not been rebuilt into an APK or tested on Android.

For the project comparison and source decisions read [research](docs/engine-comparison.md).

## Separation rules

1. All BluSwitch 2.0 source lives under `BluSwitch2-v2/`. Do not change `bluswitch-ui/`, `scripts/` or existing workflows.
2. Use `com.blustudio.bluswitch2.v2`, not the old package.
3. Do not reuse old signing material, user data paths or saves.
4. GitHub Actions for BluSwitch 2.0 are manual-only. Do not build another APK without explicit permission.
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


## Experimental ARM64 test-program loader (not in previous APK)

The independent loader now accepts restricted, static little-endian ELF64 AArch64 ET_EXEC programs smaller than 4 MB within an 8 MB guest-memory window. The new Test ELF loader button runs a self-authored three-instruction ELF. The Open test ELF option selects locally created test executables through Android's document picker. The guest CPU interpreter enforces a maximum of 100,000 instructions.

Input is validated before mapping. Unsupported formats, overlapping segments, invalid offsets, bad executable entrypoints and out-of-range memory are rejected. The original app is unaffected. The loader does not support Switch 2 NSO/NRO/NSP/XCI, firmware, operating system services, Vulkan guest graphics, dynamic linking or commercial game execution. Builds are manual-only.

## Original guest-CPU mini-game (source milestone)

`Play Blu Treasure Hunt` opens a 3×3 treasure game. Direction buttons feed player position and input to a self-authored AArch64 guest program executed by our interpreter. The guest code handles board boundaries, move counts and the winning square at (2,2). Android only displays the board and sends controller input. `tests/test_game_demo.cpp` exercises movement and victory logic using a local native compiler.

This is an original educational game within the emulator, **not** Switch 2 homebrew compatibility or commercial Switch 2 game support. The code is committed to the independent branch, but requires a **separately authorised** APK build before it appears on your AYN Thor Pro. Existing BluSwitch2 installs and saved data are unaffected.
