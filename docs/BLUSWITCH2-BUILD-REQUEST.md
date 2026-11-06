# BluSwitch2 single-app signed build request

Build requested: 2026-11-06.

Trigger the signed Android release workflow for the established `dev.nxnative.android.bluswitchpack` package ID. Keep the original icon, native engine, controller options, UI and experimental Pokopia v327680 profile. Use the same private signing certificate for future updates. Do not create another Android application ID.

Retry 2: stable Gradle signingConfig assignment corrected; use the SAME BluSwitch2 package ID and GitHub secrets.

Android installation compatibility audit: verify apksigner signature and zip alignment before release.

Installer compatibility rebuild: explicitly sign with v1, v2 and v3 using the existing permanent key.

Installer signature retry: corrected shell command quoting, still using same permanent signing keystore.

Rollback request: remove all BluSwitch2 custom signing logic and use Banana Native's original debug signing for one APK. No BLUSWITCH2 signing secrets and no release re-signing step. Keep package ID and UI unchanged.
