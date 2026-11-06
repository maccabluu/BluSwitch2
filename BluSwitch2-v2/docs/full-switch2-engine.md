# Experimental Switch 2 engine integration

This is an independent, native Android build path for BluSwitch 2.0, based on the GPL-licensed Eden SX2A v0.3.2 / PokeDinner experimental engine. It integrates the existing repository's BluSwitch navigation scripts into that engine at build time.

Source: https://git.eden-emu.dev/moceure/eden-nx2-android, tag `nx2-android-v0.3.2`.
The engine work belongs to its original contributors; retain GPL-3.0-or-later notices and the original source attribution.

The existing scratch-built `BluSwitch2-v2/app/` remains in place and separate for research. The experimental full-engine APK uses the **same** intended Android application ID `com.blustudio.bluswitch2.v2`, but is a different runtime. This source-based build does not currently include the earlier ARM64 treasure mini-game. Both are part of the same BluSwitch 2.0 development branch, not the original app.

Game input supported by upstream: self-dumped extracted `exefs/` and `romfs/` folders or experimental `.nup` files. Switch 2 NSP/XCI support is not available. Actual playable results depend on the individual game, device RAM, GPU driver, and upstream bugs. No commercial games, keys or firmware are bundled.

The heavy Android build must be explicitly authorised, and the independent workflow is manual-only after the one-off run. Do not edit original `bluswitch-ui/`, `scripts/`, the original Android workflow, or the `main` branch.

Note: GitHub-hosted debug builds use ephemeral debug signing certificates. Installing over the previous scratch-built BluSwitch 2.0 APK may fail with a signing-certificate conflict. Uninstall **only** BluSwitch 2.0 before installing the full-engine APK, and back up any data from that independent test app first. Never uninstall the original BluSwitch2 app.
