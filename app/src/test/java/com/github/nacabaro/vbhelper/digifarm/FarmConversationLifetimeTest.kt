package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.digifarm.social.FarmConversationLifetime
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class FarmConversationLifetimeTest {
    @Test fun overlappingSurfacesKeepRequestsAliveUntilTheLastLeaseLeaves() {
        val life = FarmConversationLifetime()
        life.acquire("farm"); life.acquire("farm")
        val job = Job()
        assertTrue(life.register("farm", life.token("farm"), job))
        life.release("farm")
        assertTrue(job.isActive)
        life.release("farm")
        assertTrue(job.isCancelled)
    }

    @Test fun aStaleRequestCannotStartAfterTheFarmHasBeenReopened() {
        val life = FarmConversationLifetime()
        life.acquire("farm")
        val old = life.token("farm")
        life.release("farm"); life.acquire("farm")
        assertFalse(life.isCurrent("farm", old))
        assertFalse(life.register("farm", old, Job()))
        assertTrue(life.isCurrent("farm", life.token("farm")))
    }

    @Test fun coordinatorSuspensionDoesNotCancelAnotherResumedObserver() {
        val life = FarmConversationLifetime()
        life.acquire("farm")
        val token = life.token("farm")
        life.suspendIfUnobserved("farm")
        assertTrue(life.isCurrent("farm", token))
        assertFalse(life.isCurrent("other", 0))
    }
}
