# Native Vulkan cache investigation

## Scope and source

Investigated BluSwitch2 main at `6990d2fa0a2d40d1bc62f381760de3f577d3904d`.
The native build downloads Eden `nx2-android-v0.3.2`, resolving to
`dd3cb327c79f511830e460b916a45a33b5738759` from
https://git.eden-emu.dev/moceure/eden-nx2-android.git.

Native changes are stored as `native-patches/0001-vulkan-cache-lifecycle.patch`,
following this repository's existing upstream-plus-patches build arrangement.
The patch changes only `src/video_core/renderer_vulkan/vk_pipeline_cache.cpp`.
The application script rejects a different upstream revision or modified input,
checks the patch before applying, and verifies the output SHA-256.

No engine compilation, APK packaging, workflow dispatch, device installation,
or hardware performance test was performed. These are source-confirmed defects,
not confirmed explanations for a specific Thor Pro game or measured FPS gains.

## Changes and evidence

### Avoid redundant disk-cache pipeline builds

`LoadDiskResources` previously queued every compatible disk record, including
keys already present in `compute_cache` or `graphics_cache`. Workers translated
and compiled those pipelines before `emplace` discarded the duplicate. Android's
`ReloadDiskShaderCache` reuses this same PipelineCache instance, so reloading a
cache already represented in memory repeats native compilation unnecessarily.
Duplicate records in the disk file also repeated this work.

The loader now snapshots existing non-null keys into loader-owned sets and
deduplicates records before queuing translation/driver compilation. Graphics
driver compatibility checks still run before accepting a key. Progress totals
count queued jobs, not skipped records. Workers never access these new sets.
The cache file format and version remain unchanged.

Check: reviewed the Android reload caller and worker insertion paths. Automated
source checks verify both deduplication guards precede queue submission. This
does not establish a measured reduction in startup time.

### Preserve usable Vulkan cache snapshots on failure

The old writer used a temporary file, but its I/O exception handler removed the
destination, deleting an earlier valid snapshot when a later save failed. It
also ignored both return values from `vkGetPipelineCacheData`. The native wrapper
returns `VkResult` directly and does not throw or validate the result.

The writer now checks both Vulkan reads before opening the temporary file. It
defers an incomplete or failed read to a later flush rather than replacing the
previous snapshot. No retry loop is added to gameplay. File writes check both
`failbit` and `badbit`, close successfully before renaming, and clean up only the
temporary file on failure. The previous cache is retained. This avoids cache
loss and consequent repeated compilation on later boots after these failures.

Check: inspected all save failure exits, the Vulkan wrapper, and the temporary
file lifetime. Automated source checks verify result checks precede file opening,
close precedes rename, and cleanup never deletes the destination. Native fault
injection and driver testing remain outstanding.

### Finish background work before replacing or saving the final cache

The destructor previously saved the final driver cache before its thread-pool
members were destroyed. An in-flight serialization task could write the same
`.tmp` filename concurrently. Driver compilation could still be running, and
translated pipeline objects could be destroyed before builder tasks referencing
them had finished. Reload also replaced the Vulkan cache without first draining
translation and driver-build jobs.

Shutdown and reload now wait for translations, adopt their results, wait for
driver builds, then wait for serialization before the final snapshot or cache
replacement. This dependency order allows translations to enqueue builds and
serialization while keeping their referenced objects alive. No new waits are
added to the per-draw or per-frame path. Shutdown/reload may wait for pending
work to finish, which is preferable to racing cache writes or object destruction.

Check: traced translation -> driver/serialization queues, destructor member
order, and the Android reload's GPU-idle boundary. Automated source checks verify
the wait/adoption order in both paths. Race-detector and hardware checks remain
outstanding.

## Other areas reviewed

- Async native shader translation, shader reuse, pipeline worker counts and
  the Vulkan scheduler/rasterizer were inspected. No speculative worker-count,
  draw-skipping, GPU synchronization, resolution, or driver-setting changes were
  made. These require game-specific traces to separate CPU, GPU, shader, and
  thermal limits on the Thor Pro.
- Cancellation of disk loading stops the shared builder pool in upstream code.
  The inspected Android callers pass an empty stop token. This is not established
  as the reported Android stall and is outside this patch.
- The upstream already has pipeline timing counters and async translation.
  Their existence is not evidence of smooth gameplay or game compatibility.

## Verification performed

`python3 scripts/verify-native-cache-fixes.py ../eden-source`: six checks passed.
These exercise real patch application in an isolated worktree, reapplication,
input drift refusal, revision refusal, and native source guard/order checks.
They are not C++ compilation or runtime tests.

All six existing `scripts/verify-*.cjs` checks and
`python3 scripts/verify-branding-fixtures.py` passed. `git diff --check` passed.

The patch does not touch saves, user IDs, keys, firmware, app package
`dev.eden.nx2.debug` (verified from the previous APK), signing, branding, or Android settings. The workflow
only gains the patch-application step. Its triggers remain unchanged. The commit
is published on `bluswitch2-native-cache-fixes`, which is excluded by both existing
workflows' push branch filters. Do not merge to main or run a build without the
user's permission, since the workflow-file change would trigger a main build.

## Checks requiring later permission and device access

After an explicitly approved build, compare the same game, save, driver,
resolution and power settings on Thor Pro. Record first-frame time for a cold
cache, warm cache, and same-title cache reload, plus frame-time percentiles and
pipeline timing logs. Test background-flush/shutdown overlap and failed cache
writes with a fault-injection setup. Keep the same app package and signing key
for an in-place update and preserve a backup of game saves. No FPS or loading-time
improvement is claimed until those checks are complete.

## Approved APK update

The user subsequently authorized the required build. The update branch preserves
the newer interface from `bluswitch2-eden-library-repair` at
`d7e8b9baa39393de0b872550cd733151316eab65` and adds the native patch.
The earlier nominal package description was corrected after inspecting the
previous APK: the mainline flavor overrides the default application ID, so the
installed package is `dev.eden.nx2.debug`. The existing debug keystore matches
the previous APK certificate. The build verifies that package, the signer SHA-256
`9d09b2001be0dde93ea0444c536b72270fab3eb1a2fccfd56e1a3c46cc0d7af1`,
and a version code above `34236209` before publishing its APK artifact.
The update workflow retains manual dispatch only. Main and the previous
source-only branch are unchanged.
