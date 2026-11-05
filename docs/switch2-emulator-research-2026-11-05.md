# BluSwitch2: Switch 2 emulator research (5 November 2026)

This is an integration shortlist for our Android ARM64 BluSwitch2 app. This is **research only**, not a claim that a game now runs. Test with user-owned game files, record the same game version and device, and retain existing saves and the BluSwitch2 application ID.

## Existing baseline

Our GitHub Actions workflow pulls Eden SX2A (moceure/eden-nx2-android), tag `nx2-android-v0.3.2`. Its developer marked the project **unmaintained** after its final 2 November 2026 release. It already includes Turnip, extracted ExeFS and NUP support, Android memory fixes, and Switch 2 readiness checks. Avoid reimplementing features already present. Switch 2 NSP and XCI are not supported by this engine.

- Original maintained source and releases (now archived in practice): https://git.eden-emu.dev/moceure/eden-nx2-android
- Releases: https://git.eden-emu.dev/moceure/eden-nx2-android/releases

## Next technical work, by priority

### 1. Make black-screen reports actionable, then reproduce on AYN Thor Pro

Keep the exact launch path, game's file type, memory setting, GPU driver build, native emulator version and latest native GPU/core log. Add a single-step report export. Do not record or upload game files or keys. Compare the same game and version on Eden SX2A and BluSwitch2 using the same handheld.

### 2. Track Eglantine fixes selectively

- Source: https://github.com/Djihads80/Eglantine
- Example: build-212 fixed a Switch 2 NUP case where selecting an empty base ExeFS instead of the newest update caused a guest memory exception and **0 FPS**. This may be relevant if the user's game is a NUP, but it is not yet verified as the BluSwitch2 failure.
- Source for releases: https://github.com/Djihads80/Eglantine/releases

Only cherry-pick an isolated fix after checking compatibility with the Android ARM64 NX2 core and establishing native compilation and a real-device test.

### 3. Look at PokeDinner and its Pokopia fixes

- Source: https://git.eden-emu.dev/Franseven/PokeDinner-a-Eglantine-Fork
- The project focuses on Pokopia and combines Eglantine fixes. Compare specific loader, video, GPU and shader changes against the SX2A v0.3.2 tag. Do not replace the whole Android source with a desktop fork.

### 4. Look at Lichii renderer and diagnostic fixes

- Source: https://github.com/zortos293/Lichii
- Good candidates: shader fixes, frame dumps, and per-game troubleshooting. Desktop Vulkan work is not guaranteed to run on Qualcomm/Adreno Android.

### 5. Assess Banana Native as a future optional native game backend

- Source: https://github.com/moceure/BananaNative
- Its author identifies it as **native porting, not emulation**, and states it is a different codebase from Eden SX2A.
- The 5 November 2026 **v0.1.5** release lists Pokémon Pokopia as playable at roughly 60 fps on the author's tested Snapdragon/Adreno 16 GB phone; sky is black. It does **not** establish similar results on AYN Thor Pro.
- Interesting UI/Android features: fold-aware displays, per-game frame cap and resolution, save backups, integrated verified updates, GPU driver downloads, game log sharing, pack import/export.
- Integrating a second backend in the **same BluSwitch2 APK** would require a new build pipeline and native launcher integration. Do not paste its sources into Eden and assume it will boot. Preserve license notices (GPL-3.0-or-later where applicable).

## Milestones

1. Working downloadable APK with recorded checksum and a working in-app launch path.
2. Test a known user-owned extracted ExeFS/NUP title on the same device and compare native logs with Eden SX2A.
3. Port one minimal and relevant fix, build APK, compare the same game again.
4. Add save backups and in-app update verification.
5. Evaluate Banana Native as an optional engine only after a proof-of-concept Android ARM64 build and compatibility check.

**Status on 5 November 2026:** research reviewed, no renderer fixes or second engine merged, Pokémon Pokopia gameplay on AYN Thor Pro remains unverified.
