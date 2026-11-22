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
package net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.client

import com.google.gson.JsonObject
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.features.module.modules.baritone.BaritoneIntegration
import net.ccbluex.liquidbounce.features.module.modules.baritone.ModuleBaritoneGoto
import net.ccbluex.liquidbounce.integration.interop.badRequest
import net.ccbluex.liquidbounce.integration.interop.forbidden
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.MapColor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/**
 * Map RestAPI
 *
 * Draws a top-down map of the loaded chunks around the player, colored like vanilla maps,
 * so the ClickGUI can pick a target for Baritone's Goto on it.
 */
private data class MapGoalData(val x: Int, val z: Int, val active: Boolean)

private data class MapData(
    /** World coordinates of the image's top-left pixel; one pixel is one block. */
    val originX: Int,
    val originZ: Int,
    val size: Int,
    val playerX: Double,
    val playerZ: Double,
    val playerYaw: Float,
    val baritone: Boolean,
    val goal: MapGoalData?,
    /** PNG as a data URL; unloaded chunks are transparent. */
    val image: String,
)

private const val MIN_RADIUS = 32
private const val MAX_RADIUS = 256
private const val SURFACE_SEARCH_DEPTH = 32
private const val UNLOADED = Int.MIN_VALUE

/**
 * Height of the highest block with a map color in a column, or [UNLOADED] when its chunk is not loaded.
 * Blocks without a map color (glass, rails, ...) are skipped like on a vanilla map.
 */
private fun surfaceHeight(level: ClientLevel, x: Int, z: Int, pos: BlockPos.MutableBlockPos): Int {
    if (!level.hasChunk(x shr 4, z shr 4)) {
        return UNLOADED
    }

    val chunk = level.getChunk(x shr 4, z shr 4)
    val top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x and 15, z and 15)
    pos.set(x, top, z)

    repeat(SURFACE_SEARCH_DEPTH) {
        if (pos.y < level.minY) {
            return top
        }
        val state = chunk.getBlockState(pos)
        if (!state.isAir && state.getMapColor(level, pos) != MapColor.NONE) {
            return pos.y
        }
        pos.move(0, -1, 0)
    }
    return top
}

/**
 * ARGB pixels of the map, row by row from north to south. Has to run on the game thread.
 */
@Suppress("NestedBlockDepth")
private fun renderMap(level: ClientLevel, originX: Int, originZ: Int, size: Int): IntArray {
    val pos = BlockPos.MutableBlockPos()
    val pixels = IntArray(size * size)

    // Heights of the row north of the current one, for vanilla-style slope shading
    var northHeights = IntArray(size) { surfaceHeight(level, originX + it, originZ - 1, pos) }
    val heights = IntArray(size)

    for (row in 0 until size) {
        val z = originZ + row

        for (column in 0 until size) {
            val x = originX + column
            val height = surfaceHeight(level, x, z, pos)
            heights[column] = height

            if (height == UNLOADED) {
                continue
            }

            pos.set(x, height, z)
            val mapColor = level.getBlockState(pos).getMapColor(level, pos)
            if (mapColor == MapColor.NONE) {
                continue
            }

            val north = northHeights[column]
            val brightness = when {
                north == UNLOADED || height == north -> MapColor.Brightness.NORMAL
                height > north -> MapColor.Brightness.HIGH
                else -> MapColor.Brightness.LOW
            }
            pixels[row * size + column] = mapColor.calculateARGBColor(brightness)
        }

        northHeights = heights.copyOf()
    }

    return pixels
}

private fun encodePng(pixels: IntArray, size: Int): String {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, size, size, pixels, 0, size)

    val bytes = ByteArrayOutputStream().use { out ->
        ImageIO.write(image, "png", out)
        out.toByteArray()
    }
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
}

// GET /api/v1/client/map?radius=128
private fun Route.getMap() = get {
    val requestedRadius = call.queryParameters["radius"]?.toIntOrNull()

    val snapshot = withContext(Dispatchers.Main) {
        val level = mc.level ?: return@withContext null
        val player = mc.player ?: return@withContext null

        val radius = (requestedRadius ?: (mc.options.renderDistance().get() * 16))
            .coerceIn(MIN_RADIUS, MAX_RADIUS)
        val size = radius * 2 + 1
        val originX = player.blockX - radius
        val originZ = player.blockZ - radius

        val goal = if (BaritoneIntegration.isAvailable) {
            MapGoalData(ModuleBaritoneGoto.x, ModuleBaritoneGoto.z, ModuleBaritoneGoto.enabled)
        } else {
            null
        }

        val pixels = renderMap(level, originX, originZ, size)
        Triple(pixels, size, MapData(
            originX, originZ, size, player.x, player.z, player.yRot,
            BaritoneIntegration.isAvailable, goal, image = ""
        ))
    } ?: call.forbidden("Not in a world")

    val (pixels, size, data) = snapshot
    val image = withContext(Dispatchers.IO) { encodePng(pixels, size) }
    call.respond(data.copy(image = image))
}

// POST /api/v1/client/baritone/goto
private fun Route.postBaritoneGoto() = post("/baritone/goto") {
    if (!BaritoneIntegration.isAvailable) {
        call.forbidden("Baritone is not installed")
    }

    val payload = call.receive<JsonObject>()
    val x = payload["x"]?.takeIf { it.isJsonPrimitive }?.asInt ?: call.badRequest("Missing x")
    val z = payload["z"]?.takeIf { it.isJsonPrimitive }?.asInt ?: call.badRequest("Missing z")

    withContext(Dispatchers.Main) {
        if (mc.level == null) {
            return@withContext false
        }
        ModuleBaritoneGoto.walkTo(x, z)
        true
    } || call.forbidden("Not in a world")

    call.respond(HttpStatusCode.NoContent)
}

internal fun Route.mapRoutes() {
    route("/map") {
        getMap()
    }
    postBaritoneGoto()
}
