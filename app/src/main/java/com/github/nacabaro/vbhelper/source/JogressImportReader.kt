package com.github.nacabaro.vbhelper.source

import com.github.cfogrady.vb.dim.card.BemCard
import com.github.cfogrady.vb.dim.card.Card
import com.github.cfogrady.vb.dim.fusion.BemSpecificFusions
import com.github.cfogrady.vb.dim.fusion.SpecificFusions
import com.github.cfogrady.vbnfc.data.NfcCharacter

data class ImportedAttributeJogress(val fromIndex: Int, val toIndex: Int, val attribute: NfcCharacter.Attribute)
data class ImportedSpecificJogress(val fromIndex: Int, val toIndex: Int, val partnerCardNumber: Int, val partnerCharaIndex: Int)
data class ImportedJogress(val attributes: List<ImportedAttributeJogress>, val specific: List<ImportedSpecificJogress>)

/** Read both tables for DiM and BEM; never treat foreign-card slots or sentinels as local IDs. */
object JogressImportReader {
    fun read(card: Card<*, *, *, *, *, *>): ImportedJogress {
        val indices = 0 until (card.characterStats?.characterEntries?.size ?: 0)
        val number = card.header.dimId
        val attributes = card.attributeFusions?.entries.orEmpty().flatMap { entry ->
            listOf(
                entry.attribute1Fusion to NfcCharacter.Attribute.Virus,
                entry.attribute2Fusion to NfcCharacter.Attribute.Data,
                entry.attribute3Fusion to NfcCharacter.Attribute.Vaccine,
                entry.attribute4Fusion to NfcCharacter.Attribute.Free,
            ).filter { entry.characterIndex in indices && it.first in indices }
                .map { ImportedAttributeJogress(entry.characterIndex, it.first, it.second) }
        }.distinct()
        val specific = card.specificFusions?.entries.orEmpty()
            .filterIsInstance<SpecificFusions.SpecificFusionEntry>().filter { entry ->
                val local = card !is BemCard || entry is BemSpecificFusions.BemSpecificFusionEntry &&
                    entry.fromBemId == number && entry.toBemId == number
                local && entry.fromCharacterIndex in indices && entry.toCharacterIndex in indices &&
                    entry.backupDimId in 0..65534 && entry.backupCharacterIndex in 0..65534 &&
                    (entry.backupDimId != number || entry.backupCharacterIndex in indices)
            }.map { ImportedSpecificJogress(it.fromCharacterIndex, it.toCharacterIndex, it.backupDimId, it.backupCharacterIndex) }
            .distinct()
        return ImportedJogress(attributes, specific)
    }
}
