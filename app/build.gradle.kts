import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vksp)
    alias(libs.plugins.vprotobuf)
    jacoco
}

// A/B switch for the Phase 5.4 battle scene/UI channel. Defaults to off; benchmark both
// builds with -PbattleSceneChannel=true and record the comparison in
// docs/performance/BASELINE.md before changing the default.
val battleSceneChannelEnabled = providers.gradleProperty("battleSceneChannel")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)

android {
    namespace = "com.tamerslab"
    compileSdk = 37

    defaultConfig {
        // Own install identity since Alpha 0.7.0 (ADR 0006): the app no longer updates or is
        // updated by VBHelper (`com.github.nacabaro.vbhelper`). Existing VBHelper data is not
        // inherited; Settings > Export/Import database moves a collection across.
        applicationId = "com.tamerslab.vbhelper"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "Alpha 0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "ONLINE_PVP_ENABLED", "true")
        // AR battles are a developer-only option while the feature stabilises: the Radar
        // preference and every AR entry point are hidden and disabled in release builds. Debug
        // and integrityCheck builds keep it available (including the -PnonDebuggable Samsung
        // workaround build above, which is still a debug *build type*).
        buildConfigField("boolean", "AR_BATTLES_ENABLED", "true")
        // Phase 5.4 rollback switch: when false the battle screen keeps the
        // original 60 Hz full-snapshot Compose state path. Enable only after the
        // device Perfetto validation described in docs/performance/BASELINE.md.
        // Override for an A/B build with -PbattleSceneChannel=true.
        buildConfigField("boolean", "BATTLE_SCENE_CHANNEL_ENABLED", battleSceneChannelEnabled.toString())
    }

    buildTypes {
        debug {
            // ARCore workaround for Samsung/One UI devices (google-ar/arcore-android-sdk#1750):
            // plane detection and tracking degrade/flicker in *debuggable* builds, while the
            // same app in a non-debuggable build tracks normally. Build the debug variant with
            // -PnonDebuggable to test AR on those devices without assembling/signing a release:
            //   .\gradlew.bat :app:installDebug -PnonDebuggable
            // Debug-only activities and logging stay available (they are source-set based), only
            // the debuggable manifest flag changes. Debugging with a debugger attached will not
            // work on such a build, which is expected.
            isDebuggable = !providers.gradleProperty("nonDebuggable").isPresent
        }
        create("integrityCheck") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".integritycheck"
            matchingFallbacks += "debug"
        }
        release {
            // AR battles are developer-only: no Radar toggle, no AR entry point in release.
            buildConfigField("boolean", "AR_BATTLES_ENABLED", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testBuildType = "integrityCheck"
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Optional ABI splits for size measurements: -PabiSplits produces per-ABI APKs.
    // Play delivery should use an App Bundle instead; local/default builds are unchanged.
    if (providers.gradleProperty("abiSplits").isPresent) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = providers.gradleProperty("abiUniversalApk").isPresent
            }
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// Opt-in Compose compiler reports for recomposition/unstable-class analysis:
// .\gradlew.bat :app:assembleRelease -PcomposeCompilerMetrics
if (providers.gradleProperty("composeCompilerMetrics").isPresent) {
    composeCompiler {
        reportsDestination.set(layout.buildDirectory.dir("reports/compose-compiler"))
        metricsDestination.set(layout.buildDirectory.dir("reports/compose-compiler"))
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

protobuf {
    protoc {
        // Version is pinned in gradle/libs.versions.toml (protoc); keep it aligned
        // with the protobuf-javalite runtime.
        artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}"
    }

    generateProtoTasks {
        all().forEach  { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

jacoco { toolVersion = libs.versions.jacoco.get() }

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val jacocoUnitTestReport = tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testIntegrityCheckUnitTest")
    group = "verification"
    description = "Generates JaCoCo coverage for the app's integrityCheck unit tests."

    val coverageExcludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*_Impl.class",
        "**/*_Impl$*.class",
        "**/*_Factory.class",
        "**/*_Factory$*.class",
        "**/*_MembersInjector.class",
        "**/*_MembersInjector$*.class",
        "**/*Hilt*.*",
        "**/dagger/**",
        "**/hilt_aggregated_deps/**",
    )

    classDirectories.setFrom(
        files(
            // AGP 9/Kotlin 2.x keeps the Kotlin class outputs under intermediates;
            // the old `tmp/kotlin-classes` path is kept as a fallback for other AGP
            // versions but is normally empty on this toolchain.
            fileTree(
                layout.buildDirectory.dir(
                    "intermediates/built_in_kotlinc/integrityCheck/compileIntegrityCheckKotlin/classes",
                ),
            ) {
                exclude(coverageExcludes)
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/integrityCheck")) {
                exclude(coverageExcludes)
            },
            fileTree(
                layout.buildDirectory.dir(
                    "intermediates/javac/integrityCheck/compileIntegrityCheckJavaWithJavac/classes",
                ),
            ) {
                exclude(coverageExcludes)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/java", "src/integrityCheck/java"))
    executionData.setFrom(layout.buildDirectory.file("jacoco/testIntegrityCheckUnitTest.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Coverage collection is opt-in: the report task is intentionally NOT wired
// into `check`, because the aggregate check is used by local/CI verification and
// a JaCoCo report over the whole app is slow. Run it explicitly when collecting
// coverage: .\gradlew.bat :app:jacocoTestReport
// (Coverage targets and the measurement record live in
// docs/performance/BASELINE.md and docs/ADR 0005.)

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(project(":hardware"))
    implementation(project(":game-save-core"))
    implementation(project(":companion-protocol"))
    implementation(libs.androidx.profileinstaller)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.location)
    implementation(libs.timber)
    implementation(libs.tinylog.api)
    implementation(libs.tinylog.impl)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    add("integrityCheckImplementation", libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)

    ksp(libs.androidx.room.compiler)
    annotationProcessor(libs.androidx.room.compiler)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    // HTTP request logging
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio.android)
    implementation(libs.filament.utils.android)

    // AR battle presentation: camera passthrough, plane detection and anchors.
    implementation(libs.arcore)
}


