$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localRoot = Join-Path $projectRoot '.local'
$tmp = Join-Path $localRoot 'tmp'
New-Item -ItemType Directory -Force $tmp | Out-Null

function Download-And-Extract($uri, $archive, $destination) {
    if (-not (Test-Path $destination)) {
        $archivePath = Join-Path $tmp $archive
        Invoke-WebRequest -Uri $uri -OutFile $archivePath
        Expand-Archive -Path $archivePath -DestinationPath $destination -Force
    }
}

$jdkDestination = Join-Path $localRoot 'jdk'
if (-not (Test-Path (Join-Path $jdkDestination 'bin\java.exe'))) {
    $extract = Join-Path $tmp 'temurin-17'
    Download-And-Extract 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'temurin-17.zip' $extract
    $jdk = Get-ChildItem $extract -Directory | Select-Object -First 1
    Move-Item $jdk.FullName $jdkDestination -Force
}

$gradleDestination = Join-Path $localRoot 'gradle-dist\gradle-8.10.2'
if (-not (Test-Path (Join-Path $gradleDestination 'bin\gradle.bat'))) {
    $extract = Join-Path $tmp 'gradle-8.10.2'
    Download-And-Extract 'https://services.gradle.org/distributions/gradle-8.10.2-bin.zip' 'gradle-8.10.2-bin.zip' $extract
    $gradle = Get-ChildItem $extract -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force (Split-Path $gradleDestination) | Out-Null
    Move-Item $gradle.FullName $gradleDestination -Force
}

$sdkRoot = Join-Path $localRoot 'android-sdk'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkManager)) {
    $extract = Join-Path $tmp 'android-commandline-tools'
    Download-And-Extract 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' 'android-commandline-tools.zip' $extract
    $nested = Get-ChildItem $extract -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force (Join-Path $sdkRoot 'cmdline-tools\latest') | Out-Null
    Get-ChildItem $nested.FullName | Move-Item -Destination (Join-Path $sdkRoot 'cmdline-tools\latest') -Force
    $sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
}

$env:JAVA_HOME = $jdkDestination
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:GRADLE_USER_HOME = Join-Path $localRoot 'gradle'
1..100 | ForEach-Object { 'y' } | & $sdkManager --sdk_root=$sdkRoot --licenses | Out-Null
& $sdkManager --sdk_root=$sdkRoot 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
