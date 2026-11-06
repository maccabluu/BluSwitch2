# BluSwitch2 clean rebuild

This branch is a safe reset of BluSwitch2. The existing `main` APK and its data are untouched.

## Interface

The BluSwitch2 home screen now offers Library, Favourites, Files, Saves, Controller, Settings and Power. The nonworking Shop, Media and Handheld Mode shortcuts are removed. The Shop network/download Java bridge and its frontend are also removed. The next interface will centre on **Add game → Check compatibility → Prepare → Play**, rather than assuming every file format boots.

## Native game engine direction

The existing app uses Eden SX2A Android `nx2-android-v0.3.2`. That core remains in this branch **until a replacement really builds and passes AYN Thor testing**. Do not describe this UI cleanup as a Banana Native engine replacement.

The next engine candidate is [Banana Native v0.1.5](https://github.com/moceure/BananaNative/releases/tag/v0.1.5), based on nxnative and independently maintained. It is a **native porting system, not a general Switch 2 emulator**. It only runs games with built-in profiles. Pokopia (`0400A3A01EE4C000`) is one such profile. Banana's release reports Pokopia running on the author's Snapdragon/Adreno phone, but that does not verify AYN Thor Pro (Adreno 740, 12 GB).

Upstream source tag is pinned to commit `243bae31a836503d216bb0003e90b194d4366bad`. Its Android launcher is Java, engine JNI/C with per-game compiled host libraries, and the real Prepare pipeline needs an Android CPython stage, .NET 10 native translator, Capstone, lz4 and SPIRV-Tools. Unlike Eden's legacy loading path, Banana requires the player's own extracted title data and preparation, often generating shaders for the particular title.

## Integration plan

1. **Current:** Clean UI and regression tests on this isolated branch; no Shop or Media.
2. **Engine proof completed:** A genuine Banana Native ARM64 prototype pinned to v0.1.5 built successfully with the Pokopia public profile and real preparation tools. No games, keys, code extracted from games or shader packs are bundled.
3. **On-device comparison:** Try the prototype on an AYN Thor Pro with the user's own game dump. Check GPU driver, RAM, preparation, game version, logs and actual gameplay. Back up saves first.
4. **One APK integrated:** The blue BluSwitch2 Library, Files, Favourites, Saves, Controller and Settings screens now run as the visible WebView frontend inside the Banana Native APK. Its JavaScript bridge calls the same app's native Add Game, Prepare/Play, save backup/restore, GPU driver and log functions. No second app is launched from the BluSwitch2 interface. The original native screen is retained behind the frontend while migration continues.
5. **Release:** Keep the user-approved package identity and update signature when practical. Provide a migration/export path for Eden saves, settings and imported folder access. Do not silently overwrite/delete existing emulator data.

Banana's full application incorporates GPL-3.0-or-later components despite the MIT upstream core. Preserve copyright notices, code-source link and licenses in the shipped app.

**5 November 2026:** native engine compiled; BluSwitch2 blue frontend integrated and compiled in the APK. A separately installable BluSwitch2-branded test package is being built because GitHub test-run debug signing keys differ. Pokémon Pokopia gameplay on AYN Thor Pro is still unverified. The prior BluSwitch2 and Banana Native Preview packages remain untouched; do not uninstall them to test this one.
