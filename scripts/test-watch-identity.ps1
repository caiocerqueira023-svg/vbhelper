# Runs watch identity regression tests independently of unrelated broken Android tests.
# Uses the compiler and JUnit already cached by Gradle; downloads nothing.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$gradleCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
function Find-CachedJar([string]$module) {
    $jar = Get-ChildItem (Join-Path $gradleCache $module) -Recurse -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "Missing cached dependency: $module. Run the Gradle build first." }
    return $jar.FullName
}
$stdlib = Find-CachedJar 'org.jetbrains.kotlin/kotlin-stdlib/2.4.0'
$junit = Find-CachedJar 'junit/junit'
$hamcrest = Find-CachedJar 'org.hamcrest/hamcrest-core'
$compilerClasspath = @(
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-compiler-embeddable')
    $stdlib
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-script-runtime')
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-reflect')
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-daemon-embeddable')
    (Find-CachedJar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm')
    (Find-CachedJar 'org.jetbrains/annotations')
) -join [IO.Path]::PathSeparator
$room = Find-CachedJar 'androidx.room/room-common-jvm'
$gson = Find-CachedJar 'com.google.code.gson/gson'
$nfcJar = Join-Path $projectRoot 'vb-nfc-reader/build/intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar'
if (-not (Test-Path $nfcJar)) { throw 'Run .\gradlew.bat :vb-nfc-reader:bundleLibCompileToJarDebug first.' }
$testClasspath = @($stdlib, $junit, $hamcrest, $room, $gson, $nfcJar) -join [IO.Path]::PathSeparator
$outputDir = Join-Path $projectRoot 'app/build/watch-identity-tests'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$androidJar = (Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Android/Sdk/platforms') -Recurse -Filter android.jar |
    Sort-Object FullName -Descending | Select-Object -First 1).FullName
if (-not $androidJar) { throw 'Android SDK android.jar not found.' }
$testClasspath = @($outputDir, $testClasspath, $androidJar) -join [IO.Path]::PathSeparator
& javac -d $outputDir (Join-Path $projectRoot 'scripts/test-support/android/util/Log.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$sources = @(Get-ChildItem (Join-Path $projectRoot 'app/src/main/java/com/github/nacabaro/vbhelper/domain/identity') -Filter '*.kt').FullName
$sources += Join-Path $projectRoot 'app/src/main/java/com/github/nacabaro/vbhelper/domain/device_data/NfcEvolutionHistory.kt'
$sources += Join-Path $projectRoot 'app/src/main/java/com/github/nacabaro/vbhelper/domain/device_data/EvolutionHistoryRepair.kt'
$tests = @(Get-ChildItem (Join-Path $projectRoot 'app/src/test/java/com/github/nacabaro/vbhelper/domain/identity') -Filter '*.kt').FullName
$tests += Join-Path $projectRoot 'app/src/test/java/com/github/nacabaro/vbhelper/domain/device_data/NfcEvolutionHistoryTest.kt'
$tests += Join-Path $projectRoot 'app/src/test/java/com/github/nacabaro/vbhelper/domain/device_data/EvolutionHistoryRepairTest.kt'
$tests += Join-Path $projectRoot 'vb-nfc-reader/src/test/java/com/github/cfogrady/vbnfc/TransferProtocolIntegrityTest.kt'
$tests += Join-Path $projectRoot 'vb-nfc-reader/src/test/java/com/github/cfogrady/vbnfc/CryptographicTransformerHelper.kt'
$tests += Join-Path $projectRoot 'vb-nfc-reader/src/test/java/com/github/cfogrady/vbnfc/vb/EvolutionStatsTransferTest.kt'
& java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
    -no-stdlib -no-reflect -jvm-target 11 -classpath $testClasspath -d $outputDir @sources @tests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp ($outputDir + [IO.Path]::PathSeparator + $testClasspath) org.junit.runner.JUnitCore `
    com.github.nacabaro.vbhelper.domain.identity.IndividualIdentityTest `
    com.github.nacabaro.vbhelper.domain.identity.WatchTransferTest `
    com.github.nacabaro.vbhelper.domain.device_data.NfcEvolutionHistoryTest `
    com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepairTest `
    com.github.cfogrady.vbnfc.TransferProtocolIntegrityTest `
    com.github.cfogrady.vbnfc.vb.EvolutionStatsTransferTest
exit $LASTEXITCODE

