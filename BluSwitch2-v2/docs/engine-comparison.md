# BluSwitch 2.0, Switch 2 engine comparison (6 November 2026)

This document guides the **new independent Android project** in `BluSwitch2-v2/`. It does not change the original BluSwitch2 application or its build.

## Verified research

| Project | Approach | Android value | Current constraint |
|---|---|---|---|
| [Eglantine](https://github.com/Djihads80/Eglantine) | Experimental C++ Switch 2 emulator, Eden lineage | Study CPU, kernel services, new memory layout and loader, and possible Android code | Desktop results do not prove Android performance |
| [Lichii](https://github.com/zortos293/Lichii) | Fork of Eglantine with Vulkan, shader and game patches | Compare isolated GPU fixes and diagnostics | Unstable; Android-ready builds not independently verified |
| [PokeDinner](https://git.eden-emu.dev/Franseven/PokeDinner-a-Eglantine-Fork) | Eglantine derivative, Pokopia-focused | Study targeted game compatibility changes | Not a general reliable Android Switch 2 emulator |
| [Eden SX2A](https://git.eden-emu.dev/moceure/eden-nx2-android) | Experimental Android Switch 2 emulator | Existing JNI, Vulkan, Adreno and Android integration patterns | Final release 0.3.2 on 2 Nov 2026, unmaintained. Loose ExeFS/romfs and NUP, **not** Switch 2 NSP/XCI |
| [eden-nx2](https://git.eden-emu.dev/) | Early experimental Switch 2 Eden fork | Early implementation details | Limited platform release and compatibility |
| [Eden-NS2](https://github.com/0x2593/Eden-NS2) | Early Eden-based Switch 2 fork | Historical hardware bring-up commits | Archived |
| [Orchid](https://github.com/MythosOfs/Orchid) and other Eglantine forks | Game- or graphics-specific branches | Review individual patches only | Results differ by game, driver and architecture |
| [BananaNative](https://github.com/moceure/BananaNative) | Native ARM execution and graphics translation | Study ARM64/Adreno performance; separate research track | **Not an emulator**. Games need explicit porting support |
| [nxnative](https://github.com/) | Native game porting toolchain | Architecture comparison | Not an Android-ready general emulator |
| Pound, oboromi, Compound, Voland | Early from-scratch emulator/experimental foundations | Study design, testing and documentation | No verified broad commercial Switch 2 playback |

Sources checked: [Android Authority 5 Nov](https://www.androidauthority.com/nintendo-switch-2-emulators-ports-android-3731890/), [Eglantine upstream](https://github.com/Djihads80/Eglantine), [Lichii upstream](https://github.com/zortos293/Lichii), [Eden SX2A upstream](https://git.eden-emu.dev/moceure/eden-nx2-android), [BananaNative upstream](https://github.com/moceure/BananaNative).

## Decision

Develop **BluSwitch 2.0** as a separate Android ARM64 application with a new package, storage and console-style UI. Establish an original, auditable Android host and native interface first. Research Eglantine and Lichii's GPL-licensed emulator techniques, adapting **individual** compatible components with attribution only after review. Do not call BananaNative an emulator. Do not copy libraries, copyrighted firmware, keys or commercial game assets into the repository.

## Technical priorities

1. New app/package and persistent SAF game-library access, without touching original BluSwitch2 installs or saves.
2. Read-only executable format checks in a small original C++/JNI component; distinguish recognised file formats from playable games.
3. Architecture for guest CPU, OS/kernel services, memory mappings, title loader, Vulkan graphics, audio and input. Native execution remains **unimplemented** until genuine integration is tested.
4. Audit a GPL Switch 2 emulator's Android core and isolate cleanly attributable compatible changes.
5. Real-device tests on AYN Thor Pro (12 GB / Adreno 740): cold boot, memory, per-stage frame times, shader stalls and game logs.
6. Implement secure save backups, controller support and cover metadata only after launch and library features are verified.

## Honest compatibility status

No Switch 2 game has been booted by the new BluSwitch 2.0 project. Detecting an NSO header is not execution. The Android source scaffold is not yet built or tested on device. Do not represent test screens or archive recognition as playable Switch 2 support. No automatic CI runs or APK builds are authorised.
