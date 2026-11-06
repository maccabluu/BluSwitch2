// SPDX-License-Identifier: GPL-3.0-or-later
// BluSwitch2 controller settings for Banana Native. Physical gamepad remaps are
// applied in GameActivity before Native.key; the engine keeps player assignment.
package dev.nxnative.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class BluSwitchControllerSettings {
    private BluSwitchControllerSettings() {}

    private static final int[] KEYS = {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR
    };
    private static final String[] LABELS = {
        "A", "B", "X", "Y", "L", "R", "ZL", "ZR",
        "Start (+)", "Select (-)", "Left Stick (L3)", "Right Stick (R3)"
    };

    private static SharedPreferences prefs(Context c) {
        return Prefs.open(c, "bluswitch_controller");
    }

    private static String suffix(String descriptor) {
        return descriptor == null ? "all" : Integer.toHexString(descriptor.hashCode());
    }

    private static String key(String descriptor, int physicalKey) {
        return "button." + suffix(descriptor) + "." + physicalKey;
    }

    static int mapKey(Context c, KeyEvent e) {
        int code = e.getKeyCode();
        InputDevice d = e.getDevice();
        SharedPreferences p = prefs(c);
        String deviceKey = d == null ? null : key(d.getDescriptor(), code);
        if (deviceKey != null && p.contains(deviceKey)) return p.getInt(deviceKey, code);
        return p.getInt(key(null, code), code);
    }

    private static int keyIndex(int keyCode) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i] == keyCode) return i;
        return -1;
    }

    private static int dp(Activity a, int n) {
        return Math.round(a.getResources().getDisplayMetrics().density * n);
    }

    static void buttonMapping(Activity a) {
        new AlertDialog.Builder(a)
            .setTitle("BluSwitch2 Button Mapping")
            .setMessage("Map your physical controller buttons to Switch buttons. Changes take effect the next time a game receives input. You can also set mappings for each connected controller from Player 1 or Player 2.")
            .setPositiveButton("Configure buttons", (d, w) -> mapping(a, null))
            .setNegativeButton("Cancel", null).show();
    }

    private static void mapping(Activity a, String descriptor) {
        String[] choices = new String[KEYS.length + 1];
        SharedPreferences p = prefs(a);
        for (int i = 0; i < KEYS.length; i++) {
            int selected = p.getInt(key(descriptor, KEYS[i]), KEYS[i]);
            int mapped = keyIndex(selected);
            choices[i] = LABELS[i] + "  →  " + (mapped < 0 ? LABELS[i] : LABELS[mapped]);
        }
        choices[KEYS.length] = "Reset these button mappings";
        new AlertDialog.Builder(a)
            .setTitle(descriptor == null ? "All controllers" : "Controller button mapping")
            .setItems(choices, (dialog, which) -> {
                if (which == KEYS.length) {
                    SharedPreferences.Editor edit = p.edit();
                    for (int code : KEYS) edit.remove(key(descriptor, code));
                    edit.apply();
                    mapping(a, descriptor);
                } else {
                    int current = p.getInt(key(descriptor, KEYS[which]), KEYS[which]);
                    new AlertDialog.Builder(a)
                        .setTitle("Physical " + LABELS[which] + " sends")
                        .setSingleChoiceItems(LABELS, Math.max(0, keyIndex(current)), (choice, target) -> {
                            p.edit().putInt(key(descriptor, KEYS[which]), KEYS[target]).apply();
                            choice.dismiss();
                            mapping(a, descriptor);
                        })
                        .setNegativeButton("Back", (d, w) -> mapping(a, descriptor))
                        .show();
                }
            })
            .setNegativeButton("Done", null).show();
    }

    private static List<InputDevice> connected(Activity a) {
        List<InputDevice> list = new ArrayList<>();
        InputManager manager = (InputManager) a.getSystemService(Context.INPUT_SERVICE);
        if (manager == null) return list;
        for (int id : manager.getInputDeviceIds()) {
            InputDevice d = manager.getInputDevice(id);
            if (d != null && !d.isVirtual() &&
               (d.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                d.supportsSource(InputDevice.SOURCE_JOYSTICK))) {
                list.add(d);
            }
        }
        return list;
    }

    static void player(Activity a, int player) {
        List<InputDevice> pads = connected(a);
        String title = "Player " + player + " controls";
        if (pads.isEmpty()) {
            new AlertDialog.Builder(a).setTitle(title)
                .setMessage("No physical gamepad is connected. Connect your AYN Thor controls, Bluetooth gamepad or USB controller and reopen this page. On-screen controls are configured separately.")
                .setPositiveButton("Button mapping", (d, w) -> mapping(a, null))
                .setNegativeButton("Close", null).show();
            return;
        }
        StringBuilder desc = new StringBuilder("Connected physical controllers:\n");
        for (int i = 0; i < pads.size(); i++) {
            desc.append("\n").append(i + 1).append(". ").append(pads.get(i).getName());
        }
        desc.append("\n\nThe game engine assigns actual player slots automatically as controllers connect.");
        new AlertDialog.Builder(a)
            .setTitle(title)
            .setMessage(desc.toString())
            .setPositiveButton("Configure a controller", (d, w) -> chooseDevice(a, pads))
            .setNeutralButton("All controllers", (d, w) -> mapping(a, null))
            .setNegativeButton("Close", null).show();
    }

    private static void chooseDevice(Activity a, List<InputDevice> pads) {
        String[] names = new String[pads.size()];
        for (int i = 0; i < pads.size(); i++) names[i] = pads.get(i).getName();
        new AlertDialog.Builder(a).setTitle("Choose physical controller")
            .setItems(names, (d, index) -> mapping(a, pads.get(index).getDescriptor()))
            .setNegativeButton("Cancel", null).show();
    }

    static void touchControls(Activity a) {
        SharedPreferences p = Prefs.open(a, "touch_controls");
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 24), dp(a, 8), dp(a, 24), dp(a, 4));

        CheckBox visible = new CheckBox(a);
        visible.setText("Show touch controls in games");
        visible.setChecked(p.getBoolean("shown", true));
        visible.setOnCheckedChangeListener((button, checked) ->
            p.edit().putBoolean("shown", checked).commit());
        box.addView(visible);

        TextView opacityLabel = new TextView(a);
        SeekBar opacity = new SeekBar(a);
        opacity.setMax(90);
        opacity.setProgress(Math.max(0, Math.min(90,
            Math.round(p.getFloat("opacity", 0.6f) * 100) - 10)));
        opacityLabel.setText("Button opacity: " + (opacity.getProgress() + 10) + "%");
        opacity.setOnSeekBarChangeListener(listener(value -> {
            int percent = value + 10;
            opacityLabel.setText("Button opacity: " + percent + "%");
            p.edit().putFloat("opacity", percent / 100f).commit();
        }));
        box.addView(opacityLabel);
        box.addView(opacity);

        TextView sizeLabel = new TextView(a);
        SeekBar size = new SeekBar(a);
        size.setMax(90);
        size.setProgress(Math.max(0, Math.min(90,
            Math.round(p.getFloat("scale", 1f) * 100) - 60)));
        sizeLabel.setText("Button size: " + (size.getProgress() + 60) + "%");
        size.setOnSeekBarChangeListener(listener(value -> {
            int percent = value + 60;
            sizeLabel.setText("Button size: " + percent + "%");
            p.edit().putFloat("scale", percent / 100f).commit();
        }));
        box.addView(sizeLabel);
        box.addView(size);

        CheckBox vibration = new CheckBox(a);
        vibration.setText("Vibrate when pressing touch buttons");
        vibration.setChecked(p.getBoolean("haptics", true));
        vibration.setOnCheckedChangeListener((button, checked) ->
            p.edit().putBoolean("haptics", checked).commit());
        box.addView(vibration);

        TextView help = new TextView(a);
        help.setText("To move individual buttons, start a game, open its side panel, then choose On-screen controls → Edit the controls layout. These settings apply when the next game starts.");
        help.setPadding(0, dp(a, 12), 0, 0);
        box.addView(help);
        new AlertDialog.Builder(a).setTitle("On-screen controls")
            .setView(box)
            .setPositiveButton("Done", null)
            .setNeutralButton("Reset", (d, w) -> {
                p.edit().putBoolean("shown", true).putFloat("opacity", 0.6f)
                    .putFloat("scale", 1f).putBoolean("haptics", true).commit();
                touchControls(a);
            }).show();
    }

    private interface OnValue { void apply(int value); }
    private static SeekBar.OnSeekBarChangeListener listener(OnValue update) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (user) update.apply(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };
    }
}
