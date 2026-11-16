# Android Native Foundation

Status: developer APK now links and launches the actual ARM64 title/runtime with
Vulkan/NRI. Two-minute warm-cache intro samples reach 30 presentations/s and
29.0-29.8 native updates/s without interpolation on Adreno 830. Residual pacing spikes,
gameplay, input and installer integration remain; this is not a supported release.
See [intro bring-up and reproduction](INTRO.md) for current results and commands.
See [shader preparation](SHADER_PREPARATION.md) for device/cache qualification.
Build on Linux with the Android NDK; the existing Windows PC may provide the USB
ADB connection without hosting a second compiler/SDK installation.

## Boundaries

- Retain the existing Vulkan/NRI renderer, native PICA rendering, gameplay and
  shared service interfaces. No Android-specific renderer or new decompilation.
- `NativePresentationPolicy` applies the exact desktop F2 mask on Android:
  Grass, Toon/outline, CACAO and reflections Off. This is not the Authentic
  preset. Configured values, interpolation, FOV, native lighting, UI composition
  and unrelated options remain unchanged. Desktop F2 remains reversible.
- Platform lifecycle, surfaces, input devices, storage and audio-device binding
  belong to the Android host, outside title logic and canonical PICA semantics.
- The publisher cross-compiles the existing manifest-verified translated C++
  title for `arm64-v8a`. No x86 module is reused. Players do not install a compiler.
- ROM import must use Android's document picker and a native import service.
  Document URIs are not filesystem paths. The desktop Tk Forge UI is not ported.
- The release catalog deliberately does not advertise Android yet. An ARM64
  library that links is not proof of renderer presentation or playable boot.

## Linux Build

Qualified compiler for this foundation: Android NDK r29 (`29.0.14206865`),
Clang 21; CMake >= 3.30 and Ninja. API 29 is the initial build floor, not a
claim that all API-29 GPUs satisfy the renderer's capability requirements.
NDK r29 produces 16-KiB-aligned ELF binaries; check every packaged native library,
including `libc++_shared.so`, rather than assuming all dependencies are aligned.

```sh
export ANDROID_NDK_HOME="$HOME/triaevum-android-tools/android-ndk-r29"
export TRIAEVUM_BUILD_DIR="$HOME/triaevum-android-build/arm64"
export CMAKE_BUILD_PARALLEL_LEVEL=3
bash scripts/build-android-native.sh
python3 -m unittest tools.triaevum_release.tests.test_translated_title_sources
# Separate, expensive publisher operation; never part of routine host iteration.
export TRIAEVUM_TRANSLATED_TITLE_DIR="$HOME/triaevum-linux-deps/title-source"
TRIAEVUM_BUILD_TITLE=1 bash scripts/build-android-native.sh
```

The native target builds the shared module services, existing memory/VFP support,
the real plugin loader, native-presentation policy, pinned NRI Vulkan core and
their probes. With title sources supplied and `TRIAEVUM_BUILD_TITLE=1` it also builds
`libtriaevum_title_aot.so`. Full source compilation is a publisher cost, separate
from fast incremental renderer/host builds.

NRI is pinned to the same revision as desktop. A small idempotent CMake patch
admits Android, whose Vulkan loader branch already exists upstream. This build
does not enable NRI's optional desktop SDK integrations. The separate renderer
build now compiles and links the actual shared TriAevum PICA/Vulkan/NRI backend:

```sh
export TRIAEVUM_ANDROID_RENDERER_BUILD_DIR="$HOME/triaevum-android-build/renderer"
bash scripts/build-android-renderer.sh
```

This cross-build uses pinned shaderc/glslang/SPIRV-Tools sources, never Linux host
shader libraries. API-29 loader compatibility resolves `vkCreateRenderPass2`
from the selected device, preserving the existing Vulkan render-pass semantics.
The link probe exercises real renderer construction and CPU shader compilation;
it does not create an Android window or present a game frame.

## Majora's Mask 3D module and private APK flow

The MM3D ROM-preparation pipeline can now target `aarch64-linux-android29` and
emit an ARM64 Android title plugin. Its APK name is `libmm3d_title_aot.so`,
distinct from the generic/OoT module name, so the launcher refuses the wrong game.
First build the NDK whole-AOT support archive with `scripts/build-android-native.sh`,
then locate it in that build tree. From a prepared MM3D setup package (which contains
the recipe and `include/`):

```sh
export ANDROID_NDK_HOME="$HOME/triaevum-android-tools/android-ndk-r29"
export TRIAEVUM_BUILD_DIR="$HOME/triaevum-android-build/arm64"
bash scripts/build-android-native.sh
NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
SUPPORT_LIBRARY="$(find "$TRIAEVUM_BUILD_DIR" -name libtriaevum_title_whole_aot_support.a -print -quit)"
python3 setup/TriAevum/tools/mm3d/package/mm3d_setup.py \
  --install "$HOME/mm3d-android-data" \
  --rom "$HOME/ROMs/MM3D-USA-v1.1.3ds" \
  --target android \
  --cxx "$NDK_BIN/aarch64-linux-android29-clang++" \
  --ar "$NDK_BIN/llvm-ar" \
  --support-library "$SUPPORT_LIBRARY" \
  --jobs 2
python3 tools/android/prepare_mm3d_android_data.py \
  --installation "$HOME/mm3d-android-data" \
  --output "$HOME/mm3d-android-stage"
python3 tools/android/package_mm3d_android_data.py \
  --stage "$HOME/mm3d-android-stage" \
  --output "$HOME/mm3d-android-data.zip"
```

Replace the example paths with local files. Do not copy ROM-derived data into
the repository. This produces `private/mm3d_title_aot.so`, relocatable app data
and a private transfer ZIP; the ZIP deliberately excludes native modules, saves
and renderer caches.

Build the shared Android runtime and the developer APK separately. These commands
follow the Android runtime's qualified NDK/CMake/Gradle toolchain; the complete
APK flow has not yet been run in this sandbox:

```sh
SRC="$PWD"
BUILD="$HOME/triaevum-android-build/mm3d"
NDK="$ANDROID_NDK_HOME"
cmake -S "$SRC/ports/android/runtime" -B "$BUILD/runtime" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 \
  -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD/runtime" --target oot3d_native_game --parallel 2
SDL_SOURCE="$BUILD/runtime/_deps/sdl2-src"
python3 "$SRC/tools/android/stage_native_app.py" \
  --runtime-build "$BUILD/runtime" \
  --title-module "$HOME/mm3d-android-data/private/mm3d_title_aot.so" \
  --title-library-name libmm3d_title_aot.so --ndk "$NDK" \
  --output "$BUILD/mm3d-apk-libs"
gradle -p "$SRC/ports/android" :app:assembleDebug \
  -PtriaevumSdlSource="$SDL_SOURCE" \
  -PtriaevumNativeStage="$BUILD/mm3d-apk-libs" \
  -PtriaevumTitleLibrary=libmm3d_title_aot.so --no-daemon --max-workers=2
```

Install `ports/android/app/build/outputs/apk/debug/app-debug.apk`, then use the
launcher to choose `mm3d-android-data.zip` from Downloads or another document
provider. The transfer ZIP contains private game data; do not publish or share
it, and remove it from shared storage after import if it is no longer needed. The
preparer requires the process manifest at the installation root and records a
SHA-256 for the complete RomFS image. The launcher validates the title ID, launch
arguments, code and RomFS hashes, ROMFS bounds, ZIP paths and 1-GiB per-entry / 2-GiB
total unpacked limits before atomically replacing its private `mm3d-data` directory;
existing save/config/cache state is preserved.
Game data is **not** included in APK assets. Import/launch code is implemented but
still needs an Android SDK/Gradle build and real-device verification.

The isolated [Azahar controls library](controls/README.md) imports the original
overlay and controller artwork with a narrow native-3DS input adapter. The Android
runtime now has a thread-safe JNI/HID input bridge for buttons, both sticks and
normalized touch, consumed on the game polling thread while preserving physical
controller state. Host-side tests cover button edges, axis clamping, release,
combination with held physical buttons and preservation of physical axes while
the virtual source is neutral.
The APK still does not bind the Azahar view/lifecycle to this bridge; transparent
surface composition, cancel/pause handling, settings and device multitouch remain
the next integration milestone. Its AAR and adapter tests build independently of
all C++ title/renderer compilation.

Run the platform-independent bridge test with:

```sh
g++ -std=c++20 -Wall -Wextra -Werror -pthread -I tools/three_ds/input \
  tools/oot3d/native_game_runtime/oot3d_android_overlay_input.cpp \
  tools/oot3d/native_game_runtime/oot3d_android_overlay_input_tests.cpp \
  -o /tmp/oot3d_android_overlay_input_tests
/tmp/oot3d_android_overlay_input_tests
```
The SDL APK host exists; live-game overlay integration is deliberately deferred
until title-intro playback works.

## Device Verification

For direct video, screenshots and optional app-filtered logs, use the isolated
[scrcpy capture workflow](CAPTURE.md). Windows may capture over USB while Linux
continues to build; no desktop screenshot capture is involved.

Use `adb devices -l` before deployment. A USB/MTP device without an ADB entry
needs debugging enabled and the host authorized on the unlocked device.
Do not expose the ADB server on a public/network interface. Use local USB,
or a loopback-only SSH tunnel when Windows is the USB bridge.

`native_presentation_policy_tests`, the module service tests and loader probes
are ARM64 Android executables: do not run them on the Linux x86 host or count
successful cross-compilation as passing on-device tests. Deploy them with the
NDK's ARM64 `libc++_shared.so` to a private `/data/local/tmp` test directory.

`triaevum_android_nri_probe` checks adapter/device/graphics queue creation only.
It is not a framebuffer or performance test. The actual renderer wraps a device
with its own capability negotiation; the standalone NRI creation defaults are
not an authoritative mobile device-compatibility gate.

## Next Deliverable

1. Complete and validate intro presentation through the APK/SDL host; qualify
   surface recreation, pause/resume and audio lifecycle afterward.
2. Wire the imported Azahar overlay, settings editor and shared input owner.
3. Add ROM selection/import and package the precompiled ARM64 title and runtime.
4. Test real on-device startup, native framebuffer output, audio, input and
   resume. Qualify capability requirements and performance on actual hardware.

Keep the currently installed Linux Flatpak and desktop distribution untouched.
Toolchains, ROMs, game-derived data and build directories are never public source
or APK payloads. Preserve existing donor licenses and source-distribution rules.

## Evidence (2026-09-09)

Historical foundation milestones below are superseded by [INTRO.md](INTRO.md)
for the full title/APK build and active-device status.

- Linux-hosted r29 cross-build passed for the pinned NRI core/device probe,
  actual title memory/VFP support and loader, module services, input/audio/storage
  tests, policy test and empty/mock shared modules. ELF inspection identifies
  Android API 29, AArch64 and `/system/bin/linker64`; the inspected shared module
  has 16-KiB `PT_LOAD` alignment.
- Host policy test passed; shared inventory parser passed seven positive/negative
  tests. The real desktop F1/Controls/TopScreen widget smoke passed 1,761
  assertions after rebuilding the changed runtime components in Steam Runtime 4.
- Full title compilation was attempted from the existing verified 256-shard
  inventory, not regenerated or decompiled. Several shards compiled, but individual
  ARM64 optimized compilations took minutes and up to approximately 3.2 GiB RSS.
  The full-title build was stopped deliberately with completed objects retained;
  there is no linked/qualified Android game module yet. It is explicitly excluded
  from the default rapid build. Investigate compiler timings before release builds.
- Follow-up: ADB now authorizes the SM-S931B (Android 16/API 36, arm64-v8a),
  which advertises Vulkan support. Direct scrcpy capture works; see CAPTURE.md.
- On-device ARM64 probes now pass: native presentation policy, input/audio/
  filesystem services, runtime memory layout, empty-title rejection, TAM
  metadata and mock module loading. NRI creates a Vulkan 1.3 device/graphics
  queue on Adreno 830. The linked shared-renderer probe also compiles a GLSL
  vertex shader to SPIR-V on the phone and exits successfully.
- Android TAM target matching now accepts `aarch64-linux-android` and rejects
  desktop GNU/Linux modules of the same CPU architecture. Positive/negative
  metadata and loader tests pass on both Android and Linux.
- These are shell-UID probes, not APK sandbox tests. They establish neither
  surface presentation nor permission to execute a library from writable app
  storage. APK-native libraries must be packaged for the platform loader.
- The actual shared renderer cross-build completed 1,017 build steps. The
  subsequent render-pass loader correction rebuilt and linked in about seven
  seconds, without recompiling the translated title.
- The controls AAR and four adapter tests pass; three offline import/resource/
  ABI tests pass. In-game multitouch/editor/overlay composition still require
  the APK host. No APK, gameplay or mobile FPS claim is established.

References: [NDK downloads](https://developer.android.com/ndk/downloads/index.html),
[16-KiB page support](https://developer.android.com/guide/practices/page-sizes),
[platform tools](https://developer.android.com/tools/releases/platform-tools),
[renderer architecture](../../docs/OOT3D_NRI_FIDELITY_EXTENSION_ARCHITECTURE.md).
