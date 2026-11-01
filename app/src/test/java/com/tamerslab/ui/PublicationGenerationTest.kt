package com.tamerslab.ui

import org.junit.Assert.*
import org.junit.Test

class PublicationGenerationTest {
    @Test fun changingPartnerRejectsAnAlreadyRunningLoad() {
        val publication = PublicationGeneration()
        val oldPartner = publication.current()
        publication.invalidate()
        assertFalse(publication.accepts(oldPartner))
        assertTrue(publication.accepts(publication.current()))
    }
    @Test fun multipleChangesCannotMakeAnOlderResultCurrentAgain() {
        val publication = PublicationGeneration()
        val first = publication.current()
        val second = publication.invalidate()
        val third = publication.invalidate()
        assertFalse(publication.accepts(first))
        assertFalse(publication.accepts(second))
        assertTrue(publication.accepts(third))
    }
    @Test fun stoppingAndRestartingTheSurfaceRevokesPreviousPublications() {
        val publication = PublicationGeneration()
        val inFlight = publication.current()
        publication.invalidate()
        publication.invalidate()
        assertFalse(publication.accepts(inFlight))
    }
}
