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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.Blaze3D
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.OptionalInclusion
import net.ccbluex.liquidbounce.config.autoconfig.AutoConfig
import net.ccbluex.liquidbounce.config.autoconfig.AutoConfig.serializeAutoConfig
import net.ccbluex.liquidbounce.config.autoconfig.IncludeConfiguration
import net.ccbluex.liquidbounce.config.gson.publicGson
import net.ccbluex.liquidbounce.config.gson.util.int
import net.ccbluex.liquidbounce.features.marketplace.autoconfig.ConfigTracker
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.integration.interop.badRequest
import net.ccbluex.liquidbounce.integration.interop.forbidden
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.io.atomicWrite
import net.minecraft.SharedConstants
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Local config RestAPI
 *
 * Lists, loads and saves the configs in the user configs folder, the same files the
 * `.localconfig` command works with, so the ClickGUI can switch between them.
 */
/**
 * [complete] is false for configs without the switcher's module list, which only set the modules they name.
 */
private data class LocalConfigData(val name: String, val lastModified: Long, val size: Long, val complete: Boolean)

private data class LocalConfigListData(val active: String, val configs: List<LocalConfigData>)

private const val CONFIG_EXTENSION = ".json"

/**
 * Names of the modules that were on when the switcher saved the config.
 *
 * Auto configs never store whether Misc, Fun and Render modules are on, and loading one leaves every
 * module it does not mention as it is, so without this list modules of the previous config stay on.
 */
private const val ENABLED_MODULES_KEY = "switcherEnabledModules"

/**
 * The switcher saves everything, unlike a config meant for sharing: all categories, binds and hidden flags.
 */
private val SNAPSHOT_INCLUDE = IncludeConfiguration(
    includeBinds = true,
    includeHidden = true,
    optionalInclusions = OptionalInclusion.entries.toSet(),
)

/**
 * One-shot modules (like the ClickGUI), modules that cannot be turned on and the HUD, which every
 * HUD element depends on, are left alone.
 */
private val ClientModule.switchable get() = !notActivatable && !disableActivation && this !== ModuleHud

private fun serializeSnapshot(): JsonObject {
    val writer = StringWriter()
    serializeAutoConfig(writer, SNAPSHOT_INCLUDE, marketplaceItemId = ConfigTracker.trackedItemId)

    return JsonParser.parseString(writer.toString()).asJsonObject.apply {
        add(ENABLED_MODULES_KEY, JsonArray().apply {
            ModuleManager.filter { it.switchable && it.enabled }.forEach { add(it.name) }
        })
    }
}

/**
 * Turns every switchable module on or off to match a config saved by the switcher.
 * Configs without the list (older ones, or ones from elsewhere) keep the usual behaviour.
 */
private fun applyEnabledModules(config: JsonObject) {
    val list = config.get(ENABLED_MODULES_KEY)?.takeIf { it.isJsonArray }?.asJsonArray ?: return
    val enabled = list.mapNotNullTo(HashSet()) { element ->
        element.takeIf { it.isJsonPrimitive }?.asString
    }

    for (module in ModuleManager) {
        if (!module.switchable) {
            continue
        }

        val shouldBeEnabled = module.name in enabled
        if (module.enabled != shouldBeEnabled) {
            module.enabled = shouldBeEnabled
        }
    }
}

private fun configFiles(): List<File> =
    ConfigSystem.userConfigsFolder.listFiles { file ->
        file.isFile && file.name.endsWith(CONFIG_EXTENSION, ignoreCase = true)
    }?.toList().orEmpty()

/**
 * Resolves [name] to a config file directly inside the user configs folder, rejecting
 * names that are blank, contain path or illegal characters, or point anywhere else.
 */
private suspend fun ApplicationCall.configFile(name: String?): File {
    if (name.isNullOrBlank() || name.indexOfAny(SharedConstants.ILLEGAL_FILE_CHARACTERS) != -1 ||
        name.contains("..")) {
        badRequest("Invalid config name")
    }

    val folder = ConfigSystem.userConfigsFolder.canonicalFile
    val file = folder.resolve(name + CONFIG_EXTENSION).canonicalFile
    if (file.parentFile != folder) {
        badRequest("Invalid config name")
    }
    return file
}

// GET /api/v1/client/localConfigs
private fun Route.getLocalConfigs() = get {
    val configs = withContext(Dispatchers.IO) {
        configFiles()
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val complete = runCatching {
                    file.readText().contains("\"$ENABLED_MODULES_KEY\"")
                }.getOrDefault(false)

                LocalConfigData(
                    name = file.name.dropLast(CONFIG_EXTENSION.length),
                    lastModified = file.lastModified(),
                    size = file.length(),
                    complete = complete,
                )
            }
    }
    call.respond(LocalConfigListData(ConfigTracker.localName, configs))
}

// POST /api/v1/client/localConfigs/load
private fun Route.postLoadLocalConfig() = post("/load") {
    val name = call.receive<JsonObject>()["name"]?.asString
    val file = call.configFile(name)
    if (!file.exists()) {
        call.forbidden("Config '$name' not found")
    }

    val config = withContext(Dispatchers.IO) {
        file.bufferedReader().use(AutoConfig::readAutoConfig)
    }

    withContext(Dispatchers.Main) {
        runCatching {
            AutoConfig.withLoading {
                AutoConfig.loadAutoConfig(config)
                applyEnabledModules(config)
            }
            ConfigTracker.loadedLocal(name!!, config.int("marketplaceItemId"), partial = false)
        }.onFailure { error ->
            logger.error("Failed to load local config '$name'", error)
        }
    }.getOrElse {
        call.forbidden("Failed to load config '$name'")
    }

    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/localConfigs/save
private fun Route.postSaveLocalConfig() = post("/save") {
    val payload = call.receive<JsonObject>()
    val name = payload["name"]?.asString
    val overwrite = payload["overwrite"]?.asBoolean == true
    val file = call.configFile(name)

    if (file.exists() && !overwrite) {
        call.respond(HttpStatusCode.Conflict, JsonObject().apply {
            addProperty("reason", "Config '$name' already exists")
        })
        return@post
    }

    // Taken on the game thread, so the values cannot change while they are read
    val snapshot = withContext(Dispatchers.Main) { serializeSnapshot() }

    withContext(Dispatchers.IO) {
        file.toPath().atomicWrite { tempPath ->
            Files.newBufferedWriter(tempPath).use { writer ->
                publicGson.toJson(snapshot, writer)
            }
        }
    }

    call.respond(HttpStatusCode.NoContent)
}

// POST /api/v1/client/localConfigs/browse
private fun Route.postBrowseLocalConfigs() = post("/browse") {
    withContext(Dispatchers.Main) {
        Blaze3D.openPath(ConfigSystem.userConfigsFolder.toPath())
    }
    call.respond(HttpStatusCode.NoContent)
}

internal fun Route.localConfigRoutes() = route("/localConfigs") {
    getLocalConfigs()
    postLoadLocalConfig()
    postSaveLocalConfig()
    postBrowseLocalConfigs()
}
