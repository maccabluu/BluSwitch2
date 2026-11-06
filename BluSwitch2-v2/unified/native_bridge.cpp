// SPDX-License-Identifier: GPL-3.0-or-later
#include "engine_core.h"
#include "elf_loader.h"
#include "game_demo.h"
#include <jni.h>
#include <string>
extern "C" JNIEXPORT jintArray JNICALL
Java_org_yuzu_yuzu_1emu_ui_main_BluGuestCore_stepMiniGame(
    JNIEnv* env,jclass,jint x,jint y,jint moves,jint won,jint direction) {
    const auto state=bluswitch2::stepMiniGame(x,y,moves,won,direction);
    const jint values[6]={state.x,state.y,state.moves,state.won,
        static_cast<jint>(state.guestInstructions),state.error.empty()?1:0};
    jintArray result=env->NewIntArray(6);
    if(result)env->SetIntArrayRegion(result,0,6,values);
    return result;
}
extern "C" JNIEXPORT jstring JNICALL
Java_org_yuzu_yuzu_1emu_ui_main_BluGuestCore_runCpuTest(JNIEnv* env,jclass) {
    return env->NewStringUTF(bluswitch2::selfTest().c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_org_yuzu_yuzu_1emu_ui_main_BluGuestCore_runElfTest(JNIEnv* env,jclass) {
    return env->NewStringUTF(bluswitch2::elfLoaderSelfTest().c_str());
}
