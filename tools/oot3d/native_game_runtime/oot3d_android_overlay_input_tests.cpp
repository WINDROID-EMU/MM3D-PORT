#include "oot3d_android_overlay_input.h"

#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>

namespace {
void Require(bool condition, const char* message) {
    if (!condition) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(1);
    }
}
}

int main() {
    Oot3dNativeGame::AndroidOverlayInputState state;
    state.SetButton(1U << 3U, true);
    state.SetButton(1U << 0U, true);
    state.SetButton(1U << 3U, false);
    auto sample = state.Snapshot();
    Require(sample.Buttons == (1U << 0U), "button edges preserve unrelated held buttons");

    state.SetSticks(2.0F, -2.0F, 0.25F, std::numeric_limits<float>::quiet_NaN());
    state.SetTouch(-1.0F, 2.0F, true);
    sample = state.Snapshot();
    Require(sample.CirclePadX == 1.0F && sample.CirclePadY == -1.0F,
            "circle pad is clamped to HID normalized range");
    Require(sample.CStickX == 0.25F && sample.CStickY == 0.0F,
            "non-finite c-stick input is neutralized");
    Require(sample.TouchX == 0.0F && sample.TouchY == 1.0F && sample.TouchPressed,
            "touch coordinates are clamped and retain press state");

    state.ReleaseAll();
    sample = state.Snapshot();
    Require(sample.Buttons == 0 && sample.CirclePadX == 0.0F &&
                sample.CirclePadY == 0.0F && sample.CStickX == 0.0F &&
                sample.CStickY == 0.0F && !sample.TouchPressed,
            "release clears the overlay source without changing other input owners");
    std::cout << "Android overlay input state tests passed\n";
}
