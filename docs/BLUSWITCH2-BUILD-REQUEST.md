# BluSwitch2 single-app signed build request

Build requested: 2026-11-06.

Trigger the signed Android release workflow for the established `dev.nxnative.android.bluswitchpack` package ID. Keep the original icon, native engine, controller options, UI and experimental Pokopia v327680 profile. Use the same private signing certificate for future updates. Do not create another Android application ID.

Retry 2: stable Gradle signingConfig assignment corrected; use the SAME BluSwitch2 package ID and GitHub secrets.

Android installation compatibility audit: verify apksigner signature and zip alignment before release.
