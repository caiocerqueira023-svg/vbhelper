package com.tamerslab.farm.diorama

import com.tamerslab.farm.map.FarmWorldBounds
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cosmetic life for the Home Digifarm.
 *
 * The pacing model is deliberately **probabilistic, not directed**: when a resident finishes what it
 * was doing, it rolls once for its next beat. Most rolls land on standing or walking â€” those are the
 * two everyday behaviours â€” and only occasionally on jogging, training, a happy beat, a nap or going
 * to greet a neighbour. There is no global scheduler, no fixed activity cycle and no cap on how many
 * residents may do the same thing at once; the calm comes from the odds and from held beats lasting
 * seconds, not from a director counting actors.
 *
 * What is deterministic (and therefore testable) is the geometry:
 *
 *  - **Full animation set.** Residents stroll (walk frames), jog (run frames), work out (train),
 *    celebrate (happy), sleep (sleep) and breathe (idle); the renderer picks the pose family from the
 *    activity + `running` flag, and this class only decides where and when.
 *  - **Real collision.** Every position update goes through [resolveOverlaps]: a resident's body is a
 *    circle of [FarmDioramaSeed.bodyRadius] plus a margin, so two sprites can never be drawn on top
 *    of each other. Movers are pushed out of the way; stationary and sleeping residents are never
 *    displaced.
 *  - **Social beats.** A resident that rolls "go and greet" walks to a standing neighbour (picked
 *    randomly among the ones within reach), both turn face to face, celebrate for several seconds,
 *    then the initiator wanders off. Multiple pairs are possible in principle, because nothing limits
 *    them â€” the probability just makes them rare.
 *
 * Every resident owns its own random stream, seeded from the simulation's master seed. The production
 * host deliberately passes fresh entropy (not a roster-derived constant), so no session ever replays
 * the same show; a fixed seed exists only as a constructor parameter so tests can pin behaviour.
 */

/** What a resident is doing. Walking/running are movement states; the others are held beats. */
enum class FarmDioramaActivity { IDLE, WALK, RUN, TRAIN, HAPPY, SLEEP }

/**
 * One resident as the simulation needs it: a stable render id and its body half-width in world
 * units. Walking/running speed is derived from [FarmDioramaSimulation]'s seed + id so callers do not
 * have to persist any personality.
 */
data class FarmDioramaSeed(val id: String, val bodyRadius: Float)

/** One resident for one beat: everything the renderer needs to pose and move it. */
data class FarmDioramaPose(
    val id: String,
    val x: Float,
    val z: Float,
    val activity: FarmDioramaActivity,
    val facingLeft: Boolean,
    val running: Boolean,
    val stepMillis: Long,
    /** World point to look at while standing (social beats); null keeps the travel heading. */
    val lookAtX: Float? = null,
    val lookAtZ: Float? = null,
)

/** One immutable beat of the whole cast. */
data class FarmDioramaFrame(val poses: List<FarmDioramaPose>)

class FarmDioramaSimulation(
    seeds: List<FarmDioramaSeed>,
    private val bounds: FarmWorldBounds,
    seed: Long,
    /** How long each [step] represents when the caller uses the default. */
    val beatMillis: Long = 100L,
) {
    private val random = Random(seed)
    private val actors: List<Actor> = seeds.map { item ->
        Actor(
            id = item.id,
            radius = item.bodyRadius.coerceAtLeast(MIN_BODY_RADIUS),
            // Each Digimon gets its own stream: two residents never share a draw sequence, so their
            // choices stay independent even when the cast order or one resident's timing changes.
            rng = Random(random.nextLong()),
        )
    }
    private val interactions = ArrayList<InteractionState>(2)
    private var nowMillis = 0L

    init {
        stageTableau()
    }

    /** The opening tableau without advancing the clock: everyone awake, standing, separated. */
    fun initialFrame(): FarmDioramaFrame = buildFrame()

    /**
     * The cast's current state without advancing the clock. The host pushes this when it resumes a
     * paused farm, so a place switch never teleports the residents back to their opening positions.
     */
    fun currentFrame(): FarmDioramaFrame = buildFrame()

    /**
     * Advances the cast by [deltaMillis] (clamped so a paused host cannot teleport anyone) and
     * returns the new beat.
     */
    fun step(deltaMillis: Long = beatMillis): FarmDioramaFrame {
        val dt = deltaMillis.coerceIn(0L, MAX_STEP_MILLIS)
        nowMillis += dt
        for (actor in actors) actor.movedThisStep = false
        for (actor in actors) advanceTravel(actor, dt)
        stepInteractions()
        decideFinishedBeats()
        resolveOverlaps()
        return buildFrame()
    }

    // ---- Staging -----------------------------------------------------------------------------

    /**
     * Lays the cast out on a jittered lattice so the opening frame is already collision-free. A
     * lattice (rather than a ring) matters on the small island: twelve wide cardboard bodies do not
     * fit on one circle, and a ring would push everyone to the cliffs.
     */
    private fun stageTableau() {
        if (actors.isEmpty()) return
        val playableWidth = (bounds.maxX - bounds.minX) - 2f * (EDGE_MARGIN + actors.maxOf { it.radius })
        val playableDepth = (bounds.maxZ - bounds.minZ) - 2f * (EDGE_MARGIN + actors.maxOf { it.radius })
        val columns = sqrt(actors.size * (playableWidth / playableDepth).coerceAtLeast(1f))
            .toInt().coerceAtLeast(1)
        val rows = ((actors.size + columns - 1) / columns).coerceAtLeast(1)
        actors.forEachIndexed { index, actor ->
            val column = index % columns
            val row = index / columns
            val cellWidth = playableWidth / columns
            val cellDepth = playableDepth / rows
            val jitterX = (random.nextFloat() - 0.5f) * cellWidth * 0.30f
            val jitterZ = (random.nextFloat() - 0.5f) * cellDepth * 0.30f
            actor.x = bounds.minX + EDGE_MARGIN + actors.maxOf { it.radius } +
                (column + 0.5f) * cellWidth + jitterX
            actor.z = bounds.minZ + EDGE_MARGIN + actors.maxOf { it.radius } +
                (row + 0.5f) * cellDepth + jitterZ
            actor.facingLeft = random.nextBoolean()
        }
        // The jitter can still close a cell gap; symmetric relaxation is fine at staging time
        // because nobody is "walking" yet.
        repeat(STAGING_PASSES) {
            for (index in actors.indices) {
                for (otherIndex in index + 1 until actors.size) {
                    separateStat(actors[index], actors[otherIndex], half = true)
                }
            }
            clampAll()
        }
        actors.forEach { actor ->
            actor.activity = FarmDioramaActivity.IDLE
            actor.running = false
            actor.stepMillis = STILL_STEP_MILLIS
            // Stagger the first rolls so the cast never decides in lockstep.
            actor.deadlineMillis = FIRST_DECISION_MIN_MILLIS +
                random.nextLong(FIRST_DECISION_SPAN_MILLIS)
        }
    }

    // ---- Movement ----------------------------------------------------------------------------

    private fun advanceTravel(actor: Actor, dt: Long) {
        if (actor.activity != FarmDioramaActivity.WALK && actor.activity != FarmDioramaActivity.RUN) {
            return
        }
        // Restored pre-liveliness rhythm: one stride every ~1.15-1.6 s, each one interpolated by the
        // renderer over 1.1 s, which leaves the small foot-plant pause the old diorama had. The
        // simulation still ticks at 10 Hz, but it only advances the body on stride beats.
        if (nowMillis < actor.nextStepAtMillis) return

        val dx = actor.targetX - actor.x
        val dz = actor.targetZ - actor.z
        val distance = hypot(dx, dz)
        if (distance <= ARRIVAL_DISTANCE) {
            actor.activity = FarmDioramaActivity.IDLE
            actor.running = false
            actor.stepMillis = STILL_STEP_MILLIS
            // Roll for the next beat on this same step: arriving must not dead-stop the resident.
            actor.deadlineMillis = nowMillis
            return
        }

        val running = actor.activity == FarmDioramaActivity.RUN
        val strideUnits = if (running) RUN_STEP_UNITS else WALK_STEP_UNITS
        val travel = distance.coerceAtMost(strideUnits)
        val dirX = dx / distance
        val dirZ = dz / distance
        // A stride is a jump, not a smooth path, so sweep it against every neighbour before
        // committing: the resident stops short of the first body it would enter. Without this the
        // collision solver had to undo large intrusions and could leave a pair a few millimetres
        // inside each other (or bounce one resident across half the island).
        val allowed = sweepDistance(actor, dirX, dirZ, travel)
        if (allowed < MIN_STRIDE_UNITS) {
            // Something is directly in the way: keep the walk beat but try again shortly.
            actor.nextStepAtMillis = nowMillis + 300L + actor.rng.nextLong(300L)
            return
        }
        val startX = actor.x
        val startZ = actor.z
        actor.x += dirX * allowed
        actor.z += dirZ * allowed
        if (abs(dx) > 0.0001f) actor.facingLeft = dx < 0f
        actor.stepMillis = if (running) RUN_INTERP_MILLIS else WALK_INTERP_MILLIS
        actor.nextStepAtMillis = nowMillis + if (running) {
            RUN_STEP_INTERVAL_MIN_MILLIS + actor.rng.nextLong(RUN_STEP_INTERVAL_SPAN_MILLIS)
        } else {
            WALK_STEP_INTERVAL_MIN_MILLIS + actor.rng.nextLong(WALK_STEP_INTERVAL_SPAN_MILLIS)
        }
        // Remember the move for the collision pass: a stride snap happens in the same step the
        // activity becomes IDLE, and the solver must still treat that resident as a mover.
        actor.movedThisStep = actor.movedThisStep ||
            abs(actor.x - startX) > 0.00001f || abs(actor.z - startZ) > 0.00001f
    }

    /**
     * How far [actor] may travel along (dirX, dirZ) before touching any neighbour. Solves the moving
     * circle against each other circle for the first contact, so the committed stride never starts an
     * overlap; the collision solver only has to deal with simultaneous movers meeting head-on.
     */
    private fun sweepDistance(actor: Actor, dirX: Float, dirZ: Float, desired: Float): Float {
        var allowed = desired
        for (other in actors) {
            if (other === actor) continue
            val minGap = actor.radius + other.radius + COLLISION_MARGIN
            val ox = other.x - actor.x
            val oz = other.z - actor.z
            val projection = ox * dirX + oz * dirZ
            if (projection <= 0f) continue // moving away from it
            val offset = ox * ox + oz * oz - minGap * minGap
            val discriminant = projection * projection - offset
            if (discriminant <= 0f) continue // the path never reaches its body
            val contact = projection - sqrt(discriminant)
            if (contact < allowed) allowed = contact
        }
        return allowed.coerceAtLeast(0f)
    }

    // ---- Probability rolls -------------------------------------------------------------------

    /**
     * The whole pacing model: one independent roll per finished beat. There is intentionally no
     * global counter or cool-down here â€” the odds below are the only thing that decides how often
     * something beyond standing/walking happens, so the island cannot synchronise into a show.
     */
    private fun decideFinishedBeats() {
        for (actor in actors) {
            if (actor.interacting) continue
            if (nowMillis < actor.deadlineMillis) continue
            if (actor.activity == FarmDioramaActivity.SLEEP) {
                // Waking up goes through a short standing beat before the next roll, so a sleeper
                // never snaps straight into walking.
                startIdle(actor, wake = true)
                continue
            }
            rollNextBeat(actor)
        }
    }

    private fun rollNextBeat(actor: Actor) {
        actor.lookAtX = null
        actor.lookAtZ = null
        val roll = actor.rng.nextFloat()
        var boundary = P_IDLE
        if (roll < boundary) {
            startIdle(actor)
            return
        }
        boundary += P_WALK
        if (roll < boundary) {
            startWalk(actor)
            return
        }
        boundary += P_RUN
        if (roll < boundary) {
            startRun(actor)
            return
        }
        boundary += P_TRAIN
        if (roll < boundary) {
            startTrain(actor)
            return
        }
        boundary += P_HAPPY
        if (roll < boundary) {
            startHappy(actor)
            return
        }
        boundary += P_SLEEP
        if (roll < boundary) {
            startSleep(actor)
            return
        }
        boundary += P_SOCIAL
        if (roll < boundary) {
            // A greeting is the rarest roll; when nobody is standing within reach it turns into an
            // ordinary walk rather than doing nothing noticeable.
            if (!tryStartSocial(actor)) startWalk(actor)
            return
        }
        // Floating-point slack only: every probability above sums to one.
        startIdle(actor)
    }

    private fun startIdle(actor: Actor, wake: Boolean = false) {
        actor.activity = FarmDioramaActivity.IDLE
        actor.running = false
        actor.stepMillis = STILL_STEP_MILLIS
        actor.deadlineMillis = nowMillis +
            (if (wake) wakeDwell(actor.rng) else idleDwell(actor.rng))
    }

    private fun startWalk(actor: Actor) {
        val spot = freeSpot(actor, WALK_TARGET_MIN, WALK_TARGET_MAX, actor.rng) ?: run {
            startIdle(actor)
            return
        }
        actor.targetX = spot.first
        actor.targetZ = spot.second
        actor.activity = FarmDioramaActivity.WALK
        actor.running = false
        actor.stepMillis = WALK_INTERP_MILLIS
        actor.nextStepAtMillis = nowMillis
        actor.deadlineMillis = nowMillis + WALK_TRAVEL_LIMIT_MILLIS
    }

    private fun startRun(actor: Actor) {
        val spot = freeSpot(actor, RUN_TARGET_MIN, RUN_TARGET_MAX, actor.rng) ?: run {
            startIdle(actor)
            return
        }
        actor.targetX = spot.first
        actor.targetZ = spot.second
        actor.activity = FarmDioramaActivity.RUN
        actor.running = true
        actor.stepMillis = RUN_INTERP_MILLIS
        actor.nextStepAtMillis = nowMillis
        actor.deadlineMillis = nowMillis + RUN_TRAVEL_LIMIT_MILLIS
    }

    private fun startTrain(actor: Actor) {
        actor.activity = FarmDioramaActivity.TRAIN
        actor.running = false
        actor.stepMillis = STILL_STEP_MILLIS
        actor.deadlineMillis = nowMillis + TRAIN_DWELL_MIN_MILLIS +
            actor.rng.nextLong(TRAIN_DWELL_SPAN_MILLIS)
    }

    private fun startHappy(actor: Actor) {
        actor.activity = FarmDioramaActivity.HAPPY
        actor.running = false
        actor.stepMillis = STILL_STEP_MILLIS
        actor.deadlineMillis = nowMillis + HAPPY_DWELL_MIN_MILLIS +
            actor.rng.nextLong(HAPPY_DWELL_SPAN_MILLIS)
    }

    private fun startSleep(actor: Actor) {
        actor.activity = FarmDioramaActivity.SLEEP
        actor.running = false
        actor.stepMillis = STILL_STEP_MILLIS
        actor.deadlineMillis = nowMillis + SLEEP_DWELL_MIN_MILLIS +
            actor.rng.nextLong(SLEEP_DWELL_SPAN_MILLIS)
    }

    private fun idleDwell(rng: Random): Long =
        IDLE_DWELL_MIN_MILLIS + rng.nextLong(IDLE_DWELL_SPAN_MILLIS)

    private fun wakeDwell(rng: Random): Long =
        WAKE_DWELL_MIN_MILLIS + rng.nextLong(WAKE_DWELL_SPAN_MILLIS)

    // ---- Social interaction ------------------------------------------------------------------

    /**
     * Starts a greeting when a standing neighbour is within reach; returns false when there is none.
     * The partner is picked **uniformly at random** from everyone in reach (not always the nearest),
     * using the initiator's own stream, so the same pair does not systematically keep finding each
     * other across sessions.
     */
    private fun tryStartSocial(actor: Actor): Boolean {
        if (actors.size < 2) return false
        val eligible = actors.filter { other ->
            other !== actor && !other.interacting &&
                other.activity == FarmDioramaActivity.IDLE &&
                distanceBetween(actor, other) <= SOCIAL_SEEK_RADIUS
        }
        if (eligible.isEmpty()) return false
        val partner = eligible[actor.rng.nextInt(eligible.size)]
        val state = InteractionState(initiator = actor, partner = partner)
        interactions += state
        actor.interacting = true
        partner.interacting = true
        if (distanceBetween(actor, partner) <= contactDistance(state)) {
            beginCelebrate(state)
        } else {
            beginApproach(state)
        }
        return true
    }

    private fun stepInteractions() {
        val iterator = interactions.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next()
            if (stepInteraction(state)) iterator.remove()
        }
    }

    /** Advances one pair; returns true when the interaction is finished and must be dropped. */
    private fun stepInteraction(state: InteractionState): Boolean {
        when (state.phase) {
            InteractionPhase.APPROACH -> {
                if (distanceBetween(state.initiator, state.partner) <= contactDistance(state)) {
                    beginCelebrate(state)
                    return false
                }
                if (nowMillis >= state.phaseEndMillis) {
                    finishInteraction(state)
                    return true
                }
                // The partner keeps facing the arriving resident; the initiator keeps walking to it.
                facePartner(state, both = false)
                if (state.initiator.activity != FarmDioramaActivity.WALK) {
                    state.initiator.activity = FarmDioramaActivity.WALK
                    state.initiator.running = false
                    state.initiator.stepMillis = WALK_INTERP_MILLIS
                }
                state.initiator.targetX = state.partner.x
                state.initiator.targetZ = state.partner.z
                return false
            }
            InteractionPhase.CELEBRATE -> {
                facePartner(state, both = true)
                return if (nowMillis >= state.phaseEndMillis) beginPart(state) else false
            }
            InteractionPhase.PART -> {
                if (nowMillis >= state.phaseEndMillis ||
                    state.initiator.activity == FarmDioramaActivity.IDLE
                ) {
                    finishInteraction(state)
                    return true
                }
                return false
            }
        }
    }

    private fun beginApproach(state: InteractionState) {
        state.phase = InteractionPhase.APPROACH
        state.phaseEndMillis = nowMillis + APPROACH_LIMIT_MILLIS
        val initiator = state.initiator
        initiator.activity = FarmDioramaActivity.WALK
        initiator.running = false
        initiator.stepMillis = WALK_INTERP_MILLIS
        initiator.targetX = state.partner.x
        initiator.targetZ = state.partner.z
        initiator.deadlineMillis = state.phaseEndMillis
        state.partner.activity = FarmDioramaActivity.IDLE
        state.partner.running = false
        state.partner.stepMillis = STILL_STEP_MILLIS
        state.partner.deadlineMillis = state.phaseEndMillis
        facePartner(state, both = false)
    }

    private fun beginCelebrate(state: InteractionState) {
        state.phase = InteractionPhase.CELEBRATE
        state.phaseEndMillis = nowMillis + CELEBRATE_DWELL_MIN_MILLIS +
            state.initiator.rng.nextLong(CELEBRATE_DWELL_SPAN_MILLIS)
        for (actor in arrayOf(state.initiator, state.partner)) {
            actor.activity = FarmDioramaActivity.HAPPY
            actor.running = false
            actor.stepMillis = STILL_STEP_MILLIS
            actor.deadlineMillis = state.phaseEndMillis
        }
        facePartner(state, both = true)
    }

    /** Returns true when the pair is finished on the spot (no room to walk away). */
    private fun beginPart(state: InteractionState): Boolean {
        state.phase = InteractionPhase.PART
        state.phaseEndMillis = nowMillis + PART_LIMIT_MILLIS
        val partner = state.partner
        partner.interacting = false
        partner.lookAtX = null
        partner.lookAtZ = null
        partner.activity = FarmDioramaActivity.IDLE
        partner.stepMillis = STILL_STEP_MILLIS
        partner.deadlineMillis = nowMillis + idleDwell(partner.rng)

        val initiator = state.initiator
        val spot = freeSpot(initiator, PART_TARGET_MIN, PART_TARGET_MAX, initiator.rng)
        if (spot == null) {
            finishInteraction(state)
            return true
        }
        initiator.lookAtX = null
        initiator.lookAtZ = null
        initiator.targetX = spot.first
        initiator.targetZ = spot.second
        initiator.activity = FarmDioramaActivity.WALK
        initiator.running = false
        initiator.stepMillis = WALK_INTERP_MILLIS
        initiator.deadlineMillis = state.phaseEndMillis
        return false
    }

    private fun finishInteraction(state: InteractionState) {
        for (actor in arrayOf(state.initiator, state.partner)) {
            actor.interacting = false
            actor.lookAtX = null
            actor.lookAtZ = null
            if (actor.activity != FarmDioramaActivity.IDLE) startIdle(actor)
        }
    }

    /** Both residents look at each other; optionally only the partner turns first. */
    private fun facePartner(state: InteractionState, both: Boolean) {
        if (both) {
            state.initiator.lookAtX = state.partner.x
            state.initiator.lookAtZ = state.partner.z
        }
        state.partner.lookAtX = state.initiator.x
        state.partner.lookAtZ = state.initiator.z
    }

    private fun contactDistance(state: InteractionState): Float =
        state.initiator.radius + state.partner.radius + CONTACT_GAP

    // ---- Collision ---------------------------------------------------------------------------

    /**
     * Hard non-overlap guarantee. Movers are pushed away from their neighbours; a stationary or
     * sleeping resident is never moved by this pass (so the farm cannot drift while nothing is
     * happening). If two movers meet, both give way half each.
     *
     * With the probability model several residents can walk at once, so the cast is denser than the
     * old budgeted version and a pair can keep passing its correction between neighbours near the
     * cliffs. The normal passes are followed by a residual pass that finishes any pair the relaxation
     * could not settle, still preferring to move walkers rather than planted residents.
     */
    private fun resolveOverlaps() {
        repeat(COLLISION_PASSES) {
            if (!separatePass()) return
        }
        repeat(RESIDUAL_PASSES) {
            if (!separateResidualPass()) return
        }
    }

    /** One Gauss-Seidel pass over every pair, followed by a bounds clamp. True when it moved anyone. */
    private fun separatePass(): Boolean {
        var adjusted = false
        for (index in actors.indices) {
            for (otherIndex in index + 1 until actors.size) {
                if (separateStat(actors[index], actors[otherIndex], half = false)) adjusted = true
            }
        }
        clampAll(EDGE_MARGIN)
        return adjusted
    }

    /**
     * Last-resort pass: any pair still interpenetrating is separated by moving the non-sleeping
     * resident, with a much smaller edge inset than usual so a pair squeezed against the cliffs still
     * has room to separate. Sleepers are never moved here either.
     */
    private fun separateResidualPass(): Boolean {
        var adjusted = false
        for (index in actors.indices) {
            for (otherIndex in index + 1 until actors.size) {
                val first = actors[index]
                val second = actors[otherIndex]
                val minGap = first.radius + second.radius + COLLISION_MARGIN
                var dx = second.x - first.x
                var dz = second.z - first.z
                val distanceSquared = dx * dx + dz * dz
                if (distanceSquared >= minGap * minGap) continue
                val distance = sqrt(distanceSquared)
                if (distance < 1e-4f) {
                    dx = 1f
                    dz = 0f
                }
                val nx = if (distance < 1e-4f) 1f else dx / distance
                val nz = if (distance < 1e-4f) 0f else dz / distance
                val push = minGap - distance + COLLISION_EPSILON
                val firstSleeping = first.activity == FarmDioramaActivity.SLEEP
                val secondSleeping = second.activity == FarmDioramaActivity.SLEEP
                when {
                    firstSleeping && secondSleeping -> Unit
                    firstSleeping -> {
                        second.x += nx * push
                        second.z += nz * push
                        clampActor(second, RESIDUAL_EDGE_MARGIN)
                        adjusted = true
                    }
                    secondSleeping -> {
                        first.x -= nx * push
                        first.z -= nz * push
                        clampActor(first, RESIDUAL_EDGE_MARGIN)
                        adjusted = true
                    }
                    first.isMover && !second.isMover -> {
                        first.x -= nx * push
                        first.z -= nz * push
                        clampActor(first, RESIDUAL_EDGE_MARGIN)
                        adjusted = true
                    }
                    second.isMover && !first.isMover -> {
                        second.x += nx * push
                        second.z += nz * push
                        clampActor(second, RESIDUAL_EDGE_MARGIN)
                        adjusted = true
                    }
                    else -> {
                        first.x -= nx * push * 0.5f
                        first.z -= nz * push * 0.5f
                        second.x += nx * push * 0.5f
                        second.z += nz * push * 0.5f
                        clampActor(first, RESIDUAL_EDGE_MARGIN)
                        clampActor(second, RESIDUAL_EDGE_MARGIN)
                        adjusted = true
                    }
                }
            }
        }
        return adjusted
    }

    /**
     * Pushes two actors out of each other. [half] is used at staging time (nobody is walking yet, so
     * both give way); during the show only the mover is displaced.
     */
    private fun separateStat(first: Actor, second: Actor, half: Boolean): Boolean {
        val minGap = first.radius + second.radius + COLLISION_MARGIN
        var dx = second.x - first.x
        var dz = second.z - first.z
        var distanceSquared = dx * dx + dz * dz
        if (distanceSquared >= minGap * minGap) return false
        var distance = sqrt(distanceSquared)
        if (distance < 1e-4f) {
            dx = 1f
            dz = 0f
            distance = 1f
            distanceSquared = 1f
        }
        val overlap = minGap - distance + COLLISION_EPSILON
        val nx = dx / distance
        val nz = dz / distance
        val firstMoves = first.isMover
        val secondMoves = second.isMover
        when {
            half -> {
                first.x -= nx * overlap * 0.5f
                first.z -= nz * overlap * 0.5f
                second.x += nx * overlap * 0.5f
                second.z += nz * overlap * 0.5f
            }
            firstMoves && secondMoves -> {
                first.x -= nx * overlap * 0.5f
                first.z -= nz * overlap * 0.5f
                second.x += nx * overlap * 0.5f
                second.z += nz * overlap * 0.5f
            }
            firstMoves -> {
                first.x -= nx * overlap
                first.z -= nz * overlap
            }
            secondMoves -> {
                second.x += nx * overlap
                second.z += nz * overlap
            }
            else -> {
                // Should not happen after staging, but a stuck pair must not stay inside each other.
                first.x -= nx * overlap * 0.5f
                first.z -= nz * overlap * 0.5f
                second.x += nx * overlap * 0.5f
                second.z += nz * overlap * 0.5f
            }
        }
        return true
    }

    private fun clampAll(edgeMargin: Float = EDGE_MARGIN) {
        for (actor in actors) clampActor(actor, edgeMargin)
    }

    private fun clampActor(actor: Actor, edgeMargin: Float = EDGE_MARGIN) {
        val margin = edgeMargin + actor.radius
        actor.x = actor.x.coerceIn(bounds.minX + margin, bounds.maxX - margin)
        actor.z = actor.z.coerceIn(bounds.minZ + margin, bounds.maxZ - margin)
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private fun freeSpot(
        actor: Actor,
        minDistance: Float,
        maxDistance: Float,
        rng: Random,
    ): Pair<Float, Float>? {
        val margin = EDGE_MARGIN + actor.radius
        val minX = bounds.minX + margin
        val maxX = bounds.maxX - margin
        val minZ = bounds.minZ + margin
        val maxZ = bounds.maxZ - margin
        if (minX >= maxX || minZ >= maxZ) return null
        var best: Pair<Float, Float>? = null
        var bestScore = Float.NEGATIVE_INFINITY
        repeat(FREE_SPOT_SAMPLES) {
            val x = minX + rng.nextFloat() * (maxX - minX)
            val z = minZ + rng.nextFloat() * (maxZ - minZ)
            val travelled = hypot(x - actor.x, z - actor.z)
            val clearance = actors.asSequence()
                .filter { it !== actor }
                .minOfOrNull { hypot(it.x - x, it.z - z) - it.radius - actor.radius }
                ?: MAX_CLEARANCE
            val inBand = travelled >= minDistance * 0.85f && travelled <= maxDistance * 1.15f
            // A spot that already overlaps someone would only be fixed by the collision solver, and
            // that is exactly where planted neighbours could be disturbed; penalise it hard.
            val safe = clearance >= MIN_TARGET_CLEARANCE
            val score = clearance + (if (inBand) BAND_BONUS else 0f) + (if (safe) 0f else -1f)
            if (score > bestScore) {
                bestScore = score
                best = x to z
            }
        }
        return best
    }

    private fun buildFrame(): FarmDioramaFrame {
        val poses = actors.map { actor ->
            FarmDioramaPose(
                id = actor.id,
                x = actor.x,
                z = actor.z,
                activity = actor.activity,
                facingLeft = actor.facingLeft,
                running = actor.activity == FarmDioramaActivity.RUN,
                stepMillis = actor.stepMillis,
                lookAtX = actor.lookAtX,
                lookAtZ = actor.lookAtZ,
            )
        }
        return FarmDioramaFrame(poses = poses)
    }

    private fun distanceBetween(first: Actor, second: Actor): Float =
        hypot(second.x - first.x, second.z - first.z)

    // ---- Internals ---------------------------------------------------------------------------

    private class Actor(
        val id: String,
        val radius: Float,
        /** This Digimon's own random stream; choices never share a sequence with a peer. */
        val rng: Random,
    ) {
        var x: Float = 0f
        var z: Float = 0f
        var activity: FarmDioramaActivity = FarmDioramaActivity.IDLE
        var facingLeft: Boolean = false
        var running: Boolean = false
        var stepMillis: Long = STILL_STEP_MILLIS
        var lookAtX: Float? = null
        var lookAtZ: Float? = null
        var deadlineMillis: Long = 0L
        var targetX: Float = 0f
        var targetZ: Float = 0f
        /** Earliest time of the next stride beat (the old farm's ~1.15-1.6 s step rhythm). */
        var nextStepAtMillis: Long = 0L
        var interacting: Boolean = false
        /** True when this actor's position changed during the current step (walking or arrival snap). */
        var movedThisStep: Boolean = false

        /** Anyone the collision solver may displace: a walker/runner, or someone who just arrived. */
        val isMover: Boolean
            get() = activity == FarmDioramaActivity.WALK ||
                activity == FarmDioramaActivity.RUN || movedThisStep
    }

    private enum class InteractionPhase { APPROACH, CELEBRATE, PART }

    private class InteractionState(val initiator: Actor, val partner: Actor) {
        var phase: InteractionPhase = InteractionPhase.APPROACH
        var phaseEndMillis: Long = 0L
    }

    private companion object {
        /** Spaces each beat. Ten a second is enough for 10 Hz decisions and smooth interpolation. */
        const val MAX_STEP_MILLIS = 250L

        const val MIN_BODY_RADIUS = 0.02f
        const val COLLISION_MARGIN = 0.012f
        const val COLLISION_EPSILON = 0.0015f
        const val COLLISION_PASSES = 16
        const val RESIDUAL_PASSES = 6
        const val EDGE_MARGIN = 0.015f
        /** Smaller inset used only by the residual solver, so a squeezed pair can still separate. */
        const val RESIDUAL_EDGE_MARGIN = 0.002f
        const val ARRIVAL_DISTANCE = 0.04f
        const val STAGING_PASSES = 10

        const val STILL_STEP_MILLIS = 1_100L

        /**
         * Restored pre-liveliness walking, as the farm shipped before the resident-life pass: one
         * 0.22-unit stride is interpolated by the renderer over 1.1 s, and the next stride starts
         * after a 1.15-1.6 s beat, which leaves the small foot-plant pause the old diorama had. A jog
         * uses the same rhythm family with a longer stride and a shorter window, so it reads faster
         * without strobing.
         */
        const val WALK_STEP_UNITS = 0.22f
        const val RUN_STEP_UNITS = 0.32f
        /** Below this a swept stride is treated as blocked instead of committing a micro-step. */
        const val MIN_STRIDE_UNITS = 0.01f
        const val WALK_INTERP_MILLIS = 1_100L
        const val RUN_INTERP_MILLIS = 800L
        const val WALK_STEP_INTERVAL_MIN_MILLIS = 1_150L
        const val WALK_STEP_INTERVAL_SPAN_MILLIS = 450L
        const val RUN_STEP_INTERVAL_MIN_MILLIS = 900L
        const val RUN_STEP_INTERVAL_SPAN_MILLIS = 300L

        /**
         * The complete pacing model. Each finished beat rolls once; there are no counters or caps
         * anywhere else. Standing and walking dominate (~0.91 together), so the island is mostly
         * residents milling about, and everything else is an occasional exception. Training and
         * sleeping now hold much longer than the other beats (owner request), which is why their
         * probability was trimmed slightly.
         */
        const val P_IDLE = 0.492f
        const val P_WALK = 0.42f
        const val P_RUN = 0.03f
        const val P_TRAIN = 0.02f
        const val P_HAPPY = 0.02f
        const val P_SLEEP = 0.01f
        const val P_SOCIAL = 0.008f

        const val FIRST_DECISION_MIN_MILLIS = 1_500L
        const val FIRST_DECISION_SPAN_MILLIS = 6_500L

        const val IDLE_DWELL_MIN_MILLIS = 2_500L
        const val IDLE_DWELL_SPAN_MILLIS = 5_500L
        const val WAKE_DWELL_MIN_MILLIS = 1_500L
        const val WAKE_DWELL_SPAN_MILLIS = 2_000L
        const val TRAIN_DWELL_MIN_MILLIS = 10_000L
        const val TRAIN_DWELL_SPAN_MILLIS = 10_000L
        const val HAPPY_DWELL_MIN_MILLIS = 1_400L
        const val HAPPY_DWELL_SPAN_MILLIS = 1_200L
        // A nap is a long, visible behaviour now: 30-60 s instead of the old 15-30 s.
        const val SLEEP_DWELL_MIN_MILLIS = 30_000L
        const val SLEEP_DWELL_SPAN_MILLIS = 30_000L

        const val WALK_TRAVEL_LIMIT_MILLIS = 14_000L
        const val RUN_TRAVEL_LIMIT_MILLIS = 8_000L
        const val WALK_TARGET_MIN = 0.30f
        const val WALK_TARGET_MAX = 0.75f
        const val RUN_TARGET_MIN = 0.55f
        const val RUN_TARGET_MAX = 1.05f
        const val PART_TARGET_MIN = 0.25f
        const val PART_TARGET_MAX = 0.60f

        const val SOCIAL_SEEK_RADIUS = 0.90f
        const val CONTACT_GAP = 0.05f
        const val APPROACH_LIMIT_MILLIS = 5_000L
        // Greetings are rare, so they are allowed to linger: the face-to-face celebration alone is
        // 5.5-8.5 s, and the whole beat (walk over, celebrate, walk away) lasts roughly 10-14 s.
        const val CELEBRATE_DWELL_MIN_MILLIS = 5_500L
        const val CELEBRATE_DWELL_SPAN_MILLIS = 3_000L
        const val PART_LIMIT_MILLIS = 5_000L

        const val FREE_SPOT_SAMPLES = 10
        const val BAND_BONUS = 0.05f
        /**
         * A walk target must already be clear of every neighbour by this much, otherwise arriving
         * there would create an overlap the collision solver then has to undo. Must stay above
         * [COLLISION_MARGIN] so arrivals never land inside the minimum gap.
         */
        const val MIN_TARGET_CLEARANCE = 0.02f
        const val MAX_CLEARANCE = 10f
    }
}
