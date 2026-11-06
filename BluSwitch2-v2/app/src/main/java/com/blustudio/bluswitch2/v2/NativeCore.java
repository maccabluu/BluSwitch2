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

    // A self-authored ELF fixture, interpreted by the experimental guest CPU.
    public static native String runLoaderSelfTest();

    // Bounded read-only input: static AArch64 test ELF, not a Switch game.
    public static native String runA64Elf(byte[] elfFile);

    // Returns x, y, moves, won, instruction count, success.
    // Movement and victory are decided by the guest AArch64 program.
    public static native int[] stepMiniGame(int x, int y, int moves, int won, int direction);
}
