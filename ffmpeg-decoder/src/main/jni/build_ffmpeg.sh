#!/bin/bash
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -euo pipefail

if [[ $# -lt 5 ]]; then
    echo "Usage: $0 MODULE_PATH NDK_PATH HOST_PLATFORM ANDROID_API DECODER..." >&2
    exit 1
fi
MODULE_PATH="$(realpath "$1")"
NDK_PATH="$(realpath "$2")"
HOST_PLATFORM="$3"
ANDROID_API="$4"
ENABLED_DECODERS=("${@:5}")
REPO_ROOT="$(realpath "${MODULE_PATH}/../../..")"
OUTPUT_ROOT="$(realpath "${MODULE_PATH}/jni/ffmpeg")"
SOURCE_ROOT="$(realpath "${FFMPEG_SOURCE_DIR:-$OUTPUT_ROOT}")"
BUILD_ROOT="$(realpath -m "${FFMPEG_BUILD_DIR:-${REPO_ROOT}/build/ffmpeg-android}")"
JOBS="${FFMPEG_BUILD_JOBS:-4}"
TOOLCHAIN_BIN="${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"

[[ "$ANDROID_API" =~ ^[0-9]+$ && "$JOBS" =~ ^[1-9][0-9]*$ ]] || {
    echo "ANDROID_API and FFMPEG_BUILD_JOBS must be integers." >&2; exit 1;
}
[[ -x "${SOURCE_ROOT}/configure" && -x "${TOOLCHAIN_BIN}/llvm-ar" ]] || {
    echo "FFmpeg source or Linux/macOS NDK toolchain is missing." >&2; exit 1;
}
[[ "$(cat "${SOURCE_ROOT}/RELEASE")" == "9.0.2" ]] || {
    echo "This build requires the matching FFmpeg 9.0.2 source tree." >&2; exit 1;
}
# Keep builds in repository outputs or a dedicated, newly created WSL temporary workspace.
case "$BUILD_ROOT/" in
    "$REPO_ROOT/build/"*|"$REPO_ROOT/app/build/"*|/var/tmp/halcyon-ffmpeg9-*/native/) ;;
    *) echo "FFMPEG_BUILD_DIR must be inside this repository's build directory." >&2; exit 1 ;;
esac
EXPECTED_OUTPUT="${REPO_ROOT}/ffmpeg-decoder/src/main/jni/ffmpeg"
[[ "$OUTPUT_ROOT" == "$EXPECTED_OUTPUT" ]] || {
    echo "Unexpected FFmpeg output directory: $OUTPUT_ROOT" >&2; exit 1;
}

COMMON_OPTIONS=(
    --target-os=android --enable-cross-compile --enable-static --disable-shared
    --enable-pic --disable-debug --disable-doc --disable-programs
    --disable-everything --disable-autodetect --disable-network
    --disable-avdevice --disable-avformat --disable-swscale --disable-avfilter
    --disable-symver --enable-swresample --disable-v4l2-m2m --disable-vulkan
    --disable-gpl --disable-nonfree --disable-version3
    --extra-ldexeflags=-pie --extra-ldflags=-Wl,-z,max-page-size=16384
)
for decoder in "${ENABLED_DECODERS[@]}"; do
    [[ "$decoder" =~ ^[a-z0-9_]+$ ]] || { echo "Invalid decoder: $decoder" >&2; exit 1; }
    COMMON_OPTIONS+=("--enable-decoder=${decoder}")
done

echo "Building FFmpeg $(cat "${SOURCE_ROOT}/RELEASE") with NDK $(sed -n 's/^Pkg.Revision = //p' "${NDK_PATH}/source.properties")"
echo "API $ANDROID_API; $JOBS make jobs; decoders: ${ENABLED_DECODERS[*]}"
STAGE_ROOT="${BUILD_ROOT}/stage"
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    api="$ANDROID_API"
    ABI_OPTIONS=()
    case "$abi" in
        armeabi-v7a)
            arch=arm; cpu=armv7-a; triple=armv7a-linux-androideabi
            ABI_OPTIONS+=("--extra-cflags=-march=armv7-a -mfloat-abi=softfp" "--extra-ldflags=-Wl,--fix-cortex-a8,-z,max-page-size=16384") ;;
        arm64-v8a)
            arch=aarch64; cpu=armv8-a; triple=aarch64-linux-android
            (( api >= 21 )) || api=21 ;;
        x86)
            arch=x86; cpu=i686; triple=i686-linux-android
            ABI_OPTIONS+=(--disable-asm) ;;
        x86_64)
            arch=x86_64; cpu=x86-64; triple=x86_64-linux-android
            (( api >= 21 )) || api=21
            ABI_OPTIONS+=(--disable-asm) ;;
    esac
    cc="${TOOLCHAIN_BIN}/${triple}${api}-clang"
    [[ -x "$cc" ]] || { echo "Missing Android compiler: $cc" >&2; exit 1; }
    build_dir="${BUILD_ROOT}/${abi}"
    prefix="${STAGE_ROOT}/${abi}"
    mkdir -p "$build_dir" "$prefix"
    echo "Configuring $abi (API $api)"
    (
        cd "$build_dir"
        "${SOURCE_ROOT}/configure" \
            "--prefix=$prefix" "--arch=$arch" "--cpu=$cpu" \
            "--cc=$cc" "--ld=$cc" \
            "--nm=${TOOLCHAIN_BIN}/llvm-nm" "--ar=${TOOLCHAIN_BIN}/llvm-ar" \
            "--ranlib=${TOOLCHAIN_BIN}/llvm-ranlib" "--strip=${TOOLCHAIN_BIN}/llvm-strip" \
            "${COMMON_OPTIONS[@]}" "${ABI_OPTIONS[@]}"
        # Keep the binary configuration LGPL 2.1+, including auto-selected dependencies.
        for flag in GPL NONFREE VERSION3; do
            grep -q "^#define CONFIG_${flag} 0$" config.h || {
                echo "Forbidden FFmpeg license flag: $flag" >&2; exit 1;
            }
        done
        for decoder in "${ENABLED_DECODERS[@]}"; do
            grep -q "^#define CONFIG_${decoder^^}_DECODER 1$" config_components.h || {
                echo "Requested decoder was not enabled: $decoder" >&2; exit 1;
            }
        done
        make -j"$JOBS"
        make install-libs install-headers
        cp config.h config_components.h ffbuild/config.mak "$prefix/"
    )
    for library in avcodec avutil swresample; do
        [[ -s "$prefix/lib/lib${library}.a" ]] || { echo "Missing $abi $library archive" >&2; exit 1; }
    done
    echo "Completed $abi"
done

# Public API headers are shared; keep each ABI's generated access/endian options
# separate, particularly x86 builds with assembly disabled.
for abi in armeabi-v7a x86 x86_64; do
    diff -r --exclude=avconfig.h "$STAGE_ROOT/arm64-v8a/include" "$STAGE_ROOT/$abi/include"
done
PUBLISH_ROOT="${BUILD_ROOT}/publish-$$"
mkdir -p "$PUBLISH_ROOT/android-libs" "$PUBLISH_ROOT/include"
cp -R "$STAGE_ROOT/arm64-v8a/include/." "$PUBLISH_ROOT/include/"
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    cp "$STAGE_ROOT/$abi/include/libavutil/avconfig.h" "$PUBLISH_ROOT/include/libavutil/avconfig-$abi.h"
done
cat > "$PUBLISH_ROOT/include/libavutil/avconfig.h" <<'EOF'
/* Select the matching FFmpeg-generated Android ABI configuration. */
#ifndef AVUTIL_ANDROID_AVCONFIG_H
#define AVUTIL_ANDROID_AVCONFIG_H
#if defined(__aarch64__)
#include "avconfig-arm64-v8a.h"
#elif defined(__arm__)
#include "avconfig-armeabi-v7a.h"
#elif defined(__i386__)
#include "avconfig-x86.h"
#elif defined(__x86_64__)
#include "avconfig-x86_64.h"
#else
#error Unsupported FFmpeg Android ABI
#endif
#endif /* AVUTIL_ANDROID_AVCONFIG_H */
EOF
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    mkdir -p "$PUBLISH_ROOT/android-libs/$abi"
    for library in avcodec avutil swresample; do
        cp "$STAGE_ROOT/$abi/lib/lib${library}.a" "$PUBLISH_ROOT/android-libs/$abi/"
    done
done

# Publish only once all twelve archives and the matching public headers succeeded.
# Preserve previous inputs in the build workspace rather than deleting a source tree.
BACKUP_ROOT="${BUILD_ROOT}/previous-inputs-$(date +%s)-$$"
mkdir -p "$BACKUP_ROOT"
for directory in include android-libs; do
    [[ "$(realpath -m "$OUTPUT_ROOT/$directory")" == "$EXPECTED_OUTPUT/$directory" ]] || {
        echo "Refusing to replace a redirected FFmpeg input directory." >&2; exit 1;
    }
done
for directory in include android-libs; do
    if [[ -e "$OUTPUT_ROOT/$directory" ]]; then
        mv "$OUTPUT_ROOT/$directory" "$BACKUP_ROOT/$directory"
    fi
    mv "$PUBLISH_ROOT/$directory" "$OUTPUT_ROOT/$directory"
done
rmdir "$PUBLISH_ROOT"
echo "Published FFmpeg inputs for all four Android ABIs; previous inputs: $BACKUP_ROOT"
