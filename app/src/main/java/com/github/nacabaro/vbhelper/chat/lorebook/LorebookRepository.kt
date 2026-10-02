package com.github.nacabaro.vbhelper.chat.lorebook

import com.github.nacabaro.vbhelper.daos.LorebookEntryDao
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry
import com.github.nacabaro.vbhelper.species.SpeciesEntryDto
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class LorebookMatch(
    val title: String,
    val content: String,
    val priority: Int
)

class LorebookRepository(
    private val lorebookEntryDao: LorebookEntryDao,
    private val speciesRepository: SpeciesRepository
) {
    companion object {
        private const val MAX_ENTRIES = 6
        private const val MAX_TOTAL_CHARS = 1600
        private const val SPECIES_PRIORITY = 5
    }

    suspend fun scanForMatches(
        scanText: String,
        languageTag: String,
        excludeSpeciesName: String? = null
    ): List<LorebookMatch> {
        if (scanText.isBlank()) return emptyList()
        val matches = mutableListOf<LorebookMatch>()

        DigimonWorldLore.entries(languageTag).forEach { entry ->
            if (matchesKeys(scanText, entry.keys)) {
                matches += LorebookMatch(entry.title, entry.content, entry.priority)
            }
        }
        lorebookEntryDao.getAllEnabled().forEach { entry ->
            if (matchesKeys(scanText, entry.triggerKeys, entry.caseSensitive)) {
                matches += LorebookMatch(entry.title, entry.content, entry.priority)
            }
        }
        val speciesEntries = try {
            speciesRepository.getAllSpeciesEntries()
        } catch (_: Exception) {
            emptyList()
        }
        speciesEntries
            .forEach { species ->
                if (excludeSpeciesName != null &&
                    species.name.equals(excludeSpeciesName, ignoreCase = true)
                ) return@forEach
                if (matchesKeys(scanText, listOf(species.name))) {
                    matches += LorebookMatch(
                        title = species.name,
                        content = formatSpeciesContent(species, languageTag),
                        priority = SPECIES_PRIORITY
                    )
                }
            }
        return applyBudget(matches.distinctBy { it.title.lowercase(Locale.ROOT) }
            .sortedByDescending { it.priority })
    }

    fun formatContextBlock(matches: List<LorebookMatch>, languageTag: String): String? {
        if (matches.isEmpty()) return null
        val header = when {
            languageTag.startsWith("pt", ignoreCase = true) ->
                "Conhecimento vivo para esta conversa (deixe-o colorir a resposta quando for relevante; ele fica nos bastidores como experiência vivida):"
            languageTag.startsWith("ja", ignoreCase = true) ->
                "この会話のための生きた知識（関わる場合だけ返答に滲ませ、舞台裏の生きた背景として保つこと）:"
            else ->
                "Living knowledge for this conversation (let it color the reply when relevant; keep it offstage as lived background):"
        }
        return "$header\n${matches.joinToString("\n") { "- ${it.title}: ${it.content}" }}"
    }

    fun getCustomEntries(): Flow<List<LorebookEntry>> = lorebookEntryDao.getAll()

    suspend fun addCustomEntry(title: String, keys: List<String>, content: String, priority: Int = 0) {
        lorebookEntryDao.insert(
            LorebookEntry(title = title, triggerKeys = keys, content = content, priority = priority)
        )
    }

    suspend fun updateEntry(entry: LorebookEntry) = lorebookEntryDao.update(entry)

    suspend fun deleteEntry(entry: LorebookEntry) = lorebookEntryDao.delete(entry)

    private fun applyBudget(matches: List<LorebookMatch>): List<LorebookMatch> {
        val result = mutableListOf<LorebookMatch>()
        var totalChars = 0
        for (match in matches) {
            if (result.size >= MAX_ENTRIES) break
            if (totalChars + match.content.length > MAX_TOTAL_CHARS && result.isNotEmpty()) break
            result += match
            totalChars += match.content.length
        }
        return result
    }

    private fun matchesKeys(text: String, keys: List<String>, caseSensitive: Boolean = false): Boolean =
        keys.any { key ->
            if (key.isBlank()) return@any false
            val asciiWord = key.all {
                (it.isLetterOrDigit() && it.code < 128) || it == '-' || it == ' '
            }
            if (asciiWord) {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex("\\b${Regex.escape(key)}\\b", options).containsMatchIn(text)
            } else {
                text.contains(key, ignoreCase = !caseSensitive)
            }
        }

    private fun formatSpeciesContent(species: SpeciesEntryDto, languageTag: String): String {
        val level = label(languageTag, "Nível", "レベル", "Level")
        val type = label(languageTag, "Tipo", "タイプ", "Type")
        val moves = label(languageTag, "Golpes especiais", "必殺技", "Special moves")
        val parts = mutableListOf<String>()
        species.level?.takeIf { it.isNotBlank() }?.let { parts += "$level: $it" }
        species.type?.takeIf { it.isNotBlank() }?.let { parts += "$type: $it" }
        species.profile?.takeIf { it.isNotBlank() }?.let { parts += it }
        if (species.specialMoves.isNotEmpty()) parts += "$moves: ${species.specialMoves.joinToString()}"
        return if (parts.isEmpty()) species.name else "${species.name} — ${parts.joinToString(" | ")}"
    }

    private fun label(languageTag: String, pt: String, ja: String, en: String) = when {
        languageTag.startsWith("pt", ignoreCase = true) -> pt
        languageTag.startsWith("ja", ignoreCase = true) -> ja
        else -> en
    }
}
