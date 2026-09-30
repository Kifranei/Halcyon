# FFmpeg audio decoder inputs

These public headers and static archives are built together from official **FFmpeg 9.0.2**.

- Source: <https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz>
- Source SHA-256: `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e`
- Upstream: <https://ffmpeg.org/>, <https://git.ffmpeg.org/ffmpeg.git>
- License of the enabled build: **LGPL-2.1-or-later**. GPL, nonfree and version3 components and external library autodetection are explicitly disabled.
- Toolchain: Android NDK r29 (`29.0.14206865`), Clang 21, Android API 21.
- ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- Libraries in every ABI: `libavcodec.a` (**63.1.102**), `libavutil.a` (**61.1.102**), `libswresample.a` (**7.1.102**).

Enabled decoders: `alac`, `aac`, `ape`, `mp3`, `vorbis`, `opus`, `flac`, `ac3`, `eac3`, `truehd`, `dca`, `amrnb`, `amrwb`, `pcm_mulaw`, `pcm_alaw`.

The configuration builds static, position independent audio decoding and resampling only. Programs, networking, devices, demuxing/muxing, filters and scaling are disabled. x86/x86_64 retain the previous assembly-disabled policy. APE/ALAC decoding and the JNI packet/frame reuse are preserved.

Public headers come from `make install-headers`; private implementation headers are intentionally excluded. `libavutil/avconfig.h` selects the matching original generated ABI configuration. ARM configurations enable `AV_HAVE_FAST_UNALIGNED`; the x86 configurations built without assembly disable it. Other public headers are checked to match across all four ABIs before publication.

The JNI shared library links with `-Wl,-z,max-page-size=16384` and `-Wl,--exclude-libs,ALL`. JNI exports remain available while static FFmpeg symbols stay hidden, allowing the independent FFmpegKit libraries to coexist without symbol interposition.

Rebuild on Windows with `build_ffmpeg.ps1`. It downloads this exact version with checksum verification and builds through an existing WSL installation with `make`, `gcc`, `libc6-dev`, `xz-utils`, `pkg-config` and `unzip`. Sources/toolchains build in a dedicated Linux temporary workspace, with four make jobs by default. All four ABIs must succeed before the matching headers and archives replace the previous inputs.

The build shell script rejects source releases other than 9.0.2. The PowerShell wrapper supplies the pinned source tree explicitly; an old ignored source tree left beside these inputs is not used.

Rebuild verification on 2026-10-07 checked all twelve archive architectures and compiled version constants, all fifteen decoder definitions, LGPL-only configuration, direct JNI compile/link for all four ABIs, 16 KB ELF load alignment, and hidden FFmpeg dynamic symbols. These checks validate compilation and linkage; playback on an Android device requires separate runtime verification.
