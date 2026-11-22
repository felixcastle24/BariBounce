/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.baritone

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.process.IBaritoneProcess
import net.ccbluex.liquidbounce.event.events.DeathEvent
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.notification
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.player.LocalPlayer

/**
 * Optional integration with the Baritone pathfinding mod.
 *
 * Baritone is a separate mod. Its classes are only touched when it is installed, so the client
 * keeps working without it; the Baritone tab and its modules then simply do not exist.
 */
object BaritoneIntegration {

    val isAvailable: Boolean by lazy { FabricLoader.getInstance().isModLoaded("baritone") }

    val category: ModuleCategory by lazy { ModuleCategories.register(ModuleCategory("Baritone")) }

    /**
     * The modules that each run one Baritone job. Only one of them runs at a time.
     */
    internal val jobModules: List<BaritoneJobModule> by lazy {
        listOf(
            ModuleBaritoneGoto,
            ModuleBaritoneMine,
            ModuleBaritoneFollow,
            ModuleBaritoneFarm,
            ModuleBaritoneExplore,
        )
    }

    /**
     * Every module of the Baritone tab, or nothing when Baritone is not installed.
     */
    fun modules(): List<ClientModule> {
        if (!isAvailable) {
            return emptyList()
        }

        category
        return jobModules + ModuleBaritoneSettings
    }

    internal val baritone: IBaritone
        get() = BaritoneAPI.getProvider().primaryBaritone
}

/**
 * A module that starts a Baritone job when it is turned on, stops it when it is turned off,
 * and turns itself off once Baritone has finished the job.
 */
abstract class BaritoneJobModule(name: String) : ClientModule(
    name,
    BaritoneIntegration.category,
    disableOnQuit = true,
) {

    /**
     * Ticks to wait after starting before checking whether the job is still running,
     * since Baritone needs a moment to pick up a new job.
     */
    private var startupTicks = 0

    protected val baritone: IBaritone
        get() = BaritoneIntegration.baritone

    /** The Baritone process that does this module's job. */
    protected abstract val process: IBaritoneProcess

    /**
     * Starts the job. Returns false (after telling the user why) when it cannot start.
     */
    protected abstract fun start(): Boolean

    override fun onEnabled() {
        if (!inGame) {
            return
        }
        resumeState = ResumeState.NONE
        resumeAttempts = 0
        trackedPlayer = player

        // Baritone runs one job at a time, so a new job replaces the one before it
        for (other in BaritoneIntegration.jobModules) {
            if (other !== this && other.enabled) {
                other.enabled = false
            }
        }

        if (!start()) {
            enabled = false
            return
        }
        startupTicks = STARTUP_TICKS
        super.onEnabled()
    }

    override fun onDisabled() {
        if (inGame) {
            baritone.pathingBehavior.cancelEverything()
        }
        super.onDisabled()
    }

    protected fun fail(message: String) {
        notification(name, message, NotificationEvent.Severity.ERROR)
    }

    /**
     * Where a job stands after the player died, when [ModuleBaritoneSettings.resumeAfterDeath] is on.
     */
    private enum class ResumeState {
        /** Running normally. */
        NONE,

        /** The player is dead; the job is paused. */
        WAITING_FOR_RESPAWN,

        /** Respawned; waiting for the world around the player to load before starting again. */
        SETTLING,

        /** Started again; Baritone giving up soon after is retried. */
        RESUMED,
    }

    private var resumeState = ResumeState.NONE
    private var settleTicks = 0
    private var resumeGraceTicks = 0
    private var resumeAttempts = 0

    /**
     * The player entity the job runs for. Every respawn replaces it, which also catches a death
     * whose [DeathEvent] was missed.
     */
    private var trackedPlayer: LocalPlayer? = null

    /**
     * Whether the job has reached what it was started for, to tell a finished job apart from
     * Baritone giving up. Jobs without a fixed target never count as reached.
     */
    protected open fun isGoalReached(): Boolean = false

    private fun onPlayerDied() {
        if (resumeState == ResumeState.WAITING_FOR_RESPAWN) {
            return
        }

        if (!ModuleBaritoneSettings.resumeAfterDeath) {
            notification(name, "Stopped because you died", NotificationEvent.Severity.INFO)
            enabled = false
            return
        }

        resumeState = ResumeState.WAITING_FOR_RESPAWN
        resumeAttempts = 0
        logger.info("Player died, pausing the job until respawn")
        // Baritone cannot walk while the player is dead, so the job is paused cleanly
        baritone.pathingBehavior.cancelEverything()
    }

    @Suppress("unused")
    private val deathHandler = handler<DeathEvent> {
        // The health packet first reaches this event on the network thread and is then handled again
        // on the game thread. Baritone may only be touched from the game thread.
        if (mc.isSameThread) {
            onPlayerDied()
        }
    }

    /**
     * Starts the job again after a respawn. Returns false when it cannot start at all.
     */
    private fun resume(): Boolean {
        if (!start()) {
            enabled = false
            return false
        }

        resumeAttempts++
        resumeState = ResumeState.RESUMED
        logger.info("Resumed after respawn (attempt $resumeAttempts)")
        resumeGraceTicks = RESUME_GRACE_TICKS
        startupTicks = STARTUP_TICKS
        if (resumeAttempts == 1) {
            notification(name, "Resumed after respawn", NotificationEvent.Severity.INFO)
        }
        return true
    }

    /** True once the chunk the player stands in has been loaded after a respawn. */
    private fun isAreaLoaded() = world.hasChunk(player.blockX shr 4, player.blockZ shr 4)

    @Suppress("unused", "CyclomaticComplexMethod", "ReturnCount")
    private val finishHandler = tickHandler {
        val current = player
        if (current !== trackedPlayer) {
            // A new player entity means the player respawned, possibly without a death event
            trackedPlayer = current
            if (resumeState == ResumeState.NONE || resumeState == ResumeState.RESUMED) {
                onPlayerDied()
                if (!enabled) return@tickHandler
            }
        }

        // Baritone's job goes idle while the player is dead, which is not the job being finished
        if (current.isDeadOrDying) {
            onPlayerDied()
            return@tickHandler
        }

        when (resumeState) {
            ResumeState.WAITING_FOR_RESPAWN -> {
                resumeState = ResumeState.SETTLING
                settleTicks = SETTLE_TICKS
                return@tickHandler
            }

            ResumeState.SETTLING -> {
                // Starting before the respawn area is loaded makes Baritone give up right away
                if (!isAreaLoaded() || --settleTicks > 0) return@tickHandler
                resume()
                return@tickHandler
            }

            ResumeState.RESUMED -> if (--resumeGraceTicks <= 0) {
                resumeState = ResumeState.NONE
            }

            ResumeState.NONE -> {}
        }

        if (startupTicks > 0) {
            startupTicks--
            return@tickHandler
        }

        if (!process.isActive) {
            // Giving up shortly after a respawn is usually the area still loading: try again
            if (resumeState == ResumeState.RESUMED && !isGoalReached()) {
                if (resumeAttempts < MAX_RESUME_ATTEMPTS) {
                    logger.info("Baritone gave up right after resuming, retrying")
                    resumeState = ResumeState.SETTLING
                    settleTicks = RETRY_DELAY_TICKS
                    return@tickHandler
                }

                logger.info("Could not resume after $resumeAttempts attempts")
                notification(name, "Could not resume after death", NotificationEvent.Severity.ERROR)
                enabled = false
                return@tickHandler
            }

            notification(name, "Finished", NotificationEvent.Severity.SUCCESS)
            enabled = false
            onFinished()
        }
    }

    /**
     * Called after Baritone stopped the job by itself and the module has turned off: either the job
     * is done, or Baritone gave up on it.
     */
    protected open fun onFinished() {}

    private companion object {
        /** Ticks Baritone gets to pick up a job before it is checked. */
        const val STARTUP_TICKS = 20

        /** Ticks to wait after respawning, once the player's chunk is loaded, before resuming. */
        const val SETTLE_TICKS = 40

        /** How long after resuming Baritone giving up is retried instead of ending the job. */
        const val RESUME_GRACE_TICKS = 200

        const val RETRY_DELAY_TICKS = 40
        const val MAX_RESUME_ATTEMPTS = 3
    }
}
