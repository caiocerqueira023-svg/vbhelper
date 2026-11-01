import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins { alias(libs.plugins.kotlin.jvm); application }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }
application { mainClass = "com.tamerslab.pccompanion.CompanionMainKt" }
dependencies { implementation(project(":game-save-core")); implementation(project(":companion-protocol")); implementation(libs.gson); testImplementation(libs.junit) }
tasks.test { systemProperty("java.io.tmpdir", temporaryDir.absolutePath) }
distributions { main { contents { from("README.md") } } }
