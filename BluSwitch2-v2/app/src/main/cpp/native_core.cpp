// SPDX-License-Identifier: GPL-3.0-or-later
// Original, read-only executable header probe. Not a Switch 2 emulator.
#include <jni.h>
#include <unistd.h>
#include <cstring>
#include <cstdint>

extern "C" JNIEXPORT jint JNICALL
Java_com_blustudio_bluswitch2_v2_NativeCore_inspectExecutable(
    JNIEnv*, jclass, jint fd) {
    if (fd < 0) return -1;
    char magic[4]{};
    const auto bytes = pread(fd, magic, sizeof(magic), 0);
    if (bytes != static_cast<ssize_t>(sizeof(magic))) return -2;
    return std::memcmp(magic, "NSO0", sizeof(magic)) == 0 ? 1 : 0;
}
