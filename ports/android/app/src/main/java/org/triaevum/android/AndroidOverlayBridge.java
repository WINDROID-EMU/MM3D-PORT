package org.triaevum.android;

import android.app.Activity;
import android.util.Log;
import org.triaevum.android.controls.EmulationMenuSettings;
import org.triaevum.android.controls.InputOverlay;
import org.triaevum.android.controls.Native3dsInputTarget;

/** Routes the Azahar control view into the Android-only native input owner. */
final class AndroidOverlayBridge implements Native3dsInputTarget {
    private final Activity activity;
    private InputOverlay overlay;
    private float circleX;
    private float circleY;
    private float cStickX;
    private float cStickY;
    private boolean touchPressed;

    AndroidOverlayBridge(Activity activity) {
        this.activity = activity;
        nativeSetScreenSwap(EmulationMenuSettings.INSTANCE.getSwapScreens());
    }

    void attach(InputOverlay overlay) {
        this.overlay = overlay;
    }

    @Override public void button(int hidMask, boolean pressed) {
        nativeSetButton(hidMask, pressed);
    }

    @Override public void circlePad(float x, float y) {
        circleX = x;
        circleY = y;
        publishSticks();
    }

    @Override public void cStick(float x, float y) {
        cStickX = x;
        cStickY = y;
        publishSticks();
    }

    private void publishSticks() {
        nativeSetSticks(circleX, circleY, cStickX, cStickY);
    }

    @Override public void home(boolean pressed) {
        if (pressed) activity.finish();
    }

    @Override public void touchPixels(float x, float y, boolean pressed) {
        touchPressed = pressed;
        publishTouch(x, y, pressed);
    }

    @Override public void moveTouchPixels(float x, float y) {
        if (touchPressed) publishTouch(x, y, true);
    }

    private void publishTouch(float x, float y, boolean pressed) {
        InputOverlay view = overlay;
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            if (!pressed) nativeSetTouch(0.0f, 0.0f, false);
            return;
        }
        // View and SDL window share the Activity content bounds. Native input then
        // applies the live screen/letterbox transform before producing guest touch.
        nativeSetTouch(x / view.getWidth(), y / view.getHeight(), pressed);
    }

    @Override public void swapScreens(boolean enabled, int displayRotation) {
        nativeSetScreenSwap(enabled);
    }

    @Override public void toggleTurbo(boolean fromOverlay) {
        Log.w("TriAevum", "Turbo is not implemented by the MM3D Android runtime yet");
    }

    @Override public void releaseAll() {
        circleX = circleY = cStickX = cStickY = 0.0f;
        touchPressed = false;
        nativeReleaseAll();
        nativeSetScreenSwap(EmulationMenuSettings.INSTANCE.getSwapScreens());
    }

    private static native void nativeSetButton(int hidMask, boolean pressed);
    private static native void nativeSetSticks(float circleX, float circleY,
                                               float cStickX, float cStickY);
    private static native void nativeSetTouch(float x, float y, boolean pressed);
    private static native void nativeSetScreenSwap(boolean enabled);
    private static native void nativeReleaseAll();
}
