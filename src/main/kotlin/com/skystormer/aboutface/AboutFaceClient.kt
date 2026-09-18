package com.skystormer.aboutface

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

/**
 * About Face — hold a key and directional blocks go down facing the other way.
 *
 * Everything happens while the key is held and stops the moment it is let go, so the mod is
 * invisible the rest of the time: no toggle to forget about, and nothing hooked into an ordinary
 * placement.
 */
object AboutFaceClient : ClientModInitializer {

    const val MOD_ID = "aboutface"

    /**
     * Held rather than pressed, like crouch.
     *
     * Deliberately a plain [KeyMapping]: its down state is read straight from the keyboard, so it
     * stays true while the right mouse button it is meant to modify is also down. Anything that
     * treats a chord as broken by an extra key would read as released at the one moment that
     * matters — the click.
     */
    lateinit var flipKey: KeyMapping
        private set

    override fun onInitializeClient() {
        flipKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.$MOD_ID.flip",
                GLFW.GLFW_KEY_LEFT_CONTROL,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main")),
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(client) }
    }

    private fun onTick(client: Minecraft) {
        if (client.player == null || client.level == null) {
            Flipper.stop()
            return
        }
        // A key held while a screen is open is the player typing, not building.
        if (flipKey.isDown && client.gui.screen() == null) {
            Flipper.tick(client)
        } else {
            Flipper.stop()
        }
    }
}
