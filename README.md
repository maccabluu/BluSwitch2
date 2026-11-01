# BluSwitch 2

BluSwitch 2 is an **experimental ARM64 Android emulator build** from Blu Studio. This repository contains the build and branding instructions for a standalone Android APK using the GPL-licensed **Eden SX2A / NX2** emulator core.

## Current status

- A cloud build is being set up on GitHub Actions. An APK is not confirmed until the build succeeds and native ARM64 emulator libraries are found inside it.
- The upstream Switch 2 emulator is highly experimental, and gameplay on the AYN Thor is **not verified**.
- This first native-engine build uses the upstream Android library and settings interface with BluSwitch 2 branding. Our custom home-screen layout from the earlier prototype is **not yet integrated**.
- It is **not** the old BluSwitch launcher or an external-app shortcut.

## Source and build

Open [GitHub Actions](https://github.com/maccabluu/BluSwitch2/actions) to see **BluSwitch 2 Native Android**.

The cloud workflow fetches tagged source from:
https://git.eden-emu.dev/moceure/eden-nx2-android

It applies the BluSwitch 2 app name, app ID and icon before compiling the ARM64 Android APK. It builds on an Ubuntu runner, not your Mac. A successful run provides a downloadable APK artifact and checksum; a failed run needs diagnosis.

## Licence and credits

The modified emulator and Android application follow the upstream project's **GPL-3.0-or-later** licence and applicable third-party licences. Upstream contributors retain their rights and credits. The app is unofficial and not affiliated with Nintendo or endorsed by the Eden project.

No games, encryption keys or Nintendo firmware are included. Use only legally obtained game dumps.
