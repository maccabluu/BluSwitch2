// SPDX-License-Identifier: GPL-3.0-or-later
// Connect BluSwitch's own Settings hub to the actual emulator functions.
package org.yuzu.yuzu_emu.ui.main

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.dialogs.NetPlayDialog
import org.yuzu.yuzu_emu.features.DocumentProvider
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.features.settings.model.IntSetting
import org.yuzu.yuzu_emu.utils.NativeConfig
import org.yuzu.yuzu_emu.features.settings.ui.SettingsActivity
import org.yuzu.yuzu_emu.features.settings.ui.SettingsSubscreen
import org.yuzu.yuzu_emu.features.settings.ui.SettingsSubscreenActivity
import org.yuzu.yuzu_emu.fragments.SystemInfoDialogFragment
import org.yuzu.yuzu_emu.utils.BugReport
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.GpuDriverHelper
import org.yuzu.yuzu_emu.utils.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native BluSwitch settings actions. These re-use the working emulator setup,
 * input mapping, graphics driver, profile, and firmware management modules.
 * No action starts the upstream MainActivity, or a different installed app.
 */
class BluSettingsActions(
    private val activity: AppCompatActivity,
    private val showBluSwitchFolder: () -> Unit
) {
    private val jobs = Executors.newSingleThreadExecutor()
    private val verifying = AtomicBoolean(false)

    private fun message(title: String, body: String) {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                AlertDialog.Builder(activity).setTitle(title)
                    .setMessage(body).setPositiveButton("OK", null).show()
            }
        }
    }

    fun open(id: String) {
        try {
            when (id) {
                "readiness" -> subscreen(SettingsSubscreen.SWITCH2_READINESS)
                "advanced" -> settings(Settings.MenuTag.SECTION_ROOT)
                "graphics" -> settings(Settings.MenuTag.SECTION_RENDERER)
                "performance-stats" -> settings(Settings.MenuTag.SECTION_PERFORMANCE_STATS)
                "performance" -> performancePreset()
                "app" -> settings(Settings.MenuTag.SECTION_APP_SETTINGS)
                "controls" -> settings(Settings.MenuTag.SECTION_INPUT)
                "player-one" -> settings(Settings.MenuTag.SECTION_INPUT_PLAYER_ONE)
                "player-two" -> settings(Settings.MenuTag.SECTION_INPUT_PLAYER_TWO)
                "input-overlay" -> settings(Settings.MenuTag.SECTION_INPUT_OVERLAY)
                "profiles" -> subscreen(SettingsSubscreen.PROFILE_MANAGER)
                "drivers" -> subscreen(SettingsSubscreen.DRIVER_MANAGER)
                "freedreno" -> {
                    if (GpuDriverHelper.isAdrenoGpu()) {
                        subscreen(SettingsSubscreen.FREEDRENO_SETTINGS)
                    } else message("Freedreno settings", "These driver options require an Adreno GPU.")
                }
                "multiplayer" -> NetPlayDialog(activity).show()
                "applets" -> {
                    if (NativeLibrary.isFirmwareAvailable()) {
                        subscreen(SettingsSubscreen.APPLET_LAUNCHER)
                    } else message("Firmware required",
                        "Install firmware from your own console in Manage BluSwitch data before opening system applets.")
                }
                "data" -> subscreen(SettingsSubscreen.INSTALLABLE)
                "folders" -> message("Game folders", "Use the Files tab to add or remove game folders.")
                "verify" -> verifyInstalled()
                "logs" -> shareDebugReport()
                "gpu-logs" -> shareGpuLog()
                "data-folder" -> showBluSwitchFolder()
                "system-info" -> SystemInfoDialogFragment.newInstance()
                    .show(activity.supportFragmentManager, SystemInfoDialogFragment.TAG)
                "about" -> about()
                else -> message("BluSwitch 2", "Unknown settings action.")
            }
        } catch (ex: Exception) {
            message("BluSwitch 2", "This setting could not open: " + (ex.message ?: "Unknown error"))
        }
    }

    private fun performancePreset() {
        val currentResolution = IntSetting.RENDERER_RESOLUTION.getInt(needsGlobal = true)
        val currentShaders = BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.getBoolean(
            needsGlobal = true
        )
        val currentBackend = IntSetting.CPU_BACKEND.getInt(needsGlobal = true)
        val currentPresentation = NativeConfig.getBoolean("async_presentation", true)
        val preferences = activity.getSharedPreferences("bluswitch_performance", 0)
        AlertDialog.Builder(activity)
            .setTitle("Eden performance mode")
            .setMessage(
                "Use Eden's recommended Switch 2 settings?\n\n" +
                "Dynarmic CPU, asynchronous shaders, presentation synchronisation, " +
                "and no higher than 1× internal resolution.\n\n" +
                "Your current settings will be saved so you can restore them. " +
                "This may reduce stutter, but cannot fix an incompatible game."
            )
            .setPositiveButton("Apply settings") { _, _ ->
                try {
                    preferences.edit()
                        .putInt("cpu", currentBackend)
                        .putBoolean("shaders", currentShaders)
                        .putBoolean("presentation", currentPresentation)
                        .putInt("resolution", currentResolution)
                        .putBoolean("backup", true)
                        .apply()
                    IntSetting.CPU_BACKEND.setInt(0)
                    BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.setBoolean(true)
                    NativeConfig.setBoolean("async_presentation", false)
                    if (currentResolution > 3) IntSetting.RENDERER_RESOLUTION.setInt(3)
                    NativeConfig.saveGlobalConfig()
                    message("Performance mode", "Eden settings saved. Restart the game to apply them.")
                } catch (ex: Exception) {
                    message("Performance mode", "Unable to change settings: " +
                        (ex.message ?: "Unknown error"))
                }
            }
            .setNeutralButton("Restore previous") { _, _ ->
                if (!preferences.getBoolean("backup", false)) {
                    message("Performance mode", "No previous settings saved yet.")
                } else {
                    try {
                        IntSetting.CPU_BACKEND.setInt(preferences.getInt("cpu", 0))
                        BooleanSetting.RENDERER_ASYNCHRONOUS_SHADERS.setBoolean(
                            preferences.getBoolean("shaders", true)
                        )
                        NativeConfig.setBoolean("async_presentation",
                            preferences.getBoolean("presentation", true))
                        IntSetting.RENDERER_RESOLUTION.setInt(
                            preferences.getInt("resolution", 3))
                        NativeConfig.saveGlobalConfig()
                        message("Performance mode", "Previous settings restored.")
                    } catch (ex: Exception) {
                        message("Performance mode", "Unable to restore settings.")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun settings(tag: Settings.MenuTag) {
        val intent = Intent(activity, SettingsActivity::class.java)
        intent.putExtra("menuTag", tag)
        intent.putExtra("game", null as Parcelable?)
        activity.startActivity(intent)
    }

    private fun subscreen(destination: SettingsSubscreen) {
        val intent = Intent(activity, SettingsSubscreenActivity::class.java)
        intent.putExtra("destination", destination)
        intent.putExtra("game", null as Parcelable?)
        activity.startActivity(intent)
    }

    private fun verifyInstalled() {
        if (!DirectoryInitialization.areDirectoriesReady) {
            message("Verify content", "Emulator storage is not ready yet.")
            return
        }
        if (!verifying.compareAndSet(false, true)) {
            message("Verify content", "Verification is already running.")
            return
        }
        message("Verify installed content", "Checking your installed game updates and content for corruption.")
        jobs.execute {
            try {
                // The native function checks installed content and returns the failed names.
                val failures = NativeLibrary.verifyInstalledContents { _, _ -> false }
                if (failures.isEmpty()) {
                    message("Verification complete", "No corrupt installed content was reported.")
                } else {
                    message("Verification results",
                        "Content requiring attention:\n" + failures.joinToString("\n").take(3000))
                }
            } catch (ex: Exception) {
                message("Verification failed", ex.message ?: "Unable to verify installed content.")
            } finally {
                verifying.set(false)
            }
        }
    }

    private fun shareDebugReport() {
        jobs.execute {
            try {
                val file = BugReport.write(activity.applicationContext, null)
                activity.runOnUiThread {
                    if (!activity.isFinishing) BugReport.share(activity, file, "Share BluSwitch 2 debug report")
                }
            } catch (ex: Exception) {
                message("Debug report", "Unable to create the report: " + (ex.message ?: "Unknown error"))
            }
        }
    }

    private fun shareGpuLog() {
        val root = DocumentProvider.ROOT_ID
        val authority = DocumentProvider.AUTHORITY
        val current = DocumentsContract.buildDocumentUri(authority, root + "/log/eden_gpu.log")
        val previous = DocumentsContract.buildDocumentUri(authority, root + "/log/eden_gpu.log.old.txt")
        val source = when {
            !Log.gameLaunched && DocumentFile.fromSingleUri(activity, previous)?.exists() == true -> previous
            DocumentFile.fromSingleUri(activity, current)?.exists() == true -> current
            else -> null
        }
        if (source == null) {
            message("GPU logs", "No GPU log was found. Start a game first to generate a report.")
            return
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(FileUtil.TEXT_PLAIN)
            .putExtra(Intent.EXTRA_STREAM, source)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Share BluSwitch 2 GPU log"))
    }

    private fun about() {
        val build = try { NativeLibrary.getBuildVersion() } catch (_: Exception) { "Unknown" }
        AlertDialog.Builder(activity)
            .setTitle("BluSwitch 2")
            .setMessage(
                "Blu Studio experimental native Android emulator\n\n" +
                "Native emulator built using open-source contributions.\n" +
                "See Licences for upstream authors and source credits.\n" +
                "Native build: " + build + "\n\n" +
                "Unofficial application. Not affiliated with Nintendo."
            )
            .setPositiveButton("OK", null)
            .setNeutralButton("Licences") { _, _ -> subscreen(SettingsSubscreen.LICENSES) }
            .show()
    }

    fun close() {
        jobs.shutdown()
    }
}