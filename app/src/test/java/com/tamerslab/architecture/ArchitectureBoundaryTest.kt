package com.tamerslab.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {
    @Test
    fun domainUseCasesAndRepositoryInterfacesStayFrameworkFree() {
        val domain = File("src/main/java/com/tamerslab/domain")
        val files = sequenceOf(File(domain, "usecase"), File(domain, "repository"))
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("Expected domain use case/repository files", files.isNotEmpty())

        val forbidden = listOf(
            "import android.",
            "import androidx.room",
            "import com.tamerslab.data.database",
            "import com.tamerslab.di",
            "import com.tamerslab.battle.ui",
            "import com.tamerslab.farm.ui",
            "import com.tamerslab.transfer.ui",
        )
        files.forEach { file ->
            val source = file.readText()
            forbidden.forEach { prefix ->
                assertTrue(
                    "${file.path} must not contain $prefix",
                    !source.contains(prefix),
                )
            }
        }
    }

    @Test
    fun repositoryInterfacesDoNotImportRoomAnnotations() {
        val repositories = File("src/main/java/com/tamerslab/domain/repository")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("Expected repository interface files", repositories.isNotEmpty())

        // The current interfaces still return entity-backed model types in a few
        // places (tracked as architecture debt); what must never leak is Room
        // itself, because that would make the interface un-fakeable on the JVM.
        val forbidden = listOf("androidx.room", "RoomDatabase", "@Entity", "@Dao", "@TypeConverter")
        repositories.forEach { file ->
            val source = file.readText()
            forbidden.forEach { token ->
                assertTrue(
                    "${file.path} must not import Room ($token)",
                    !source.contains(token),
                )
            }
        }
    }

    @Test
    fun libraryModulesDoNotDependOnAppShell() {
        val modules = listOf(
            "../game-save-core/src/main",
            "../hardware/src/main",
            "../hardware-dim/src/main",
            "../companion-protocol/src/main",
            "../pc-save-companion/src/main",
        )
        val forbidden = listOf(
            "import com.tamerslab.battle.ui",
            "import com.tamerslab.di",
            "import com.tamerslab.app",
        )
        val files = modules
            .map(::File)
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }

        files.forEach { file ->
            val source = file.readText()
            forbidden.forEach { prefix ->
                assertTrue(
                    "${file.path} must not depend on the app shell ($prefix)",
                    !source.contains(prefix),
                )
            }
        }
    }

    @Test
    fun libraryModulesOnlyImportSharedLibraryNamespaces() {
        // Every `com.tamerslab.*` import inside a library module must belong to a
        // library module. The app namespace is the only other consumer of these
        // modules and must never become a dependency of one.
        val libraryNamespaces = listOf(
            "com.tamerslab.gamesave",
            "com.tamerslab.hardware",
            "com.tamerslab.companionprotocol",
            "com.tamerslab.pccompanion",
        )
        val modules = listOf(
            "../game-save-core/src/main",
            "../hardware/src/main",
            "../hardware-dim/src/main",
            "../companion-protocol/src/main",
            "../pc-save-companion/src/main",
        )
        val files = modules
            .map(::File)
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }

        files.forEach { file ->
            file.readLines()
                .filter { it.trimStart().startsWith("import com.tamerslab.") }
                .forEach { importLine ->
                    assertTrue(
                        "${file.path} imports ${importLine.trim()} outside a library namespace",
                        libraryNamespaces.any { importLine.contains(it) },
                    )
                }
        }
    }
}
