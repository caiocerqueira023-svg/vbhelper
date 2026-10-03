package com.github.nacabaro.vbhelper.source

import com.github.cfogrady.vb.dim.card.BemCard
import com.github.cfogrady.vb.dim.card.DimCard
import com.github.cfogrady.vb.dim.character.BemCharacterStats
import com.github.cfogrady.vb.dim.character.DimStats
import com.github.cfogrady.vb.dim.fusion.AttributeFusions
import com.github.cfogrady.vb.dim.fusion.BemSpecificFusions
import com.github.cfogrady.vb.dim.fusion.DimSpecificFusions
import com.github.cfogrady.vb.dim.fusion.DimFusions
import com.github.cfogrady.vb.dim.fusion.SpecificFusions
import com.github.cfogrady.vb.dim.header.BemHeader
import com.github.cfogrady.vb.dim.header.DimHeader
import com.github.cfogrady.vbnfc.data.NfcCharacter
import org.junit.Assert.*
import org.junit.Test

class JogressImportReaderTest {
    private fun attribute(from: Int = 0, to: Int = 2) = AttributeFusions.AttributeFusionEntry.builder()
        .characterIndex(from).attribute1Fusion(to).attribute2Fusion(65535)
        .attribute3Fusion(65535).attribute4Fusion(65535).build()

    @Test fun dimIncludesSpecificPartnersAndAttributeRoutesWithoutSentinelsOrDuplicates() {
        val pair = SpecificFusions.SpecificFusionEntry.builder().fromCharacterIndex(0).toCharacterIndex(2)
            .backupDimId(42).backupCharacterIndex(1).build()
        val card = DimCard.builder().header(DimHeader.builder().dimId(42).build())
            .characterStats(DimStats.builder().characterEntries((0..2).map { DimStats.DimStatBlock.builder().stage(5).build() }).build())
            .attributeFusions(DimFusions.builder().entries(listOf(attribute(), attribute(), attribute(65535), attribute(to = 999))).build())
            .specificFusions(DimSpecificFusions.builder().entries(listOf(pair, pair)).build()).build()
        val result = JogressImportReader.read(card)
        assertEquals(listOf(ImportedAttributeJogress(0, 2, NfcCharacter.Attribute.Virus)), result.attributes)
        assertEquals(listOf(ImportedSpecificJogress(0, 2, 42, 1)), result.specific)
    }

    @Test fun bemImportsAttributeAndSpecificJogressButDoesNotMapAnotherCardsIndicesLocally() {
        fun pair(fromCard: Int = 42, toCard: Int = 42) = BemSpecificFusions.BemSpecificFusionEntry.builder()
            .fromBemId(fromCard).toBemId(toCard).fromCharacterIndex(0).toCharacterIndex(2)
            .backupDimId(99).backupCharacterIndex(15).build()
        val card = BemCard.builder().header(BemHeader.builder().dimId(42).build())
            .characterStats(BemCharacterStats.builder().characterEntries((0..2).map {
                BemCharacterStats.BemCharacterStatEntry.builder().stage(5).build()
            }).build()).attributeFusions(AttributeFusions.builder().entries(listOf(attribute())).build())
            .specificFusions(BemSpecificFusions.builder().entries(listOf(pair(), pair(99), pair(toCard = 99))).build()).build()
        val result = JogressImportReader.read(card)
        assertEquals(1, result.attributes.size)
        assertEquals(listOf(ImportedSpecificJogress(0, 2, 99, 15)), result.specific)
    }

    @Test fun absentTablesAndInvalidSpecificSlotsAreHandledAsNoRoutes() {
        val empty = DimCard.builder().header(DimHeader.builder().dimId(42).build())
            .characterStats(DimStats.builder().characterEntries(emptyList()).build()).build()
        val result = JogressImportReader.read(empty)
        assertTrue(result.attributes.isEmpty())
        assertTrue(result.specific.isEmpty())
    }
}
