#include "oot3d_android_overlay_input.h"

#include <algorithm>
#include <cmath>

namespace Oot3dNativeGame {
namespace {
float ClampAxis(float value) noexcept {
    if (!std::isfinite(value)) return 0.0F;
    return std::clamp(value, -1.0F, 1.0F);
}
float ClampTouch(float value) noexcept {
    if (!std::isfinite(value)) return 0.0F;
    return std::clamp(value, 0.0F, 1.0F);
}
}  // namespace

void AndroidOverlayInputState::SetButton(uint32_t mask, bool pressed) noexcept {
    std::lock_guard lock(mMutex);
    if (pressed) mState.Buttons |= mask;
    else mState.Buttons &= ~mask;
}

void AndroidOverlayInputState::SetSticks(float circleX, float circleY,
                                          float cStickX, float cStickY) noexcept {
    std::lock_guard lock(mMutex);
    mState.CirclePadX = ClampAxis(circleX);
    mState.CirclePadY = ClampAxis(circleY);
    mState.CStickX = ClampAxis(cStickX);
    mState.CStickY = ClampAxis(cStickY);
}

void AndroidOverlayInputState::SetTouch(float x, float y, bool pressed) noexcept {
    std::lock_guard lock(mMutex);
    mState.TouchX = ClampTouch(x);
    mState.TouchY = ClampTouch(y);
    mState.TouchPressed = pressed;
}

void AndroidOverlayInputState::ReleaseAll() noexcept {
    std::lock_guard lock(mMutex);
    mState = {};
}

AndroidOverlayInputSnapshot AndroidOverlayInputState::Snapshot() const noexcept {
    std::lock_guard lock(mMutex);
    return mState;
}

void MergeAndroidOverlayInput(const AndroidOverlayInputSnapshot& overlay,
                              ThreeDsRecomp::Input::InputFrame& frame) noexcept {
    frame.Hid.Buttons |= overlay.Buttons;
    constexpr float kNativeStickScale = static_cast<float>(
        ThreeDsRecomp::Input::kNativeStickMaximum);
    if (overlay.CirclePadX != 0.0F || overlay.CirclePadY != 0.0F) {
        frame.Hid.CirclePadX = static_cast<std::int16_t>(
            std::lround(overlay.CirclePadX * kNativeStickScale));
        frame.Hid.CirclePadY = static_cast<std::int16_t>(
            std::lround(overlay.CirclePadY * kNativeStickScale));
    }
    if (overlay.CStickX != 0.0F || overlay.CStickY != 0.0F) {
        frame.CStick.X = static_cast<std::int16_t>(
            std::lround(overlay.CStickX * kNativeStickScale));
        frame.CStick.Y = static_cast<std::int16_t>(
            std::lround(overlay.CStickY * kNativeStickScale));
        frame.CStick.Kind = ThreeDsRecomp::Input::AxisInputKind::Absolute;
    }
}

AndroidOverlayInputState& GetAndroidOverlayInputState() noexcept {
    static AndroidOverlayInputState state;
    return state;
}

}  // namespace Oot3dNativeGame
