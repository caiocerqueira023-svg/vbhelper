package com.github.nacabaro.vbhelper.ui

import java.util.concurrent.atomic.AtomicLong

/** Invalidating a surface also revokes every asynchronous result started before it. */
class PublicationGeneration {
    private val value = AtomicLong()
    fun current(): Long = value.get()
    fun invalidate(): Long = value.incrementAndGet()
    fun accepts(generation: Long): Boolean = generation == value.get()
}
