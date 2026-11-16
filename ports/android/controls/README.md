# Azahar Android Controls

This Android library ports Azahar's actual input overlay, not a visual imitation.
It has no emulator, title-code, SDL or renderer dependency. The Vulkan/NRI game
surface remains owned by TriAevum's Android host.

## Preservation Contract

- The four `InputOverlay*.kt` classes preserve drawing, button artwork, pointer
  tracking, D-pad and button sliding, Circle Pad/C-stick movement, relative stick
  centering, haptic feedback, layout editing, portrait/landscape defaults, control
  visibility, global/per-control scale and opacity preference behavior.
- All referenced artwork/density variants and layout integers come from the same
  donor snapshot. No substitute shapes or game-derived textures are introduced.
- Adaptations are limited to namespace, application context and host callbacks;
  an unused activity viewmodel and unrelated drawer-lock setting are omitted.
- `AzaharInputAdapter` translates donor IDs to the existing 3DS HID bits. It
  preserves the donor JNI's Y inversion and circular stick normalization. It
  never maps them to keyboard keys or game-specific actions. HOME, screen swap
  and turbo remain separate host actions, not fictitious HID buttons.
- Original copyright headers and `license.txt` remain. The manifest records
  every source hash and each imported/adapted output hash. The upstream overlay
  is last changed at `3716f6b9b63ee5271fe5a1c4327972e02158834d`; the donor snapshot
  is the same `beb5681...` already used by this project's Azahar imports.

## Host Integration

`TriAevumActivity` binds `OverlayHost` before constructing the imported overlay.
The overlay is a normal transparent `View` above SDL's `SurfaceView`; it does not
create a competing Android surface. `AndroidOverlayBridge` implements the donor
`Native3dsInputTarget` and routes buttons, Circle Pad, C-stick and pointer pixels
through JNI into the thread-safe native owner. Buttons/sticks merge with, rather
than replace, physical controller state. The native touch mapper remains
responsible for the live lower-screen/layout/letterbox transform; normalized
overlay coordinates only identify a host-window pointer.

The app releases virtual input on `ACTION_CANCEL`, pause, focus loss and teardown,
resets drawable pointer/axis state, removes the view and unbinds the adapter. HOME
closes the game Activity. Screen swap toggles between the runtime's existing
`SingleScreen` and `LowerFocus` MM3D layouts. Turbo has no runtime implementation
yet; the callback reports that limitation rather than pretending to accelerate.
Donor edit operations and preference keys remain available, but no in-app control
settings/editor menu is wired yet.

## Status And Remaining Work

The imported controls library and four JVM adapter tests were previously verified
on Linux (JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.0.20, API 35 SDK, min API 29).
Host-side C++ tests cover HID merge, clamping and release. This environment does
not currently have the Android SDK/Gradle/JDK compiler, so the new app/Activity
composition has not been built here. No test verifies real Android MotionEvents,
rendered composition or gameplay on hardware.

Still required before calling the Android port complete: a successful APK/SDL
build, device validation of transparent composition and physical/virtual
multitouch, donor settings/editor menu wiring, and a true turbo implementation.
The imported donor's
existing quirks are not silently changed: for example its joystick change
notification compares both axes with `&&`; isolate and test any future fix,
rather than claiming the adapter tests validate that gesture logic.

## Reproducible Checks

```sh
python3 -m unittest tools.android.test_azahar_overlay_import
# Optional donor verification, read-only. Not needed for normal builds.
python3 tools/android/import_azahar_overlay.py /path/to/azahar --check
export ANDROID_HOME="$HOME/triaevum-android-tools/sdk"
"$HOME/triaevum-android-tools/gradle-8.13/bin/gradle" -p ports/android \
    :controls:assembleDebug :controls:testDebugUnitTest --console=plain
```

No SDK, build output or game input is committed. Tool installation uses the
[official Android SDK tools](https://developer.android.com/tools/sdkmanager).
