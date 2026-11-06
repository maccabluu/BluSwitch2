// SPDX-License-Identifier: GPL-3.0-or-later
#include "engine_core.h"
#include <jni.h>
#include <unistd.h>
#include <cstring>
#include <cstdint>
#include <string>

extern "C" JNIEXPORT jint JNICALL
Java_com_blustudio_bluswitch2_v2_NativeCore_inspectExecutable(
    JNIEnv*, jclass, jint fd) {
    if (fd < 0) return -1;
    char magic[4]{};
    const auto bytes = pread(fd, magic, sizeof(magic), 0);
    if (bytes != static_cast<ssize_t>(sizeof(magic))) return -2;
    return std::memcmp(magic, "NSO0", sizeof(magic)) == 0 ? 1 : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_blustudio_bluswitch2_v2_NativeCore_runEngineSelfTest(
    JNIEnv* env, jclass) {
    const std::string report = bluswitch2::selfTest();
    return env->NewStringUTF(report.c_str());
}
