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
2. **First engine proof:** Build a genuine Banana Native ARM64 prototype pinned to v0.1.5, with Pokopia's publicly defined game profile and real on-device preparation tools. Use a distinct test application ID and no included games, keys, game code or shader packs.
3. **On-device comparison:** Try the prototype on an AYN Thor Pro with the user's own game dump. Check GPU driver, RAM, preparation, game version, logs and actual gameplay. Back up saves first.
4. **One app:** Once engine proof works, replace the BluSwitch2 launch/backend layer with native Banana game list, Prepare, Play, logs, settings, driver and save APIs. Rebuild our blue UI against that backend, not just an Android redirect or a second installed app.
5. **Release:** Keep the user-approved package identity and update signature when practical. Provide a migration/export path for Eden saves, settings and imported folder access. Do not silently overwrite/delete existing emulator data.

Banana's full application incorporates GPL-3.0-or-later components despite the MIT upstream core. Preserve copyright notices, code-source link and licenses in the shipped app.

No gameplay compatibility claim is made for this branch until on-device testing succeeds.
