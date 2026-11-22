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
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.utils.client.inGame
import net.ccbluex.liquidbounce.utils.client.notification
import net.fabricmc.loader.api.FabricLoader

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

    @Suppress("unused")
    private val finishHandler = tickHandler {
        if (startupTicks > 0) {
            startupTicks--
            return@tickHandler
        }

        if (!process.isActive) {
            notification(name, "Finished", NotificationEvent.Severity.SUCCESS)
            enabled = false
        }
    }

    private companion object {
        const val STARTUP_TICKS = 20
    }
}
