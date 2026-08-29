param(
    [string]$AndroidSdk = $env:ANDROID_SDK_ROOT,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'

function Report-Tool([string]$Name, [string]$Path) {
    if ($Path -and (Test-Path -LiteralPath $Path)) {
        Write-Host "OK      $Name -> $Path"
        return $true
    }
    Write-Host "MISSING $Name"
    return $false
}

if (-not $JavaHome) {
    $jdk = Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-17*' |
        Select-Object -First 1
    if ($jdk) { $JavaHome = $jdk.FullName }
}

if (-not $AndroidSdk) {
    $candidate = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $candidate) { $AndroidSdk = $candidate }
}

$checks = @(
    (Report-Tool 'JDK 17' $(if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' })),
    (Report-Tool 'Android SDK' $AndroidSdk),
    (Report-Tool 'ADB' $(if ($AndroidSdk) { Join-Path $AndroidSdk 'platform-tools\adb.exe' })),
    (Report-Tool 'SDK Manager' $(if ($AndroidSdk) { Join-Path $AndroidSdk 'cmdline-tools\latest\bin\sdkmanager.bat' })),
    (Report-Tool 'Rust' (Join-Path $env:USERPROFILE '.cargo\bin\rustc.exe')),
    (Report-Tool 'MLC generated module' (Join-Path $PSScriptRoot '..\android-mvp\dist\lib\mlc4j'))
)

if ($checks -contains $false) { exit 1 }
Write-Host 'Environment is ready for an Android MVP build.'
