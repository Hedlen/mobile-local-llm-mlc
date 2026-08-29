param(
    [Parameter(Mandatory = $true)] [string]$Python,
    [string]$AndroidSdk = $env:ANDROID_SDK_ROOT,
    [string]$AndroidNdk = $env:ANDROID_NDK
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$mlcSource = Join-Path $repoRoot "vendor\mlc-llm"
$androidProject = Join-Path $repoRoot "android-mvp"
if (-not $AndroidSdk) { throw 'Set ANDROID_SDK_ROOT or pass -AndroidSdk.' }
if (-not $AndroidNdk) { $AndroidNdk = Join-Path $AndroidSdk 'ndk\27.0.11718014' }
$pythonPath = (Resolve-Path -LiteralPath $Python).Path
$envRoot = Split-Path -Parent $pythonPath

$requiredPaths = @(
    $pythonPath,
    (Join-Path $AndroidNdk "build\cmake\android.toolchain.cmake"),
    $mlcSource,
    (Join-Path $androidProject "mlc-package-config.json")
)

foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Missing required path: $path"
    }
}

$env:PATH = "$envRoot;$envRoot\Scripts;$envRoot\Library\bin;$env:PATH"
$env:ANDROID_SDK_ROOT = $AndroidSdk
$env:ANDROID_NDK = $AndroidNdk
$env:TVM_NDK_CC = Join-Path $AndroidNdk "toolchains\llvm\prebuilt\windows-x86_64\bin\aarch64-linux-android24-clang"
$env:TVM_SOURCE_DIR = Join-Path $mlcSource "3rdparty\tvm"
$env:MLC_LLM_SOURCE_DIR = $mlcSource
$env:MLC_LLM_HOME = Join-Path $repoRoot ".tooling\mlc-cache"

Push-Location $androidProject
try {
    & $pythonPath -m mlc_llm package
    if ($LASTEXITCODE -ne 0) {
        throw "mlc_llm package failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
