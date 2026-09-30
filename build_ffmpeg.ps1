# Build pinned LGPL-only FFmpeg audio decoders through an existing WSL installation.
$ErrorActionPreference = "Stop"
$REPO_ROOT = $PSScriptRoot
$FFMPEG_MODULE_PATH = Join-Path $REPO_ROOT "ffmpeg-decoder\src\main"
$FFMPEG_VERSION = "9.0.2"
$FFMPEG_SHA256 = "8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e"
$SOURCE_PARENT = Join-Path $REPO_ROOT "build\ffmpeg-sources"
$SOURCE_ARCHIVE = Join-Path $SOURCE_PARENT "ffmpeg-$FFMPEG_VERSION.tar.xz"
$LINUX_NDK_VERSION = "r29"
$LINUX_NDK_PARENT = Join-Path $REPO_ROOT "build\android-ndk-linux"
$LINUX_NDK_ARCHIVE = Join-Path $LINUX_NDK_PARENT "android-ndk-$LINUX_NDK_VERSION-linux.zip"
$ENABLED_DECODERS = @("alac", "aac", "ape", "mp3", "vorbis", "opus", "flac", "ac3", "eac3", "truehd", "dca", "amrnb", "amrwb", "pcm_mulaw", "pcm_alaw")

function ConvertTo-WslPath([string]$Path) {
    $absolute = [System.IO.Path]::GetFullPath($Path)
    if ($absolute -notmatch '^[A-Za-z]:\\') { throw "Expected an absolute Windows drive path: $Path" }
    return "/mnt/" + $absolute.Substring(0, 1).ToLowerInvariant() + $absolute.Substring(2).Replace("\", "/")
}

function ConvertTo-ShellLiteral([string]$Value) {
    return "'" + $Value.Replace("'", "'\''") + "'"
}

New-Item -ItemType Directory -Force -Path $LINUX_NDK_PARENT, $SOURCE_PARENT, (Join-Path $FFMPEG_MODULE_PATH "jni\ffmpeg") | Out-Null
if (-not (Test-Path -LiteralPath $LINUX_NDK_ARCHIVE)) {
    curl.exe --fail --location "https://dl.google.com/android/repository/android-ndk-$LINUX_NDK_VERSION-linux.zip" --output $LINUX_NDK_ARCHIVE
    if ($LASTEXITCODE -ne 0) { throw "Linux NDK download failed." }
}
if (-not (Test-Path -LiteralPath $SOURCE_ARCHIVE)) {
    curl.exe --fail --location "https://ffmpeg.org/releases/ffmpeg-$FFMPEG_VERSION.tar.xz" --output $SOURCE_ARCHIVE
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg source download failed." }
}
if ((Get-FileHash -LiteralPath $SOURCE_ARCHIVE -Algorithm SHA256).Hash.ToLowerInvariant() -ne $FFMPEG_SHA256) {
    throw "FFmpeg source checksum mismatch; expected the official $FFMPEG_VERSION archive."
}

# Extract the Linux NDK in Linux: this preserves its original symlinks and avoids
# slow per-file compiler/header access through the Windows filesystem mount.
# Each run uses a fresh temporary directory; neither sources nor previous inputs are deleted.
$runner = Join-Path $SOURCE_PARENT "build-android.sh"
$buildJobs = if ($env:FFMPEG_BUILD_JOBS) { $env:FFMPEG_BUILD_JOBS } else { "4" }
$moduleLiteral = ConvertTo-ShellLiteral (ConvertTo-WslPath $FFMPEG_MODULE_PATH)
$scriptLiteral = ConvertTo-ShellLiteral (ConvertTo-WslPath (Join-Path $FFMPEG_MODULE_PATH "jni\build_ffmpeg.sh"))
$ndkArchiveLiteral = ConvertTo-ShellLiteral (ConvertTo-WslPath $LINUX_NDK_ARCHIVE)
$sourceArchiveLiteral = ConvertTo-ShellLiteral (ConvertTo-WslPath $SOURCE_ARCHIVE)
$lines = @(
    '#!/bin/bash',
    'set -euo pipefail',
    'for tool in make gcc xz pkg-config unzip; do command -v "$tool" >/dev/null || { echo "WSL requires make gcc libc6-dev xz-utils pkg-config unzip" >&2; exit 1; }; done',
    'task_tmp=$(mktemp -d /var/tmp/halcyon-ffmpeg9-XXXXXX)',
    '[[ "$task_tmp" =~ ^/var/tmp/halcyon-ffmpeg9-[A-Za-z0-9]+$ && ! -L "$task_tmp" ]] || exit 1',
    'printf "Native build workspace: %s\n" "$task_tmp"',
    ("unzip -oq " + $ndkArchiveLiteral + ' -d "$task_tmp"'),
    'mkdir -p "$task_tmp/source"',
    ("tar -xJf " + $sourceArchiveLiteral + ' -C "$task_tmp/source"'),
    ('export FFMPEG_SOURCE_DIR="$task_tmp/source/ffmpeg-' + $FFMPEG_VERSION + '"'),
    'export FFMPEG_BUILD_DIR="$task_tmp/native"',
    ("export FFMPEG_BUILD_JOBS=" + (ConvertTo-ShellLiteral $buildJobs)),
    ('[[ "$(cat "$FFMPEG_SOURCE_DIR/RELEASE")" == "' + $FFMPEG_VERSION + '" ]] || exit 1'),
    ('sed ''s/\r$//'' ' + $scriptLiteral + ' > "$task_tmp/build_ffmpeg.sh"'),
    ('bash "$task_tmp/build_ffmpeg.sh" ' + $moduleLiteral + ' "$task_tmp/android-ndk-' + $LINUX_NDK_VERSION + '" linux-x86_64 21 ' + ($ENABLED_DECODERS -join " "))
)
[System.IO.File]::WriteAllText($runner, ($lines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Host "Building FFmpeg $FFMPEG_VERSION for all four Android ABIs (LGPL-only)."
wsl.exe -- bash (ConvertTo-WslPath $runner)
if ($LASTEXITCODE -ne 0) { throw "FFmpeg native build failed; previous inputs have been retained." }

Push-Location $REPO_ROOT
try {
    .\gradlew.bat :ffmpeg-decoder:assembleRelease -PellaBuildNative=true -PellaAbi=arm64-v8a
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg JNI build failed." }
    $builtSo = Join-Path $REPO_ROOT "ffmpeg-decoder\build\intermediates\stripped_native_libs\release\stripReleaseDebugSymbols\out\lib\arm64-v8a\libffmpegJNI.so"
    if (-not (Test-Path -LiteralPath $builtSo)) { throw "Built FFmpeg JNI library was not found." }
    $outputDir = Join-Path $FFMPEG_MODULE_PATH "jniLibs\arm64-v8a"
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
    Copy-Item -LiteralPath $builtSo -Destination (Join-Path $outputDir "libffmpegJNI.so") -Force
} finally { Pop-Location }
Write-Host "FFmpeg $FFMPEG_VERSION headers, four-ABI static libraries, and arm64 JNI prebuilt updated."
Write-Host "Build the APK with .\gradlew.bat :app:assembleRelease"
