# BluSwitch 2.0, original native engine architecture

Our aim is to implement a **compatible** Switch 2 emulator, not to clone or embed Nintendo's actual firmware or hardware software. This is independent source, not a repackaged Eden core.

## Hardware model to research and eventually implement

- **Guest CPU:** AArch64 instruction execution. Hardware reports suggest 8-core ARM Cortex-A78C derived CPU. The present interpreter handles only MOVZ, ADD (immediate), NOP and RET on one synthetic test program. No JIT, multi-core or guest operating system support.
- **Memory:** Switch 2 reportedly has 12 GB LPDDR5X. On Android, allocating 12 GB is neither practical nor required for an emulator. Start with **bounded virtual memory**, lazy mapping and host-memory budgets. Current proof of concept maps an 8 KB test region and enforces bounds.
- **Graphics:** Switch 2 uses NVIDIA hardware. The Android device uses Qualcomm Adreno. A renderer would translate guest GPU commands into host Vulkan and needs correct synchronisation, format conversion and shader translation. **Not implemented.**
- **Kernel and system services:** handles, threads, events, IPC, files, time, controllers, audio and memory mapping. **Not implemented.**
- **Game loading:** unpacked program layout detection does not establish game compatibility; Switch 2 containers, decryption and executable relocation are **not implemented**.
- **Host app:** separate Android ID, SAF game library and native JNI probe. No original BluSwitch2 settings or saves imported or changed.

## What exists now

`engine_core.h/.cpp` contains an isolated AArch64 decoding experiment and bounded guest-memory window. `NativeCore.runEngineSelfTest()` runs a synthetic instruction sequence (MOVZ x0, #35; ADD x0, x0, #7; RET). Expect `X0=42` and 3 instructions **only if the source builds and runs successfully**. A new console UI action invokes the test.

There is no Switch 2 gameplay claim, native engine build, on-device measurement or APK artifact. Native source is currently **untested**. This architecture is a starting point and not a substitute for a mature Switch 2 CPU/GPU/OS implementation.

## Research sources

- Official Nintendo specifications: https://www.nintendo.com/en-gb/Hardware/Nintendo-Switch-2/Nintendo-Switch-2-Specifications-2785627.html
- Switchbrew hardware: https://switchbrew.org/w/index.php?title=Switch_2:Hardware
- Existing experimental implementation: https://github.com/Djihads80/Eglantine
- Android experimental implementation: https://git.eden-emu.dev/moceure/eden-nx2-android

## Next development tasks

1. Add automated offline instruction-decoder and memory-boundary tests (no APK build without permission).
2. Expand ARM64 CPU state, MMU/page maps, faults, floating point and atomics.
3. Research correct title loading, IPC, system calls and GPU command processor for Switch 2.
4. Prototype Vulkan resources and frame-time logging on AYN Thor Pro.
5. When authorised, build a separate APK and conduct controlled real-device tests.

All external code integration will retain GPL source and attribution.


## ELF64/AArch64 test loader

The new elf_loader.cpp restricts image loading to static, self-authored AArch64 ELF64 ET_EXEC programs in a bounded virtual-memory region. It validates program headers, entrypoint, alignment and image size before writing segments. Test code runs under the small existing guest interpreter. The Android UI offers a built-in loader test and a document picker for user-authored ELF binaries. No Switch 2 game executable format or console OS is supported. No APK rebuild was performed for this milestone.
