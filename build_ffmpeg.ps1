# build_ffmpeg_windows.ps1
# Run this script in PowerShell to build FFmpeg for Android
# Requires: WSL (Windows Subsystem for Linux) with git, make, and Android NDK

$ErrorActionPreference = "Stop"

$REPO_ROOT = $PSScriptRoot
$FFMPEG_MODULE_PATH = Join-Path $REPO_ROOT "ffmpeg-decoder\src\main"
$SDK_PATH = $env:ANDROID_HOME
$FFMPEG_PREBUILT_DIR = Join-Path $FFMPEG_MODULE_PATH "jni\ffmpeg"
$LINUX_NDK_VERSION = "r29"
$LINUX_NDK_URL = "https://dl.google.com/android/repository/android-ndk-$LINUX_NDK_VERSION-linux.zip"
$LINUX_NDK_PARENT = Join-Path $REPO_ROOT "build\android-ndk-linux"
$LINUX_NDK_PATH = Join-Path $LINUX_NDK_PARENT "android-ndk-$LINUX_NDK_VERSION"

function ConvertTo-WslPath([string]$Path) {
    $resolved = (Resolve-Path $Path).Path
    $drive = $resolved.Substring(0, 1).ToLowerInvariant()
    $rest = $resolved.Substring(2).Replace("\", "/")
    return "/mnt/$drive$rest"
}

function Repair-LinuxNdkSymlinks([string]$NdkPath) {
    $wslNdk = ConvertTo-WslPath $NdkPath
    $wslBin = "$wslNdk/toolchains/llvm/prebuilt/linux-x86_64/bin"
    $links = @{
        "clang" = "clang-21"
        "clang++" = "clang"
        "ld" = "ld.lld"
        "ld.lld" = "lld"
        "ld64.lld" = "lld"
        "lld-link" = "lld"
        "llvm-addr2line" = "llvm-symbolizer"
        "llvm-dlltool" = "llvm-ar"
        "llvm-lib" = "llvm-ar"
        "llvm-ranlib" = "llvm-ar"
        "llvm-readelf" = "llvm-readobj"
        "llvm-strip" = "llvm-objcopy"
        "llvm-windres" = "llvm-rc"
        "perf2bolt" = "llvm-bolt"
        "wasm-ld" = "lld"
    }
    foreach ($entry in $links.GetEnumerator()) {
        $link = $entry.Key
        $target = $entry.Value
        wsl bash -lc "cd '$wslBin' && if [ -e '$target' ] && [ ! -L '$link' ]; then rm -f '$link' && ln -s '$target' '$link'; fi"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Failed to repair Linux NDK symlink $link -> $target."
            exit 1
        }
    }
}

function Reset-FfmpegPrebuiltDirectory {
    if (Test-Path $FFMPEG_PREBUILT_DIR) {
        Remove-Item -LiteralPath $FFMPEG_PREBUILT_DIR -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $FFMPEG_PREBUILT_DIR | Out-Null
}

function Cleanup-FfmpegSourceTree {
    if (-not (Test-Path $FFMPEG_PREBUILT_DIR)) {
        return
    }

    Get-ChildItem -LiteralPath $FFMPEG_PREBUILT_DIR -Force | ForEach-Object {
        if ($_.Name -notin @("android-libs", "include")) {
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    }
}

if ([string]::IsNullOrWhiteSpace($SDK_PATH)) {
    $localProperties = Join-Path $REPO_ROOT "local.properties"
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match "^sdk\.dir=" } | Select-Object -First 1
        if ($sdkLine) {
            $SDK_PATH = ($sdkLine -replace "^sdk\.dir=", "").Replace("\:", ":").Replace("\\", "\")
        }
    }
}

if ([string]::IsNullOrWhiteSpace($SDK_PATH) -or -not (Test-Path $SDK_PATH)) {
    Write-Host "ERROR: Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties."
    exit 1
}

$NDK_PATH = Join-Path $SDK_PATH "ndk\29.0.14206865"

# Check NDK
if (-not (Test-Path $NDK_PATH)) {
    # Try common NDK locations
    $ndkVersions = Get-ChildItem (Join-Path $SDK_PATH "ndk") -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
    if ($ndkVersions.Count -gt 0) {
        $NDK_PATH = $ndkVersions[0].FullName
        Write-Host "Using NDK: $NDK_PATH"
    } else {
        Write-Host "ERROR: Android NDK not found. Install it via Android Studio > SDK Manager > SDK Tools > NDK"
        exit 1
    }
}

if (-not (Test-Path (Join-Path $NDK_PATH "toolchains\llvm\prebuilt\linux-x86_64"))) {
    Write-Host "Windows NDK found, but WSL needs the Linux NDK toolchain."
    if (-not (Test-Path (Join-Path $LINUX_NDK_PATH "toolchains\llvm\prebuilt\linux-x86_64"))) {
        $zipPath = Join-Path $LINUX_NDK_PARENT "android-ndk-$LINUX_NDK_VERSION-linux.zip"
        New-Item -ItemType Directory -Force -Path $LINUX_NDK_PARENT | Out-Null
        if (-not (Test-Path $zipPath)) {
            Write-Host "Downloading Linux NDK $LINUX_NDK_VERSION..."
            curl.exe -L $LINUX_NDK_URL -o $zipPath
            if ($LASTEXITCODE -ne 0) {
                Write-Host "ERROR: Failed to download Linux NDK."
                exit 1
            }
        }
        Write-Host "Extracting Linux NDK..."
        Expand-Archive -Path $zipPath -DestinationPath $LINUX_NDK_PARENT -Force
    }
    $NDK_PATH = $LINUX_NDK_PATH
    Repair-LinuxNdkSymlinks $NDK_PATH
    Write-Host "Using Linux NDK for WSL: $NDK_PATH"
}

$ENABLED_DECODERS = @("alac", "aac", "ape", "mp3", "vorbis", "opus", "flac", "ac3", "eac3", "truehd", "dca", "amrnb", "amrwb", "pcm_mulaw", "pcm_alaw")

Write-Host "=== Building FFmpeg for Android ==="
Write-Host "Module path: $FFMPEG_MODULE_PATH"
Write-Host "NDK path: $NDK_PATH"
Write-Host "Decoders: $($ENABLED_DECODERS -join ', ')"

# Check if FFmpeg source exists
$ffmpegDir = $FFMPEG_PREBUILT_DIR
if (-not (Test-Path (Join-Path $ffmpegDir "configure"))) {
    Reset-FfmpegPrebuiltDirectory
    Write-Host ""
    Write-Host "FFmpeg source not found. Cloning..."
    git -c core.autocrlf=false clone https://git.ffmpeg.org/ffmpeg.git --branch=release/6.0 --depth=1 $ffmpegDir
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to clone FFmpeg."
        exit 1
    }
}

# Convert Windows paths to WSL paths
$wslModulePath = ConvertTo-WslPath $FFMPEG_MODULE_PATH
$wslNdkPath = ConvertTo-WslPath $NDK_PATH

Write-Host ""
Write-Host "Building FFmpeg via WSL..."
Write-Host "WSL module path: $wslModulePath"
Write-Host "WSL NDK path: $wslNdkPath"

$decoderList = $ENABLED_DECODERS -join " "
$wslToolPath = "$wslNdkPath/prebuilt/linux-x86_64/bin:$wslNdkPath/toolchains/llvm/prebuilt/linux-x86_64/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

wsl bash -c "export PATH='$wslToolPath' && cd '${wslModulePath}/jni' && sed -i 's/\r$//' build_ffmpeg.sh && chmod +x build_ffmpeg.sh && ./build_ffmpeg.sh '${wslModulePath}' '${wslNdkPath}' 'linux-x86_64' 21 $decoderList"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=== FFmpeg build completed successfully ==="
    Write-Host "Static libraries are in: $FFMPEG_MODULE_PATH\jni\ffmpeg\android-libs\"
    Write-Host "Building libffmpegJNI.so and refreshing the packaged arm64-v8a prebuilt..."

    Push-Location $REPO_ROOT
    try {
        .\gradlew.bat :ffmpeg-decoder:assembleRelease -PellaBuildNative=true -PellaAbi=arm64-v8a
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: Failed to build libffmpegJNI.so."
            exit 1
        }

        $builtSo = Join-Path $REPO_ROOT "ffmpeg-decoder\build\intermediates\stripped_native_libs\release\stripReleaseDebugSymbols\out\lib\arm64-v8a\libffmpegJNI.so"
        $outputDir = Join-Path $REPO_ROOT "ffmpeg-decoder\src\main\jniLibs\arm64-v8a"
        if (-not (Test-Path $builtSo)) {
            Write-Host "ERROR: Built library not found: $builtSo"
            exit 1
        }
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
        Copy-Item -LiteralPath $builtSo -Destination (Join-Path $outputDir "libffmpegJNI.so") -Force
        Write-Host "Copied prebuilt library to $outputDir"
        Cleanup-FfmpegSourceTree
        Write-Host "Removed full FFmpeg source tree and kept only include/android-libs prebuilt inputs."
    } finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "Now rebuild the app normally: .\gradlew.bat :app:assembleRelease"
} else {
    Write-Host ""
    Write-Host "=== FFmpeg build FAILED ==="
    exit 1
}
