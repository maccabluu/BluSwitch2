package com.blustudio.bluswitch2.v2;

// Independent native library. Format recognition is not guest code execution.
public final class NativeCore {
    static {
        System.loadLibrary("bluswitch_v2_core");
    }

    private NativeCore() {}

    // 1 = NSO0 recognised; 0 = other; negative = unable to probe.
    public static native int inspectExecutable(int fileDescriptor);

    // Test only. Executes our own synthetic ARM64 instructions, not game files.
    public static native String runEngineSelfTest();
}
