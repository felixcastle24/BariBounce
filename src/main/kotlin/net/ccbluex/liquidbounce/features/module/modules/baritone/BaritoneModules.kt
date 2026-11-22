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
@file:Suppress("MatchingDeclarationName")

package net.ccbluex.liquidbounce.features.module.modules.baritone

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalXZ
import baritone.api.process.IBaritoneProcess
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.collection.blockSortedSetOf
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

private const val WORLD_BORDER = 30_000_000

/**
 * Walks to the given coordinates.
 */
object ModuleBaritoneGoto : BaritoneJobModule("Goto") {

    internal var x by int("X", 0, -WORLD_BORDER..WORLD_BORDER)
    internal var useY by boolean("UseY", false)
    private val y by int("Y", 64, -64..320)
    internal var z by int("Z", 0, -WORLD_BORDER..WORLD_BORDER)

    override val process: IBaritoneProcess
        get() = baritone.customGoalProcess

    override fun start(): Boolean {
        val goal = if (useY) GoalBlock(x, y, z) else GoalXZ(x, z)
        baritone.customGoalProcess.setGoalAndPath(goal)
        return true
    }

    /**
     * Walks to [targetX], [targetZ] at any height, as picked on the ClickGUI map.
     * A walk already under way is replaced.
     */
    fun walkTo(targetX: Int, targetZ: Int) {
        x = targetX
        z = targetZ
        useY = false

        if (enabled) {
            enabled = false
        }
        enabled = true
    }
}

/**
 * Finds and mines the selected blocks.
 */
object ModuleBaritoneMine : BaritoneJobModule("Mine") {

    private val blocks by blocks("Blocks", blockSortedSetOf())
    private val quantity by int("Quantity", 0, 0..2304)

    override val process: IBaritoneProcess
        get() = baritone.mineProcess

    override fun start(): Boolean {
        if (blocks.isEmpty()) {
            fail("Choose at least one block in the settings")
            return false
        }

        baritone.mineProcess.mine(quantity, *blocks.toTypedArray())
        return true
    }
}

/**
 * Follows nearby players or mobs.
 */
object ModuleBaritoneFollow : BaritoneJobModule("Follow") {

    private enum class Targets(override val tag: String) : Tagged {
        PLAYERS("Players"),
        MOBS("Mobs"),
        ALL("All"),
    }

    private val targets by enumChoice("Targets", Targets.PLAYERS)

    override val process: IBaritoneProcess
        get() = baritone.followProcess

    private fun shouldFollow(entity: Entity): Boolean {
        if (entity === player || entity !is LivingEntity || !entity.isAlive) {
            return false
        }

        return when (targets) {
            Targets.PLAYERS -> entity is Player
            Targets.MOBS -> entity !is Player
            Targets.ALL -> true
        }
    }

    override fun start(): Boolean {
        baritone.followProcess.follow(::shouldFollow)
        return true
    }
}

/**
 * Harvests and replants crops.
 */
object ModuleBaritoneFarm : BaritoneJobModule("Farm") {

    private val range by int("Range", 0, 0..512, "blocks")

    override val process: IBaritoneProcess
        get() = baritone.farmProcess

    override fun start(): Boolean {
        baritone.farmProcess.farm(range)
        return true
    }
}

/**
 * Walks into chunks that have not been seen yet, starting where the player stands.
 */
object ModuleBaritoneExplore : BaritoneJobModule("Explore") {

    override val process: IBaritoneProcess
        get() = baritone.exploreProcess

    override fun start(): Boolean {
        baritone.exploreProcess.explore(player.blockX, player.blockZ)
        return true
    }
}

/**
 * Baritone's main options. Changes are passed straight to Baritone.
 */
object ModuleBaritoneSettings : ClientModule(
    "Baritone",
    BaritoneIntegration.category,
    notActivatable = true,
) {

    private val baritoneSettings get() = BaritoneAPI.getSettings()

    init {
        boolean("AllowBreak", true).onChange { baritoneSettings.allowBreak.value = it; it }
        boolean("AllowPlace", true).onChange { baritoneSettings.allowPlace.value = it; it }
        boolean("AllowSprint", true).onChange { baritoneSettings.allowSprint.value = it; it }
        boolean("AllowParkour", false).onChange { baritoneSettings.allowParkour.value = it; it }
        boolean("AllowInventory", false).onChange { baritoneSettings.allowInventory.value = it; it }
        boolean("FreeLook", true).onChange { baritoneSettings.freeLook.value = it; it }
        boolean("RenderPath", true).onChange { baritoneSettings.renderPath.value = it; it }
        boolean("ChatPrefix", true).onChange { baritoneSettings.prefixControl.value = it; it }
    }

    /**
     * Whether a running Baritone job (Goto, Mine, ...) starts again after the player died and
     * respawned, instead of stopping.
     */
    val resumeAfterDeath by boolean("ResumeAfterDeath", true)
}
