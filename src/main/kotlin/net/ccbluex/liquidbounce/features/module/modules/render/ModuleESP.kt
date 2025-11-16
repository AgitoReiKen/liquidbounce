/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.utils.client.asText
import net.ccbluex.liquidbounce.utils.client.convertToString
import net.ccbluex.liquidbounce.utils.combat.EntityTaggingManager
import net.ccbluex.liquidbounce.utils.combat.shouldBeShown
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.fabricmc.loader.impl.util.log.Log
import net.fabricmc.loader.impl.util.log.LogCategory
import net.fabricmc.loader.impl.util.log.LogLevel
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import net.ccbluex.liquidbounce.utils.client.logger
import net.ccbluex.liquidbounce.utils.combat.*
import java.lang.System.Logger.Level

/**
 * ESP module
 *
 * Allows you to see targets through walls.
 */

object ModuleESP : ClientModule("ESP", Category.RENDER) {

    override val baseKey: String
        get() = "liquidbounce.module.esp"

    private val modes = choices("Mode", GlowMode, arrayOf(BoxMode, OutlineMode, GlowMode))
    private val colorModes = choices("ColorMode", 0) {
        arrayOf(
            GenericEntityHealthColorMode(it),
            GenericStaticColorMode(it, Color4b.WHITE.alpha(100)),
            GenericRainbowColorMode(it)
        )
    }

    private val friendColor by color("Friends", Color4b(0, 0, 255))
    private val entityColor by color("Entities", Color4b(255, 255, 255))
    private val allEntities by boolean("ShowAllEntities", true)

    abstract class EspMode(
        name: String,
        val requiresTrueSight: Boolean = false
    ) : Choice(name) {
        override val parent
            get() = modes
    }

    private object BoxMode : EspMode("Box") {

        private val outline by boolean("Outline", true)

        @Suppress("unused")
        val renderHandler = handler<WorldRenderEvent> { event ->
            val matrixStack = event.matrixStack

            val entitiesWithBoxes = findRenderedEntities().map { entity ->
                val dimensions = entity.getDimensions(entity.pose)

                val d = dimensions.width.toDouble() / 2.0

                entity to Box(-d, 0.0, -d, d, dimensions.height.toDouble(), d).expand(0.05)
            }

            renderEnvironmentForWorld(matrixStack) {
                BoxRenderer.drawWith(this) {
                    entitiesWithBoxes.forEach { (entity, box) ->
                        val pos = entity.interpolateCurrentPosition(event.partialTicks)
                        val color = getColor(entity)

                        val baseColor = color.alpha(50)
                        val outlineColor = color.alpha(100)

                        withPositionRelativeToCamera(pos) {
                            drawBox(
                                box,
                                baseColor,
                                outlineColor.takeIf { outline }
                            )
                        }
                    }
                }
            }
        }

    }

    object GlowMode : EspMode("Glow", requiresTrueSight = true)

    object OutlineMode : EspMode("Outline", requiresTrueSight = true)

    fun findRenderedEntities() : Iterable<Entity> {
         return world.entities.filter {
            shouldRender(it)
        };
    }

    private fun getBaseColor(entity: Entity): Color4b {
        if (entity is PlayerEntity) {
            if (FriendManager.isFriend(entity) && friendColor.a > 0) {
                return friendColor
            }

            EntityTaggingManager.getTag(entity).color?.let { return it }
        }
        if (entity is LivingEntity) {
            return colorModes.activeChoice.getColor(entity);

        }
        return entityColor;

    }

    fun getColor(entity: Entity): Color4b {
        val baseColor = getBaseColor(entity)

//        Entity translation{key='entity.minecraft.armor_stand', args=[]}
//        literal{flag}
//        literal{flag}
//        empty to Color4b(r=0, g=255, b=0, a=255)

        //flag
        //icelayout1v2 pokeball
        //icelayout1v2
        //Entity translation{key='entity.cobblemon.npc', args=[]}
        // literal{Guardian }[style={color=#4A99D4,!italic}, siblings=[literal{(}[style={color=#41D247},
        // siblings=[literal{ม }[style={color=white}, siblings=[literal{Battle)}[style={color=#41D247}]]]]]]]
        // literal{Guardian }[style={color=#4A99D4,!italic}, siblings=[literal{(}[style={color=#41D247},
        // siblings=[literal{ม }[style={color=white}, siblings=[literal{Battle)}[style={color=#41D247}]]]]]]] empty}
        // to Color4b(r=0, g=255, b=0, a=255
        //='entity.cobblemon.pokemon'
//        logger.info("Entity ${entity.type.name}" +
//            " ${entity.name} ${entity.customName} " +
//            "${entity.commandTags.joinToString().asText()} to $baseColor")
        if (entity is LivingEntity && entity.hurtTime > 0) {
            return Color4b.RED
        }
        // pokeballs probably
        if (entity.name.convertToString().contains("layout")) return Color4b(255, 0, 255, 255)

        return baseColor
    }

    fun shouldRender(entity: Entity) : Boolean {
        if (entity is LivingEntity && entity.shouldBeShown()) return true
        if (allEntities) {
            // flags probably
            if (entity.name.convertToString().contains("flag")) return false

            return true
        }
        return false
    }
    fun requiresTrueSight(entity: Entity) =
        modes.activeChoice.requiresTrueSight && entity.shouldBeShown()

}
