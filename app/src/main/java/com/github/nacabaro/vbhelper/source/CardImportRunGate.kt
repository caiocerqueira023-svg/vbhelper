package com.github.nacabaro.vbhelper.source

/** A replacement cannot enter until the owning coroutine and its cleanup finish. */
class CardImportRunGate {
    private var active = false
    private var generation = 0L

    @Synchronized fun begin(): Long? {
        if (active) return null
        active = true
        return ++generation
    }

    @Synchronized fun owns(owner: Long): Boolean = active && generation == owner

    @Synchronized fun finish(owner: Long) {
        if (active && generation == owner) active = false
    }
}
