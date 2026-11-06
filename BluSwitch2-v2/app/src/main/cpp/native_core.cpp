// SPDX-License-Identifier: GPL-3.0-or-later
#include "engine_core.h"
#include "game_demo.h"
#include "elf_loader.h"
#include <vector>
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


// Interprets an explicitly selected user-authored test ELF; never loads code
// into the Android process address space or attempts to run console firmware.
extern "C" JNIEXPORT jstring JNICALL
Java_com_blustudio_bluswitch2_v2_NativeCore_runA64Elf(
    JNIEnv* env, jclass, jbyteArray bytes) {
    if (bytes==nullptr) return env->NewStringUTF("No test ELF selected");
    const jsize n=env->GetArrayLength(bytes);
    if (n<64 || n>4*1024*1024)
        return env->NewStringUTF("ELF must be between 64 bytes and 4 MB");
    std::vector<uint8_t> image(static_cast<std::size_t>(n));
    env->GetByteArrayRegion(bytes,0,n,reinterpret_cast<jbyte*>(image.data()));
    if (env->ExceptionCheck()) return nullptr;
    bluswitch2::GuestMemory memory(0x400000,8*1024*1024);
    const auto loaded=bluswitch2::loadTestElf(image,memory);
    if (!loaded.ok) {
        const std::string result="Rejected: "+loaded.error;
        return env->NewStringUTF(result.c_str());
    }
    bluswitch2::A64Cpu cpu(memory);
    cpu.reset(loaded.entry);
    if (!cpu.run(100000)) {
        const std::string result="Test program stopped: "+cpu.failure();
        return env->NewStringUTF(result.c_str());
    }
    const std::string result=
        "Test program completed. X0="+std::to_string(cpu.registerValue(0))+
        ", instructions="+std::to_string(cpu.instructions())+
        ". This does not run Nintendo Switch 2 games.";
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_blustudio_bluswitch2_v2_NativeCore_runLoaderSelfTest(
    JNIEnv* env, jclass) {
    const std::string report=bluswitch2::elfLoaderSelfTest();
    return env->NewStringUTF(report.c_str());
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_blustudio_bluswitch2_v2_NativeCore_stepMiniGame(
    JNIEnv* env, jclass, jint x, jint y, jint moves, jint won, jint direction) {
    const auto state=bluswitch2::stepMiniGame(x,y,moves,won,direction);
    const jint values[6]={
        state.x,state.y,state.moves,state.won,
        static_cast<jint>(state.guestInstructions),state.error.empty()?1:0
    };
    jintArray result=env->NewIntArray(6);
    if(result!=nullptr)env->SetIntArrayRegion(result,0,6,values);
    return result;
}
