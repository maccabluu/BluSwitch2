#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Rebrand user-visible emulator UI without renaming engine files or legal attribution.

Internal log names, Android document-provider IDs, config keys, source copyright
and URL references stay unchanged. Profile UUIDs and game saves are never replaced.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 rebrand-visible.py <nx2-source-root>")
root = Path(sys.argv[1]).resolve() / "src/android/app/src/main"
assert (root / "res/values/strings.xml").is_file(), "Expected Android strings not found"

# Text resources are the real source of labels on the Android native settings pages.
# Do not alter upstream URLs, legal contributor credit, or internal resource identifiers.
skip_names = {
    "app_notification_channel_id", "app_disclaimer",
    "contributors_description", "contributors_link",
    "licenses_description", "github_link", "website_link", "x_link",
}
pattern = re.compile(r'(<string\b[^>]*\bname="([^"]+)"[^>]*>)([\s\S]*?)(</string>)')

def rebrand(text):
    text = re.sub(r'(?i)Eden\\\'s', "BluSwitch 2\\'s", text)
    text = re.sub(r"(?i)Eden's", "BluSwitch 2's", text)
    text = re.sub(r"(?i)\bEden SX2A\b", "BluSwitch 2", text)
    text = re.sub(r"(?i)\bEglantine\b", "BluSwitch 2", text)
    text = re.sub(r"(?i)\bEden\b", "BluSwitch 2", text)
    return text

changed = []
for xml in sorted(root.glob("res/values*/strings.xml")):
    original = xml.read_text()
    count = [0]
    def replace(match):
        prefix, name, value, suffix = match.groups()
        if name in skip_names or any(t in name.lower() for t in ("license", "contributor")):
            return match.group(0)
        if re.search(r"https?://|content://", value):
            return match.group(0)
        renamed = rebrand(value)
        if renamed != value:
            count[0] += 1
        return prefix + renamed + suffix
    updated = pattern.sub(replace, original)
    ET.fromstring(updated)
    if count[0]:
        xml.write_text(updated)
        changed.append((str(xml.relative_to(root)), count[0]))

# Diagnostic report display and export filename. The underlying native log
# files are deliberately still named eden_log.txt, because the engine writes them.
bug = root / "java/org/yuzu/yuzu_emu/utils/BugReport.kt"
s = bug.read_text()
assert 'Eden SX2A report' in s and 'eden-sx2a-report-$stamp.txt' in s
s = s.replace('Eden SX2A report', 'BluSwitch 2 report')
s = s.replace('eden-sx2a-report-$stamp.txt', 'bluswitch2-report-$stamp.txt')
s = s.replace('text.appendLine("==== $name ($label) ====")',
              'text.appendLine("==== BluSwitch 2 emulator log ($label) ====")')
bug.write_text(s)

# Preserve user UUID, saves, and avatar. Rename only the upstream default
# username, never any custom profile name.
profile = root / "java/org/yuzu/yuzu_emu/fragments/ProfileManagerFragment.kt"
s = profile.read_text()
old = '                val username = NativeLibrary.getUserUsername(uuid)'
assert s.count(old) == 1, "Native profile retrieval changed"
new = '''                var username = NativeLibrary.getUserUsername(uuid)
                if (username == "Eden" || username == "Eglantine") {
                    if (NativeLibrary.updateUserUsername(uuid, "BluSwitch User")) {
                        username = "BluSwitch User"
                    }
                }'''
profile.write_text(s.replace(old, new, 1))

# The Settings > System > Device name is a real native configuration value.
# Update only the upstream default. Keep user-set custom names intact.
directory = root / "java/org/yuzu/yuzu_emu/utils/DirectoryInitialization.kt"
s = directory.read_text()
old = '''            migrateSettings()
            areDirectoriesReady = true'''
assert s.count(old) == 1, "Directory setup has changed"
new = '''            migrateSettings()
            val deviceName = NativeConfig.getString("device_name", true)
            if (deviceName == "Eden" || deviceName == "Eglantine") {
                NativeConfig.setString("device_name", "BluSwitch 2")
                NativeConfig.saveGlobalConfig()
            }
            areDirectoriesReady = true'''
directory.write_text(s.replace(old, new, 1))

# Reset to default also uses BluSwitch 2 rather than restoring Eden.
setting = root / "java/org/yuzu/yuzu_emu/features/settings/model/StringSetting.kt"
s = setting.read_text()
old = 'override val defaultValue: String by lazy { NativeConfig.getDefaultToString(key) }'
assert s.count(old) == 1, "Native string setting defaults have changed"
setting.write_text(s.replace(old, '''override val defaultValue: String by lazy {
        if (this == DEVICE_NAME) "BluSwitch 2" else NativeConfig.getDefaultToString(key)
    }''', 1))

# Replace the upstream fallback avatar used when no saved custom photo exists.
# Custom profile images are not changed.
for path, start, end in [
    ("java/org/yuzu/yuzu_emu/adapters/ProfileAdapter.kt",
     "val jpegData = NativeLibrary.getDefaultAccountBackupJpeg()",
     "binding.imageAvatar.setImageBitmap(bitmap)"),
    ("java/org/yuzu/yuzu_emu/fragments/EditUserDialogFragment.kt",
     "val jpegData = NativeLibrary.getDefaultAccountBackupJpeg()",
     "binding.imageUserAvatar.setImageBitmap(bitmap)"),
]:
    file = root / path
    s = file.read_text()
    pos = s.index(start)
    stop = s.index(end, pos) + len(end)
    s = s[:pos] + ("binding.imageAvatar" if "ProfileAdapter" in path else "binding.imageUserAvatar") + ".setImageResource(R.drawable.bluswitch_icon)" + s[stop:]
    file.write_text(s)

print("BluSwitch 2 visible labels updated:", changed)
print("Native device name, profile name, default avatar and debug report branded")
