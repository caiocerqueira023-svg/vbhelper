param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
if (-not $SkipBuild) {
    & ./gradlew.bat :app:assembleIntegrityCheck :app:assembleIntegrityCheckAndroidTest --offline --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$integrityAdb = Join-Path $sdkRoot 'platform-tools/adb.exe'
$aapt = (Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Recurse -Filter aapt.exe |
    Sort-Object FullName -Descending | Select-Object -First 1).FullName
$apks = @{
    'app/build/outputs/apk/integrityCheck/app-integrityCheck.apk' = 'com.github.nacabaro.vbhelper.integritycheck'
    'app/build/outputs/apk/androidTest/integrityCheck/app-integrityCheck-androidTest.apk' = 'com.github.nacabaro.vbhelper.integritycheck.test'
}
foreach ($entry in $apks.GetEnumerator()) {
    $badging = & $aapt dump badging $entry.Key
    if ($LASTEXITCODE -ne 0 -or -not ($badging | Select-String -SimpleMatch "package: name='$($entry.Value)'")) {
        throw 'Refusing to install an APK outside the separate integritycheck application.'
    }
    & $integrityAdb install -r $entry.Key
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$result = & $integrityAdb shell am instrument -w -r -e class com.github.nacabaro.vbhelper.IndividualPersistenceTest com.github.nacabaro.vbhelper.integritycheck.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$result | Set-Content 'app/build/identity-instrumentation.txt'
$result | Select-String 'INSTRUMENTATION_STATUS: stack=|Tests run:|OK \(|FAILURES|INSTRUMENTATION_FAILED'
if (-not ($result | Select-String 'OK \(\d+ tests?\)')) { exit 1 }
