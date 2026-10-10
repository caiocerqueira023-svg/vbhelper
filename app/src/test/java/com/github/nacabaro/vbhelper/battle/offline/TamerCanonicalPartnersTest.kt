package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TamerCanonicalPartnersTest {
    private val roster get() = TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText())
    private fun art(name: String, stage: Int) = ArenaSpecies(name, stage, "fixture:$name", null,
        BattleAttribute.VACCINE, listOf("Signature"), null)
    private fun resolver(vararg art: Pair<String, Int>) = TamerTeamResolver(roster, art.map { this.art(it.first, it.second) })
    private fun lead(resolver: TamerTeamResolver, id: String, stage: Int) = resolver.resolve(id, listOf(stage))!!.members.first()

    @Test fun nextAndSaversUseTheirOwnTerminalPartners() {
        val resolver = resolver("Victory Greymon" to 5, "Shine Greymon" to 5,
            "Z'd Garurumon" to 5, "Mirage Gaogamon" to 5)
        assertEquals("Victory Greymon", lead(resolver, "tsurugi", 5).species.name)
        assertEquals("Shine Greymon", lead(resolver, "masaru", 5).species.name)
        assertEquals("Z'd Garurumon", lead(resolver, "yuu-next", 5).species.name)
        assertEquals("Mirage Gaogamon", lead(resolver, "thoma", 5).species.name)
    }

    @Test fun ownedDigiXrosPairsEquipTheirCombinedForm() {
        val resolver = resolver("Shoutmon" to 2, "Ballistamon" to 2, "Shoutmon X2" to 4,
            "Greymon (2010 Anime Version)" to 3, "Mail Birdramon" to 2, "Metal Greymon (2010 Anime Version)" to 4)
        val taiki = TamerBattleLoadouts.participants(resolver.resolve("taiki", listOf(2, 2))!!, resolver)
        assertEquals("Shoutmon", taiki[0].speciesName)
        assertEquals("Ballistamon", taiki[1].speciesName)
        assertEquals(ArenaPartnerOrigin.OWNED, resolver.resolve("taiki", listOf(2, 2))!!.members[1].origin)
        assertEquals("Shoutmon X2", taiki[0].jogressResultSpecies)
        val kiriha = TamerBattleLoadouts.participants(resolver.resolve("kiriha", listOf(3, 3))!!, resolver)
        assertEquals("Metal Greymon (2010 Anime Version)", kiriha[0].jogressResultSpecies)
        assertEquals(listOf("Mail Birdramon"), kiriha[0].jogressPartnerSpecies)
    }

    @Test fun dnaDigivolutionPartnersPreferTheFusingGuest() {
        val resolver = resolver("V-mon" to 2, "XV-mon" to 3, "Paildramon" to 4,
            "Wormmon" to 2, "Stingmon" to 3, "Hawkmon" to 2, "Aquilamon" to 3,
            "Plotmon" to 2, "Tailmon" to 3, "Armadimon" to 2, "Ankylomon" to 3,
            "Patamon" to 2, "Angemon" to 3, "Silphymon" to 4, "Shakkoumon" to 4)
        fun team(id: String): ResolvedTamerTeam = resolver.resolve(id, listOf(3, 3))
            ?: throw AssertionError("No stage-3 team for $id")
        fun fusion(id: String): String? =
            TamerBattleLoadouts.participants(team(id), resolver)[0].jogressResultSpecies
        assertEquals("ken", team("daisuke").members[1].ownerId)
        assertEquals("Paildramon", fusion("daisuke"))
        assertEquals("daisuke", team("ken").members[1].ownerId)
        assertEquals("Paildramon", fusion("ken"))
        assertEquals("hikari", team("miyako").members[1].ownerId)
        assertEquals("Silphymon", fusion("miyako"))
        assertEquals("miyako", team("hikari").members[1].ownerId)
        assertEquals("Silphymon", fusion("hikari"))
        assertEquals("takeru", team("iori").members[1].ownerId)
        assertEquals("Shakkoumon", fusion("iori"))
        assertEquals("iori", team("takeru").members[1].ownerId)
        assertEquals("Shakkoumon", fusion("takeru"))
    }

    @Test fun nonFusingStagesKeepTheEstablishedGuestOrder() {
        val resolver = resolver("V-mon" to 2, "Hawkmon" to 2, "Wormmon" to 2,
            "Patamon" to 2, "Plotmon" to 2, "Armadimon" to 2)
        val team = resolver.resolve("daisuke", listOf(2, 2))!!
        assertEquals("ken", team.members[1].ownerId)
        assertNull(TamerBattleLoadouts.participants(team, resolver)[0].jogressResultSpecies)
    }

    @Test fun unavailableDocumentedFormNeverFallsBackToAnotherTamersEvolution() {
        val resolver = resolver("Shine Greymon" to 5, "Alphamon" to 5, "Stingmon" to 3)
        assertNull(resolver.resolve("tsurugi", listOf(5)))
        assertNull(resolver.resolve("takumi-rearise", listOf(5)))
        assertNull(resolver.resolve("erika", listOf(3)))
    }

    @Test fun dorumonContinuitiesRemainDistinct() {
        val resolver = resolver("Gaioumon" to 5, "DORUgoramon" to 5, "Alphamon" to 5)
        assertEquals("Gaioumon", lead(resolver, "takumi-rearise", 5).species.name)
        assertEquals("DORUgoramon", lead(resolver, "kosuke", 5).species.name)
        assertFalse(lead(resolver, "takumi-rearise", 5).inferred)
    }

    @Test fun reariseUsesDocumentedIndividualPartners() {
        val expected = mapOf("michi" to "Lovely Angemon", "keito" to "Heavy Leomon",
            "mayu-rearise" to "Mitamamon", "nozomi" to "Noble Pumpmon")
        val resolver = resolver(*(expected.values.map { it to 5 } + listOf("Holydramon" to 5, "Saber Leomon" to 5,
            "Sleipmon" to 5)).toTypedArray())
        expected.forEach { (id, form) -> assertEquals(id, form, lead(resolver, id, 5).species.name) }
    }

    @Test fun erikaAndKenDoNotShareAWormmonHistory() {
        val resolver = resolver("Hudiemon" to 3, "Stingmon" to 3, "Paildramon" to 4, "Dinobeemon" to 4,
            "Imperialdramon Dragon Mode" to 5, "Gran Kuwagamon" to 5)
        assertEquals("Hudiemon", lead(resolver, "erika", 3).species.name)
        assertEquals("Stingmon", lead(resolver, "ken", 3).species.name)
        assertEquals("Paildramon", lead(resolver, "ken", 4).species.name)
        assertEquals("Imperialdramon Dragon Mode", lead(resolver, "ken", 5).species.name)
    }

    @Test fun nokiaStartsFusedAtMegaAndUsesAGuestWithoutCloningComponents() {
        val resolver = resolver("Omegamon" to 5, "War Greymon" to 5, "Metal Garurumon" to 5, "Diablomon" to 5)
        assertEquals("Omegamon", lead(resolver, "nokia", 5).species.name)
        val team = resolver.resolve("nokia", listOf(5, 5))!!
        assertEquals(listOf("Omegamon", "Diablomon"), team.members.map { it.species.name })
        assertEquals(ArenaPartnerOrigin.GUEST, team.members[1].origin)
        assertTrue(team.members[0].componentPartnerIds.intersect(team.members[1].componentPartnerIds).isEmpty())
    }

    @Test fun mireiStartsAsMastemonAndKeepsTheActualPerfectPairBelowMega() {
        val resolver = resolver("Mastemon" to 5, "Ofanimon" to 5, "Lilithmon" to 5,
            "Ulforce V-dramon" to 5, "Angewomon" to 4, "Lady Devimon" to 4)
        assertEquals("Mastemon", lead(resolver, "mirei", 5).species.name)
        val mega = resolver.resolve("mirei", listOf(5, 5))!!
        assertEquals(listOf("Mastemon", "Ulforce V-dramon"), mega.members.map { it.species.name })
        assertEquals(ArenaPartnerOrigin.GUEST, mega.members[1].origin)
        assertEquals(listOf("Angewomon", "Lady Devimon"), resolver.resolve("mirei", listOf(4, 4))!!.members.map { it.species.name })
        assertEquals("Mastemon", TamerBattleLoadouts.participants(resolver.resolve("mirei", listOf(4, 4))!!, resolver)[0].jogressResultSpecies)
    }

    @Test fun shomaFightsAloneAsAlterBAndPairsGaioumonWithKuzuhamon() {
        val resolver = resolver("Gaioumon" to 5, "Kuzuhamon" to 5, "Omegamon Alter-B" to 5)
        val solo = resolver.resolve("shoma", listOf(5))!!
        assertEquals("Omegamon Alter-B", solo.members.single().species.name)
        assertTrue(solo.members.single().componentPartnerIds.contains("shoma:secondary"))
        val duo = resolver.resolve("shoma", listOf(5, 5))!!
        assertEquals(listOf("Gaioumon", "Kuzuhamon"), duo.members.map { it.species.name })
        assertEquals(ArenaPartnerOrigin.OWNED, duo.members[1].origin)
        assertTrue(duo.members[0].componentPartnerIds.intersect(duo.members[1].componentPartnerIds).isEmpty())
        assertEquals("Omegamon Alter-B", TamerBattleLoadouts.participants(duo, resolver)[0].jogressResultSpecies)
    }

    @Test fun missingFusionArtworkNeverProducesAJogressMovie() {
        val resolver = resolver("Ofanimon" to 5, "Lilithmon" to 5, "War Greymon" to 5, "Metal Garurumon" to 5,
            "Angewomon" to 4, "Lady Devimon" to 4, "Agumon" to 2, "Gabumon" to 2)
        // Documented solo Ultimates still field at tier; only the fusion movie stays gated on result art.
        assertEquals("War Greymon", resolver.resolve("nokia", listOf(5))!!.members.single().species.name)
        val duo = resolver.resolve("nokia", listOf(5, 5))!!
        assertEquals(listOf("War Greymon", "Metal Garurumon"), duo.members.map { it.species.name })
        assertNull(TamerBattleLoadouts.participants(duo, resolver)[0].jogressResultSpecies)
        // Mirei fields her documented solo Ultimate at tier; only the fusion
        // movie stays gated on result art.
        assertEquals("Ofanimon", resolver.resolve("mirei", listOf(5))!!.members.single().species.name)
        // Hideto fields his documented partner itself when the combined form
        // lacks artwork; only the fusion movie stays gated.
        val hideto = resolver.resolve("hideto", listOf(5))!!.members.single()
        assertEquals("War Greymon", hideto.species.name)
        assertFalse(hideto.scaled)
    }

    @Test fun mixedStageFusionTeamsDoNotReuseAnAbsorbedPartner() {
        val resolver = resolver("Mastemon" to 5, "Lady Devimon" to 4, "Aero V-dramon" to 4,
            "Omegamon" to 5, "Gabumon" to 2, "Keramon" to 2)
        val mirei = resolver.resolve("mirei", listOf(5, 4))!!
        assertEquals("Aero V-dramon", mirei.members[1].species.name)
        assertEquals(ArenaPartnerOrigin.GUEST, mirei.members[1].origin)
        val nokia = resolver.resolve("nokia", listOf(5, 2))!!
        assertEquals("Keramon", nokia.members[1].species.name)
    }

    @Test fun hidetoUsesHisFusionAndAnotherTamersPartner() {
        val resolver = resolver("Omegamon" to 5, "Ulforce V-dramon" to 5, "Metal Garurumon" to 5)
        val team = resolver.resolve("hideto", listOf(5, 5))!!
        assertEquals(listOf("Omegamon", "Ulforce V-dramon"), team.members.map { it.species.name })
        assertEquals("taichi-vtamer", team.members[1].ownerId)
    }

    @Test fun surviveLinesDoNotBorrowGenericSpeciesMegas() {
        val expected = mapOf("aoi" to "Anubimon", "saki" to "Ceresmon Medium", "ryo-survive" to "Bancho Stingmon", "kaito" to "Beelzebumon")
        val resolver = resolver(*(expected.values.map { it to 5 } + listOf("Plutomon" to 5, "Ceresmon" to 5,
            "Gran Kuwagamon" to 5, "Grand Dracumon" to 5)).toTypedArray())
        expected.forEach { (id, form) -> assertEquals(id, form, lead(resolver, id, 5).species.name) }
    }

    @Test fun liberatorPartnersKeepTheirOwnPaths() {
        val resolver = resolver("Punkmon" to 3, "Wizarmon" to 3, "Meramon" to 3,
            "Forgebeemon" to 3, "Waspmon" to 3, "Master Tyranomon" to 4, "Metal Tyranomon" to 4,
            "Pyramidimon" to 5, "Dinomon" to 5)
        assertEquals("Punkmon", lead(resolver, "yuuki", 3).species.name)
        assertEquals("Meramon", lead(resolver, "ai-makoto", 3).species.name)
        assertEquals("Forgebeemon", lead(resolver, "saikiyo", 3).species.name)
        assertEquals("Master Tyranomon", lead(resolver, "ryutaro", 4).species.name)
        assertEquals("Pyramidimon", lead(resolver, "close", 5).species.name)
    }

    @Test fun genuineGapsScaleExistingFormsInsteadOfBorrowingSpeciesEvolutionTrees() {
        val resolver = resolver("Metal Greymon" to 4, "Greymon" to 3, "Were Garurumon" to 4,
            "Boutmon" to 4, "Bulkmon" to 3, "Pusumon" to 0, "Pyonmon" to 0, "Agumon" to 2, "V-mon" to 2)
        assertEquals("Greymon", lead(resolver, "nokia", 4).species.name)
        assertTrue(lead(resolver, "nokia", 4).scaled)
        assertEquals("Bulkmon", lead(resolver, "ritsu", 4).species.name)
        assertTrue(lead(resolver, "ritsu", 4).scaled)
        assertEquals("Pyonmon", lead(resolver, "ruli", 0).species.name)
        assertFalse(lead(resolver, "ruli", 0).inferred)
        assertEquals("Agumon", lead(resolver, "taichi-vtamer", 2).species.name)
        assertFalse(lead(resolver, "taichi-vtamer", 2).inferred)
    }

    @Test fun scaledPartnersUseRequestedStatAndTechniqueTierButKeepTheirActualFormSize() {
        val resolver = resolver("Angewomon" to 4, "Lady Devimon" to 4)
        val team = resolver.resolve("mirei", listOf(0, 2))!!
        val participants = TamerBattleLoadouts.participants(team, resolver)
        assertEquals(listOf(0, 2), participants.map { it.stage })
        assertEquals(listOf(4, 4), participants.map { it.visualStage })
        assertEquals(listOf("Angewomon", "Lady Devimon"), participants.map { it.speciesName })
        val stats = TamerBattleFactory.definitions(ArenaEntrant("mirei", "Mirei", "mirei", participants,
            com.github.nacabaro.vbhelper.battle.offline.core.TrainerAiPolicy()),
            com.github.nacabaro.vbhelper.battle.offline.core.BattleSide.OPPOSING)
        assertEquals(900, stats[0].maxHealth)
        assertEquals(1_260, stats[1].maxHealth)
        assertEquals(65, participants[0].battleTechniques[0].power)
        assertEquals(77, participants[1].battleTechniques[0].power)
    }

    @Test fun blastEvolutionUsesTheResolvedPartnersHistoryIncludingGuests() {
        val resolver = resolver("Rize Greymon" to 4, "Victory Greymon" to 5, "Shine Greymon" to 5,
            "Gaioumon" to 5, "Gaioumon Itto Mode" to 5, "War Greymon" to 5)
        val tsurugi = TamerBattleLoadouts.participants(resolver.resolve("tsurugi", listOf(4))!!, resolver).single()
        assertEquals("Victory Greymon", tsurugi.blastTargetSpecies)
        val takumi = TamerBattleLoadouts.participants(resolver.resolve("takumi-rearise", listOf(5))!!, resolver).single()
        assertEquals("Gaioumon Itto Mode", takumi.blastTargetSpecies)
    }

    @Test fun documentedOwnedGameTeamsBeatFabricatedStageExtensions() {
        val resolver = resolver("Pinochimon" to 5, "Hi Andromon" to 5, "Zhuqiaomon" to 5, "Herakle Kabuterimon" to 5)
        assertEquals("Pinochimon", lead(resolver, "skull", 5).species.name)
        assertEquals("Zhuqiaomon", lead(resolver, "kain", 5).species.name)
        assertFalse(lead(resolver, "skull", 5).inferred)
    }

    @Test fun aliasesMatchOfficialSpellingsWithoutMergingActualVariants() {
        assertEquals(BattleSpeciesIdentity.normalize("Grap Leomon"), BattleSpeciesIdentity.normalize("Grappu Leomon"))
        assertEquals(BattleSpeciesIdentity.normalize("Hackmon"), BattleSpeciesIdentity.normalize("Huckmon"))
        assertNotEquals(BattleSpeciesIdentity.normalize("Yatagaramon"), BattleSpeciesIdentity.normalize("Yatagaramon (2006 Anime Version)"))
    }

    @Test fun everyPublishedPrimaryHistoryWinsOverArtworkFromOtherContinuities() {
        val allDocumentedArt = TamerCanonicalPartners.histories.values.flatMap { history ->
            history.stages.flatMap { (stage, names) -> names.map { art(it, stage) } }
        } + TamerCanonicalPartners.fusionLeads.values.flatten().map { art(it.species, it.stage) }
        val resolver = TamerTeamResolver(roster, allDocumentedArt)
        var checked = 0
        for (tamer in roster) {
            val history = TamerCanonicalPartners.history(tamer.id, tamer.primary) ?: continue
            for ((stage, documented) in history.stages) {
                val fusion = TamerCanonicalPartners.fusionLeads[tamer.id]?.firstOrNull { it.stage == stage }
                val expected = if (fusion != null) listOf(fusion.species) else documented
                val partner = lead(resolver, tamer.id, stage)
                assertTrue("${tamer.id}/$stage selected ${partner.species.name} instead of $expected",
                    expected.any { BattleSpeciesIdentity.normalize(it) == BattleSpeciesIdentity.normalize(partner.species.name) })
                assertFalse("${tamer.id}/$stage unexpectedly scaled a documented stage", partner.scaled)
                assertFalse(partner.inferred)
                assertNotNull(partner.sourceUri)
                checked++
            }
        }
        assertTrue("Expected a roster-wide canonical regression, got $checked stage cases", checked >= 350)
    }

    @Test fun onePartnerSharedByTwoHumansCannotOccupyTwoSlots() {
        val resolver = resolver("Gankoomon" to 5)
        assertNotNull(resolver.resolve("hajime", listOf(5)))
        assertNotNull(resolver.resolve("haruka", listOf(5)))
        assertNull(resolver.resolve("hajime", listOf(5, 5)))
        assertNull(resolver.resolve("haruka", listOf(5, 5)))
    }

    @Test fun digixrosPrimaryFormsDoNotFieldTheirOwnAbsorbedComponentsAgain() {
        val resolver = resolver("Metal Greymon (2010 Anime Version)" to 4, "Mail Birdramon" to 3,
            "Cyberdramon (2010 Anime Version)" to 4, "Shoutmon X2" to 3, "Ballistamon" to 3, "Damemon" to 3)
        val kiriha = resolver.resolve("kiriha", listOf(4, 4))!!
        assertEquals("Metal Greymon (2010 Anime Version)", kiriha.members[0].species.name)
        assertEquals("Cyberdramon (2010 Anime Version)", kiriha.members[1].species.name)
        val taiki = resolver.resolve("taiki", listOf(3, 3))!!
        assertEquals(listOf("Shoutmon X2", "Damemon"), taiki.members.map { it.species.name })
        assertEquals(ArenaPartnerOrigin.GUEST, taiki.members[1].origin)
    }

    @Test fun nextOrderUsesItsDocumentedOpeningFusionWithAnIndependentGuest() {
        val resolver = resolver("Omegamon" to 5, "War Greymon" to 5, "Metal Garurumon" to 5,
            "Dukemon" to 5, "Holydramon" to 5, "Piyomon" to 2, "Palmon" to 2)
        assertEquals("Omegamon", lead(resolver, "takuto", 5).species.name)
        assertEquals("Omegamon", lead(resolver, "shiki", 5).species.name)
        val takuto = resolver.resolve("takuto", listOf(5, 5))!!
        val shiki = resolver.resolve("shiki", listOf(5, 5))!!
        assertEquals("Dukemon", takuto.members[1].species.name)
        assertEquals("Holydramon", shiki.members[1].species.name)
        assertEquals(ArenaPartnerOrigin.GUEST, takuto.members[1].origin)
        assertEquals(ArenaPartnerOrigin.GUEST, shiki.members[1].origin)
    }

    @Test fun legacyCupCorrectionsKeepPlayerRegistrationBracketAndSeeds() {
        val resolver = resolver("Victory Greymon" to 5, "Shine Greymon" to 5, "Mirage Gaogamon" to 5,
            "Dukemon" to 5, "Sakuyamon" to 5, "Gaioumon" to 5, "War Greymon" to 5)
        val frozen = com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant(null, "fixture", "War Greymon", 1, 1,
            stage = 5, battleInstanceId = "registered-player", techniqueIds = listOf("generic_assault_quick_combo"))
        val player = ArenaEntrant(ArenaRepository.PLAYER_ID, "Player", null, listOf(frozen),
            com.github.nacabaro.vbhelper.battle.offline.core.TrainerAiPolicy())
        val ids = listOf("tsurugi", "masaru", "thoma", "takato", "ruki", "takumi-rearise", "taiga")
        val entries = listOf(player) + ids.map { id -> ArenaEntrant(id, id, id,
            listOf(frozen.copy(battleInstanceId = "legacy:$id", displayName = "Shine Greymon", speciesName = "Shine Greymon")),
            com.github.nacabaro.vbhelper.battle.offline.core.TrainerAiPolicy()) }
        val old = ArenaTournament.create("legacy", entries, 1, "NORMAL", 919).copy(canonRevision = 0)
        val corrected = ArenaCanonicalMigration.refresh(old, resolver)
        assertSame(player, corrected.entrants.single { it.id == ArenaRepository.PLAYER_ID })
        assertEquals(old.matches, corrected.matches)
        assertEquals(old.seed, corrected.seed)
        assertEquals("Victory Greymon", corrected.entrants.single { it.id == "tsurugi" }.members.single().speciesName)
        assertEquals("Gaioumon", corrected.entrants.single { it.id == "takumi-rearise" }.members.single().speciesName)
        assertEquals(TamerCanonicalPartners.REVISION, corrected.canonRevision)
        assertSame(corrected, ArenaCanonicalMigration.refresh(corrected, resolver))
    }
}
