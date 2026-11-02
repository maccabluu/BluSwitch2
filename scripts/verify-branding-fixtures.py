#!/usr/bin/env python3
"""Exercise the real native branding patch with small, synthetic upstream fixtures."""
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

with tempfile.TemporaryDirectory() as temp:
    root = Path(temp) / "src/android/app/src/main"
    values = root / "res/values"
    values.mkdir(parents=True)
    (values/"strings.xml").write_text("""<resources>
<string name="app_name">Eglantine</string>
<string name="open_user_folder">Open Eden folder</string>
<string name="select_games_folder_description">Allows Eden to populate your library</string>
<string name="share_gpu_log_description">Share Eden\\'s GPU log</string>
<string name="licenses_description">Projects that make Eden for Android possible</string>
<string name="app_notification_channel_id">Eden</string>
</resources>""")
    def sample(path, content):
        file = root/"java/org/yuzu/yuzu_emu"/path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
        return file
    bug=sample("utils/BugReport.kt", '''
text.appendLine("Eden SX2A report")
text.appendLine("==== $name ($label) ====")
val nativeLog = "eden_log.txt"
return File(dir, "eden-sx2a-report-$stamp.txt")
''')
    profile=sample("fragments/ProfileManagerFragment.kt",
                   "                val username = NativeLibrary.getUserUsername(uuid)\n")
    directory=sample("utils/DirectoryInitialization.kt",
                     "            migrateSettings()\n            areDirectoriesReady = true\n")
    setting=sample("features/settings/model/StringSetting.kt",
                   "override val defaultValue: String by lazy { NativeConfig.getDefaultToString(key) }\n")
    adapter=sample("adapters/ProfileAdapter.kt", '''
val jpegData = NativeLibrary.getDefaultAccountBackupJpeg()
val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
binding.imageAvatar.setImageBitmap(bitmap)
''')
    dialog=sample("fragments/EditUserDialogFragment.kt", '''
val jpegData = NativeLibrary.getDefaultAccountBackupJpeg()
val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
binding.imageUserAvatar.setImageBitmap(bitmap)
''')
    subprocess.run([sys.executable, "scripts/rebrand-visible.py", temp], check=True)
    elements=ET.fromstring((values/"strings.xml").read_text())
    names={x.attrib["name"]: x.text for x in elements if x.tag=="string"}
    assert names["app_name"]=="BluSwitch 2"
    assert names["open_user_folder"]=="Open BluSwitch 2 folder"
    assert "BluSwitch 2" in names["select_games_folder_description"]
    assert "BluSwitch 2" in names["share_gpu_log_description"]
    assert "Eden" in names["licenses_description"], "Legal attribution was altered"
    assert names["app_notification_channel_id"]=="Eden", "Internal Android channel ID changed"
    assert 'bluswitch2-report-$stamp.txt' in bug.read_text()
    assert 'eden_log.txt' in bug.read_text(), "Diagnostic source log lookup changed"
    assert 'updateUserUsername(uuid, "BluSwitch User")' in profile.read_text()
    assert 'NativeConfig.setString("device_name", "BluSwitch 2")' in directory.read_text()
    assert 'if (this == DEVICE_NAME) "BluSwitch 2"' in setting.read_text()
    assert 'R.drawable.bluswitch_icon' in adapter.read_text()
    assert 'R.drawable.bluswitch_icon' in dialog.read_text()
print("PASS: native labels and default identities changed without touching user IDs or legal attribution")
