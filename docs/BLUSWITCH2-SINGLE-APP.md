# BluSwitch2: ONE installable Android app

The Android package ID for every future native BluSwitch2 build is fixed to:

`dev.nxnative.android.bluswitchpack`

This is the package ID used by the user's BluSwitch2 Native Prepare APK. **Do not change `-PnxApp=bluswitchpack` again.** Do not make experimental `-PnxApp=bluswitchv123...` variants for routine fixes. Put all fixes, UI changes, controller features and supported game profiles into the same package.

## Why earlier test versions installed as duplicates

Each upstream `-PnxApp` suffix is a separate Android app ID. The earlier CI builds also used ephemeral debug signing, meaning two APKs with the same package ID from different runners may refuse to update one another. Android requires the **same package ID, signing certificate and an increasing versionCode** to install an update in place.

## One-time signing setup (private GitHub secrets)

An initial signing key was generated in the chat as a separate private ZIP, **not committed**. Its owner must save it securely. In the repository at Settings → Secrets and variables → Actions → New repository secret, add these exact three values from the private bundle's `BluSwitch2-GitHub-signing-secrets.txt`:

- `BLUSWITCH2_KEYSTORE_B64` (complete base64 keystore, one line)
- `BLUSWITCH2_KEY_ALIAS`
- `BLUSWITCH2_KEY_PASSWORD`

Do not upload the signing ZIP, the keystore, the base64 text or the password to this **public repository**. Never paste the secret values into an issue or chat. Losing the key will prevent signing future in-place updates.

The single-app workflow is `.github/workflows/bluswitch2-banana-prototype.yml`. It now runs **only by explicit workflow_dispatch**, checks the three secrets before building, uses the unchanged package ID, signs with the permanent certificate and increases Android's `versionCode` on subsequent GitHub Actions runs. Its artifact is always `BluSwitch2-Android/BluSwitch2.apk`.

Do not restart the old automatic test build or distribute its experimental-package APKs to the user as updates.

## One-time consolidation of existing installed apps

Previously installed APKs were signed with temporary debug certificates and included differing app IDs. They cannot all be fused in place by a new APK. The first permanently signed APK may require a **one-time uninstall of the old `dev.nxnative.android.bluswitchpack` test installation** because Android rejects a signature mismatch. Other older experimental icons are unrelated apps and must be removed **only after** saves and pack files have been exported and checked. The new signed app should then update over itself in place on future builds. No factory reset is needed.

Use the native Saves tab's export/backup, game data backup and external storage as appropriate before uninstalling a test APK. Do not assume an Android uninstall preserves app-private save files. Adding a game dump to a separately installed package does not migrate its saves or prepared pack automatically.

## Compatibility status

The current experimental native profile recognises Pokémon Pokopia's exact `v327680` build ID. This does **not** prove Prepare or gameplay works on AYN Thor Pro. Other Switch 2 games require real per-game profiles and graphics testing, not a placeholder `pack.tsv`.

Keep one app as features improve. Keep builds signed consistently. Verify gameplay on-device before declaring success.
