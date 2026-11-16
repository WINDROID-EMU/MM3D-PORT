#include "oot3d_android_overlay_input.h"

#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_org_triaevum_android_AndroidOverlayBridge_nativeSetButton(
    JNIEnv*, jclass, jint mask, jboolean pressed) {
    Oot3dNativeGame::GetAndroidOverlayInputState().SetButton(
        static_cast<uint32_t>(mask), pressed == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_triaevum_android_AndroidOverlayBridge_nativeSetSticks(
    JNIEnv*, jclass, jfloat circleX, jfloat circleY,
    jfloat cStickX, jfloat cStickY) {
    Oot3dNativeGame::GetAndroidOverlayInputState().SetSticks(
        circleX, circleY, cStickX, cStickY);
}

extern "C" JNIEXPORT void JNICALL
Java_org_triaevum_android_AndroidOverlayBridge_nativeSetTouch(
    JNIEnv*, jclass, jfloat x, jfloat y, jboolean pressed) {
    Oot3dNativeGame::GetAndroidOverlayInputState().SetTouch(
        x, y, pressed == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_org_triaevum_android_AndroidOverlayBridge_nativeReleaseAll(
    JNIEnv*, jclass) {
    Oot3dNativeGame::GetAndroidOverlayInputState().ReleaseAll();
}
