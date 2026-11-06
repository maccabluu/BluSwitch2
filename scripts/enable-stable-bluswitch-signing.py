#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Enable a single BluSwitch2 Android package with a persistent signing key.

The user's existing 'BluSwitch2 Native Prepare' APK uses the
dev.nxnative.android.bluswitchpack application ID. Keep that exact identity.
Signing key bytes are supplied via GitHub Actions secrets and NEVER committed.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: enable-stable-bluswitch-signing.py <banana-source-dir>")

source = Path(sys.argv[1]) / "android/app/build.gradle"
gradle = source.read_text(encoding="utf-8")

old = """def signStore = System.getenv('NX_SIGN_STORE'), signAlias = System.getenv('NX_SIGN_ALIAS'), signPass = System.getenv('NX_SIGN_PASS')
if (nxLive && !(signStore && signAlias && signPass && new File(signStore).isFile()))
    throw new GradleException('nxLive needs the release key: NX_SIGN_STORE (an existing keystore), NX_SIGN_ALIAS and NX_SIGN_PASS')"""
new = """def signStore = System.getenv('NX_SIGN_STORE'), signAlias = System.getenv('NX_SIGN_ALIAS'), signPass = System.getenv('NX_SIGN_PASS')
def bluswitchStable = nxApp == 'bluswitchpack'
if ((nxLive || bluswitchStable) && !(signStore && signAlias && signPass && new File(signStore).isFile()))
    throw new GradleException('BluSwitch2 updates require the same permanent signing keystore: NX_SIGN_STORE, NX_SIGN_ALIAS, NX_SIGN_PASS')"""

assert gradle.count(old) == 1, "Gradle signing inputs changed"
gradle = gradle.replace(old, new, 1)
old = """    signingConfigs {
        if (nxLive) live { storeFile file(signStore); storePassword signPass; keyAlias signAlias; keyPassword signPass }
    }"""
new = """    signingConfigs {
        if (nxLive || bluswitchStable) live { storeFile file(signStore); storePassword signPass; keyAlias signAlias; keyPassword signPass }
    }"""
assert gradle.count(old) == 1, "Signing config changed"
gradle = gradle.replace(old, new, 1)
old = """            signingConfig nxLive ? signingConfigs.live : signingConfigs.debug"""
new = """            signingConfig = (nxLive || bluswitchStable) ? signingConfigs.live : signingConfigs.debug"""
assert gradle.count(old) == 1, "Release signing configuration changed"
gradle = gradle.replace(old, new, 1)
source.write_text(gradle, encoding="utf-8")
print("BluSwitch2 fixed to dev.nxnative.android.bluswitchpack and permanent signing key")
