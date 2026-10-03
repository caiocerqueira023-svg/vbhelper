package com.github.nacabaro.vbhelper.screens.cardScreen

import com.github.nacabaro.vbhelper.dtos.CardEvolutionLink
import org.junit.Assert.*
import org.junit.Test

class CardEvolutionLayoutTest {
    private fun node(id: Long, stage: Int, index: Int = id.toInt()) =
        CardEvolutionLayoutNode(id, stage, index)

    @Test fun branchesAndMergesKeepEveryCharacterExactlyOnce() {
        val layout = CardEvolutionLayout.create(
            listOf(node(1, 0), node(2, 1), node(3, 1), node(4, 2)),
            listOf(CardEvolutionLink(1, 2), CardEvolutionLink(1, 3),
                CardEvolutionLink(2, 4), CardEvolutionLink(3, 4)),
        )
        assertEquals(setOf(1L, 2L, 3L, 4L), layout.nodes.keys)
        assertEquals(4, layout.routes.size)
        assertEquals(listOf(0, 1, 2), layout.rows.map { it.stage })
        assertEquals(layout.width / 2, layout.nodes.getValue(1).centerX, .01f)
        assertEquals(layout.width / 2, layout.nodes.getValue(4).centerX, .01f)
    }

    @Test fun disconnectedAndFusionOnlyCharactersStayVisible() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 0), node(2, 5), node(3, 5)),
            listOf(CardEvolutionLink(2, 3, "Virus")))
        assertEquals(3, layout.nodes.size)
        assertTrue(layout.routes.single().isFusion)
        assertEquals(setOf("Virus"), layout.routes.single().fusionAttributes)
    }

    @Test fun duplicateRequirementsCollapseButFusionAttributesArePreserved() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 0), node(2, 1)), listOf(
            CardEvolutionLink(1, 2), CardEvolutionLink(1, 2),
            CardEvolutionLink(1, 2, "Virus"), CardEvolutionLink(1, 2, "Data")))
        assertEquals(2, layout.routes.size)
        assertEquals(setOf("Virus", "Data"), layout.routes.first { it.isFusion }.fusionAttributes)
    }

    @Test fun inputOrderDoesNotChangeTheLayout() {
        val nodes = listOf(node(8, 2, 3), node(2, 0, 0), node(5, 1, 2), node(3, 1, 1))
        val links = listOf(CardEvolutionLink(2, 3), CardEvolutionLink(2, 5), CardEvolutionLink(5, 8))
        assertEquals(CardEvolutionLayout.create(nodes, links),
            CardEvolutionLayout.create(nodes.reversed(), links.reversed()))
    }

    @Test fun parentOrderingReducesCrossedBranches() {
        val layout = CardEvolutionLayout.create(
            listOf(node(1, 0, 0), node(2, 0, 1), node(3, 1, 2), node(4, 1, 3)),
            listOf(CardEvolutionLink(1, 4), CardEvolutionLink(2, 3)))
        assertTrue((layout.nodes.getValue(1).centerX - layout.nodes.getValue(2).centerX) *
            (layout.nodes.getValue(4).centerX - layout.nodes.getValue(3).centerX) > 0)
    }

    @Test fun skippedStagesBackwardRoutesAndSelfLoopsAvoidUnrelatedNodes() {
        val layout = CardEvolutionLayout.create(
            listOf(node(1, 0), node(2, 1), node(3, 2), node(4, 1)),
            listOf(CardEvolutionLink(1, 3), CardEvolutionLink(3, 1),
                CardEvolutionLink(2, 4, "Data"), CardEvolutionLink(2, 2, "Free")))
        for (route in layout.routes) {
            for (other in layout.nodes.values.filter { it.node.id != route.fromId && it.node.id != route.toId }) {
                for ((a, b) in route.points.zipWithNext()) {
                    val crosses = if (a.x == b.x) {
                        a.x > other.x && a.x < other.x + other.size &&
                            maxOf(a.y, b.y) > other.y && minOf(a.y, b.y) < other.y + other.size
                    } else {
                        a.y > other.y && a.y < other.y + other.size &&
                            maxOf(a.x, b.x) > other.x && minOf(a.x, b.x) < other.x + other.size
                    }
                    assertFalse("Route $route crosses node ${other.node.id}", crosses)
                }
            }
        }
    }

    @Test fun everyRouteAndNodeFitsTheContentBounds() {
        val layout = CardEvolutionLayout.create((0..18).map { node(it.toLong(), it % 4, it) },
            (0..17).map { CardEvolutionLink(it.toLong(), (it + 1).toLong(), if (it % 2 == 0) "Data" else null) })
        layout.nodes.values.forEach {
            assertTrue(it.x >= 0 && it.y >= 0 && it.x + it.size <= layout.width && it.y + it.size <= layout.height)
        }
        layout.routes.flatMap { it.points }.forEach {
            assertTrue("Out-of-bounds point $it", it.x in 0f..layout.width && it.y in 0f..layout.height)
        }
    }

    @Test fun invalidEndpointsAreIgnoredWithoutLosingValidNodes() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 0)),
            listOf(CardEvolutionLink(1, 999), CardEvolutionLink(999, 1)))
        assertEquals(1, layout.nodes.size)
        assertTrue(layout.routes.isEmpty())
    }

    @Test fun emptyAndNonstandardStageCardsHaveFiniteDimensions() {
        val empty = CardEvolutionLayout.create(emptyList(), emptyList())
        assertTrue(empty.width.isFinite() && empty.height.isFinite())
        val custom = CardEvolutionLayout.create(listOf(node(1, -1), node(2, 65535)), emptyList())
        assertEquals(2, custom.rows.size)
        assertTrue(custom.height < 1000)
    }

    @Test fun everyAdjacentRouteUsesTheSameStageBus() {
        val layout = CardEvolutionLayout.create(
            listOf(node(1, 2), node(2, 2), node(3, 3), node(4, 3), node(5, 3)),
            listOf(CardEvolutionLink(1, 3), CardEvolutionLink(1, 4), CardEvolutionLink(2, 4), CardEvolutionLink(2, 5)))
        assertEquals(1, layout.routes.map { it.points[1].y }.distinct().size)
        val horizontal = layout.connectorSegments(includeJogress = false).filter { it.start.y == it.end.y }
        assertEquals(1, horizontal.size)
    }

    @Test fun sameStageAndSkippedRoutesShareLanesInsteadOfAddingParallelTracks() {
        val layout = CardEvolutionLayout.create(
            listOf(node(1, 0), node(2, 0), node(3, 1), node(4, 2), node(5, 2)),
            listOf(CardEvolutionLink(1, 2, "Data"), CardEvolutionLink(2, 1, "Virus"),
                CardEvolutionLink(1, 4), CardEvolutionLink(2, 5)))
        assertEquals(1, layout.routes.filter { it.isFusion }.map { it.points[1].y }.distinct().size)
        assertEquals(1, layout.routes.filter { !it.isFusion }.map { it.points[2].x }.distinct().size)
    }

    @Test fun resultsOnlyReachedFromUltimateGetTheirOwnFinalSectionAndBothJogressSources() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 5), node(2, 5), node(3, 5)),
            listOf(CardEvolutionLink(1, 3, isJogress = true), CardEvolutionLink(2, 3, isJogress = true)))
        assertEquals(listOf(5, 6), layout.rows.map { it.stage })
        assertEquals(listOf(3L), layout.rows.last().nodeIds)
        assertEquals(setOf(1L, 2L), layout.routes.map { it.fromId }.toSet())
        assertTrue(layout.routes.all { it.toId == 3L && it.isFusion })
        assertEquals(5, layout.nodes.getValue(3).node.stage) // Presentation never rewrites imported stats.
    }

    @Test fun finalSectionSupportsOrdinaryEvolutionChainsAndPreservesExplicitBemStages() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 5), node(2, 5), node(3, 5), node(4, 6)),
            listOf(CardEvolutionLink(1, 2), CardEvolutionLink(2, 3), CardEvolutionLink(1, 4)))
        assertEquals(listOf(5, 6, 7), layout.rows.map { it.stage })
        assertEquals(setOf(2L, 4L), layout.rows[1].nodeIds.toSet())
        assertEquals(listOf(3L), layout.rows.last().nodeIds)
    }

    @Test fun resultsAlsoReachedFromEarlierStagesStayInTheirImportedStage() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 4), node(2, 5), node(3, 5)),
            listOf(CardEvolutionLink(1, 3), CardEvolutionLink(2, 3, "Data")))
        assertEquals(listOf(4, 5), layout.rows.map { it.stage })
        assertEquals(setOf(2L, 3L), layout.rows.last().nodeIds.toSet())
    }

    @Test fun terminalCyclesDoNotCreateInfinitePresentationStages() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 5), node(2, 5)),
            listOf(CardEvolutionLink(1, 2, "Data"), CardEvolutionLink(2, 1, "Virus")))
        assertEquals(1, layout.rows.size)
        assertEquals(2, layout.routes.size)
    }

    @Test fun adjacentAndSkippedRoutesUseTheSameBusInEverySharedGap() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 0), node(2, 1), node(3, 2)),
            listOf(CardEvolutionLink(1, 2), CardEvolutionLink(1, 3), CardEvolutionLink(2, 3)))
        val y = layout.connectorSegments(false).filter { it.start.y == it.end.y }.map { it.start.y }.distinct()
        assertEquals(2, y.size)
    }

    @Test fun resultsDownstreamFromAnUltimateCycleStillGetTheirFinalSection() {
        val layout = CardEvolutionLayout.create(listOf(node(1, 5), node(2, 5), node(3, 5), node(4, 5)),
            listOf(CardEvolutionLink(1, 2, "Data"), CardEvolutionLink(2, 1, "Virus"),
                CardEvolutionLink(2, 3, "Free"), CardEvolutionLink(3, 4)))
        assertEquals(listOf(5, 6, 7), layout.rows.map { it.stage })
        assertEquals(setOf(1L, 2L), layout.rows.first().nodeIds.toSet())
        assertEquals(listOf(3L), layout.rows[1].nodeIds)
        assertEquals(listOf(4L), layout.rows.last().nodeIds)
    }
}
