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
    val priority: Int,
    val authority: LoreAuthority = LoreAuthority.PERSONAL_NOTE,
    val reference: ResearchReference? = null
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
                matches += LorebookMatch(entry.title, entry.content, entry.priority,
                    entry.reference?.authority ?: LoreAuthority.APP_OPERATION, entry.reference)
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
                        priority = SPECIES_PRIORITY,
                        authority = LoreAuthority.SPECIES_REFERENCE,
                        reference = ResearchSources.catalog
                    )
                }
            }
        return applyBudget(matches.distinctBy { it.authority to it.title.lowercase(Locale.ROOT) }
            .sortedWith(compareBy<LorebookMatch> { it.authority.order }.thenByDescending { it.priority }), languageTag)
    }

    fun formatContextBlock(matches: List<LorebookMatch>, languageTag: String): String? {
        if (matches.isEmpty()) return null
        val header = when {
            languageTag.startsWith("pt", ignoreCase = true) ->
                "Referências desta conversa: pesquisa e notas não são lembranças pessoais. Preserve a autoria, o contexto regional e as incertezas; use apenas o que for relevante:"
            languageTag.startsWith("ja", ignoreCase = true) ->
                "会話の参考資料。研究と個人ノートは個人の記憶ではありません。出典、地域、不確実性を保ち、関連する内容だけを使います："
            else ->
                "Conversation references: research and notes are not personal memories. Preserve authorship, regional scope, and uncertainty; use only what is relevant:"
        }
        return "$header\n${matches.joinToString("\n") { serialized(it, languageTag) }}"
    }

    fun getCustomEntries(): Flow<List<LorebookEntry>> = lorebookEntryDao.getAll()

    suspend fun addCustomEntry(title: String, keys: List<String>, content: String, priority: Int = 0) {
        lorebookEntryDao.insert(
            LorebookEntry(title = title, triggerKeys = keys, content = content, priority = priority)
        )
    }

    suspend fun updateEntry(entry: LorebookEntry) = lorebookEntryDao.update(entry)

    suspend fun deleteEntry(entry: LorebookEntry) = lorebookEntryDao.delete(entry)

    private fun applyBudget(matches: List<LorebookMatch>, languageTag: String): List<LorebookMatch> {
        val result = mutableListOf<LorebookMatch>()
        var totalChars = 0
        for (match in matches) {
            if (result.size >= MAX_ENTRIES) break
            var line = serialized(match, languageTag)
            var cost = line.length
            if (totalChars + cost > MAX_TOTAL_CHARS) {
                // Bound every entry, including the first: keep the serialized
                // source scope and leading qualifications, truncate the tail.
                val available = MAX_TOTAL_CHARS - totalChars
                if (available < 120) continue
                val overflow = cost - available
                val truncated = match.content.dropLast(overflow + 1).trimEnd() + "…"
                if (truncated.length < 40) continue
                val bounded = match.copy(content = truncated)
                line = serialized(bounded, languageTag)
                cost = line.length
                if (totalChars + cost > MAX_TOTAL_CHARS) continue
                result += bounded
                totalChars += cost
            } else {
                result += match
                totalChars += cost
            }
        }
        return result
    }

    private fun serialized(match: LorebookMatch, languageTag: String): String {
        val kind = when (match.authority) {
            LoreAuthority.GENERAL_RESEARCH -> label(languageTag, "Pesquisa publicada", "公表研究", "Published research")
            LoreAuthority.SPECIES_REFERENCE -> label(languageTag, "Referência de espécie", "種族資料", "Species reference")
            LoreAuthority.SETTING_OBSERVATION -> label(languageTag, "Observação de contexto específico", "特定の文脈の観測", "Scoped observation")
            LoreAuthority.APP_OPERATION -> label(languageTag, "Registro operacional", "運用記録", "Operational record")
            LoreAuthority.PERSONAL_NOTE -> label(languageTag, "Nota pessoal", "個人ノート", "Personal note")
            LoreAuthority.PUBLICATION_HISTORY -> label(languageTag, "Registro de dispositivo e publicação", "端末と公表の記録", "Device/publication record")
        }
        val source = match.reference?.let { " · ${it.title} · ${it.scope}" }.orEmpty()
        return "- [$kind$source] ${match.title}: ${match.content}"
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
        species.profile?.let(com.github.nacabaro.vbhelper.species.ResearchText::description)?.takeIf(String::isNotBlank)?.let { parts += it }
        val cleanMoves = com.github.nacabaro.vbhelper.species.ResearchText.moves(species.specialMoves)
        if (cleanMoves.isNotEmpty()) parts += "$moves: ${cleanMoves.joinToString()}"
        return if (parts.isEmpty()) species.name else "${species.name} — ${parts.joinToString(" | ")}"
    }

    private fun label(languageTag: String, pt: String, ja: String, en: String) = when {
        languageTag.startsWith("pt", ignoreCase = true) -> pt
        languageTag.startsWith("ja", ignoreCase = true) -> ja
        else -> en
    }
}
