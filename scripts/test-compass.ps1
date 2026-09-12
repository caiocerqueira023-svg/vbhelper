# Runs the compass's pure Kotlin regression tests independently of Android test sources.
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
$stdlib = Find-CachedJar 'org.jetbrains.kotlin/kotlin-stdlib'
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
$testClasspath = @($stdlib, $junit, $hamcrest) -join [IO.Path]::PathSeparator
$outputDir = Join-Path $projectRoot 'app/build/compass-tests'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$source = Join-Path $projectRoot 'app/src/main/java/com/github/nacabaro/vbhelper/screens/worldScreen/CompassHeading.kt'
$test = Join-Path $projectRoot 'app/src/test/java/com/github/nacabaro/vbhelper/screens/worldScreen/CompassHeadingTest.kt'
& java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
    -no-stdlib -no-reflect -jvm-target 11 -classpath $testClasspath -d $outputDir $source $test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp ($outputDir + [IO.Path]::PathSeparator + $testClasspath) org.junit.runner.JUnitCore `
    com.github.nacabaro.vbhelper.screens.worldScreen.CompassHeadingTest
exit $LASTEXITCODE
