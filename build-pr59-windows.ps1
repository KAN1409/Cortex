$ErrorActionPreference = 'Stop'

# One-shot validation build for PR #59. Never uninstalls Cortex or clears app data.
$Branch = 'robot-user-journey-fixes-20260825'
$Repo = $PSScriptRoot
Set-Location $Repo

Write-Host "==> Syncing $Branch"
git fetch origin $Branch
if ($LASTEXITCODE -ne 0) { throw 'git fetch failed' }

$dirty = git status --porcelain --untracked-files=no
if ($dirty) {
    $stash = 'cortex-prebuild-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    git stash push -m $stash | Out-Null
    Write-Host "Saved tracked local edits as git stash: $stash"
}

git checkout $Branch
if ($LASTEXITCODE -ne 0) { throw 'git checkout failed' }
git pull --ff-only origin $Branch
if ($LASTEXITCODE -ne 0) { throw 'git pull failed' }

$CodexRoot = Split-Path $Repo -Parent
if (-not $env:JAVA_HOME) {
    $candidate = Join-Path $CodexRoot 'tools\jdk17'
    if (Test-Path $candidate) { $env:JAVA_HOME = $candidate }
}
if (-not $env:ANDROID_HOME) {
    $candidate = Join-Path $CodexRoot 'tools\android-sdk'
    if (Test-Path $candidate) { $env:ANDROID_HOME = $candidate }
}
if ($env:ANDROID_HOME) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }

$Gradle = $null
$localGradle = Join-Path $CodexRoot 'tools\gradle-8.9\bin\gradle.bat'
if (Test-Path $localGradle) { $Gradle = $localGradle }
elseif (Get-Command gradle -ErrorAction SilentlyContinue) { $Gradle = (Get-Command gradle).Source }
else { throw 'Gradle 8.9 was not found. Set it in PATH or place it under ..\tools\gradle-8.9.' }

if (-not $env:JAVA_HOME) { throw 'JAVA_HOME is not set and ..\tools\jdk17 was not found.' }
if (-not $env:ANDROID_HOME) { throw 'ANDROID_HOME is not set and ..\tools\android-sdk was not found.' }

Write-Host "==> JAVA_HOME: $env:JAVA_HOME"
Write-Host "==> ANDROID_HOME: $env:ANDROID_HOME"
Write-Host "==> Gradle: $Gradle"

Write-Host '==> Building clean debug APK'
& $Gradle ':app:clean' ':app:assembleDebug' '--stacktrace' '--console=plain'
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

$Apk = Join-Path $Repo 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }

$Out = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads\Cortex-v50-pr59-debug.apk'
Copy-Item $Apk $Out -Force
$hash = (Get-FileHash $Out -Algorithm SHA256).Hash
Write-Host "==> APK: $Out"
Write-Host "==> SHA256: $hash"

$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if ((Test-Path $adb) -and ((& $adb devices) -match '\tdevice')) {
    Write-Host '==> Installing over existing Cortex with adb install -r (no uninstall / no data clear)'
    & $adb install -r $Out
    if ($LASTEXITCODE -ne 0) { throw 'adb install -r failed; existing app data was not removed.' }
} else {
    Write-Host 'No connected ADB device detected. APK is ready in Downloads.'
}
