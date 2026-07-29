$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ModuleRoot = Join-Path $RepoRoot "lyrico-audiotag"
$OutputDir = Join-Path $ModuleRoot "src\main\jniLibs\arm64-v8a"
$CmakeOutputRoot = Join-Path $ModuleRoot "build\intermediates\cxx\RelWithDebInfo"

Write-Host "=== Building lyrico-audiotag native library ==="
Write-Host "Only arm64-v8a is packaged by default."

Push-Location $RepoRoot
try {
    .\gradlew.bat :lyrico-audiotag:assembleRelease -PellaBuildNative=true -PellaAbi=arm64-v8a
    $BuiltSo = Get-ChildItem -LiteralPath $CmakeOutputRoot -Recurse -File -Filter "liblyrico_taglib.so" |
        Where-Object { $_.FullName -like "*\obj\arm64-v8a\liblyrico_taglib.so" } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $BuiltSo) {
        throw "Fresh CMake library not found under: $CmakeOutputRoot"
    }
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    Copy-Item -LiteralPath $BuiltSo.FullName -Destination (Join-Path $OutputDir "liblyrico_taglib.so") -Force
    Write-Host "Native source: $($BuiltSo.FullName)"
    Write-Host "Copied prebuilt library to $OutputDir"
} finally {
    Pop-Location
}
