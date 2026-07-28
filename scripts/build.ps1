param(
    [string]$Task = ':app:assembleDebug'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localRoot = Join-Path $projectRoot '.local'
$env:JAVA_HOME = Join-Path $localRoot 'jdk'
$env:ANDROID_SDK_ROOT = Join-Path $localRoot 'android-sdk'
$env:GRADLE_USER_HOME = Join-Path $localRoot 'gradle'

if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw "Missing project-local JDK at $env:JAVA_HOME. Run scripts\bootstrap-tools.ps1."
}
if (-not (Test-Path (Join-Path $env:ANDROID_SDK_ROOT 'platforms\android-35'))) {
    throw "Missing project-local Android SDK at $env:ANDROID_SDK_ROOT. Run scripts\bootstrap-tools.ps1."
}

Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') $Task --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($Task -eq ':app:assembleDebug') {
        New-Item -ItemType Directory -Force (Join-Path $projectRoot 'artifacts') | Out-Null
        $version = (Get-Content (Join-Path $projectRoot 'VERSION') -Raw).Trim()
        Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' (Join-Path $projectRoot "artifacts\zhique-v$version.apk") -Force
    }
} finally {
    Pop-Location
}
