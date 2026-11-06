// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.ui.main;
// Original independent ARM64 guest tests and mini-game inside unified APK.
final class BluGuestCore {
    static { System.loadLibrary("blu_guest_core"); }
    private BluGuestCore() {}
    static native int[] stepMiniGame(int x,int y,int moves,int won,int direction);
    static native String runCpuTest();
    static native String runElfTest();
}
