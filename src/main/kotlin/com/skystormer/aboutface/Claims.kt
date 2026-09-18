package com.skystormer.aboutface

import net.minecraft.world.phys.BlockHitResult

/**
 * What the mod is currently asking the server to believe.
 *
 * A vanilla server works out a block's facing for itself, from the state the player appears to be
 * in when the placement arrives. A client-side mod cannot overrule that, so it changes the inputs
 * instead: what the player looks like they are looking at, and what they look like they clicked.
 * Both are claims rather than facts, which is why they live together behind one name.
 *
 * These are plain fields because mixins read them directly on the packet thread; nothing here is
 * clever, and it should stay that way.
 */
object Claims {

    /** Rotation to put on outgoing movement packets, or null to report the player's real one. */
    @JvmField
    var rotation: Rotation? = null

    /** Hit to put on outgoing placements, or null to send the one the player actually clicked. */
    @JvmField
    var hit: BlockHitResult? = null

    /** Drops every claim, so the player's own aim and clicks are reported untouched again. */
    fun clear() {
        rotation = null
        hit = null
    }

    /** A yaw/pitch pair, in Minecraft's own convention: yaw 0 faces south, pitch -90 looks up. */
    data class Rotation(val yaw: Float, val pitch: Float)
}
