import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
application { mainClass = "com.github.nacabaro.vbhelper.gamesave.SaveInspectorKt" }
dependencies { testImplementation(libs.junit) }
