#pragma once

#include "three_ds_input.h"

#include <cstdint>
#include <mutex>

namespace Oot3dNativeGame {

// UI/JNI writers publish a complete sample; the game thread consumes a copy.
// Stick axes are normalized to [-1, 1], touch coordinates to [0, 1].
struct AndroidOverlayInputSnapshot {
    uint32_t Buttons = 0;
    float CirclePadX = 0.0F;
    float CirclePadY = 0.0F;
    float CStickX = 0.0F;
    float CStickY = 0.0F;
    float TouchX = 0.0F;
    float TouchY = 0.0F;
    bool TouchPressed = false;
};

class AndroidOverlayInputState {
public:
    void SetButton(uint32_t mask, bool pressed) noexcept;
    void SetSticks(float circleX, float circleY, float cStickX, float cStickY) noexcept;
    void SetTouch(float x, float y, bool pressed) noexcept;
    void ReleaseAll() noexcept;
    [[nodiscard]] AndroidOverlayInputSnapshot Snapshot() const noexcept;

private:
    mutable std::mutex mMutex;
    AndroidOverlayInputSnapshot mState;
};

// Merge only the overlay source into an already-polled frame. A neutral
// overlay must never release buttons or axes owned by a physical controller.
void MergeAndroidOverlayInput(const AndroidOverlayInputSnapshot& overlay,
                              ThreeDsRecomp::Input::InputFrame& frame) noexcept;

AndroidOverlayInputState& GetAndroidOverlayInputState() noexcept;

}  // namespace Oot3dNativeGame
