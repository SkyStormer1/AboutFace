package com.skystormer.aboutface

import net.minecraft.core.Direction
import net.minecraft.network.chat.Component

/** The few things the mod says, all of them on the action bar and all of them brief. */
object Messages {

    /** Tells the player which way the next block will go down, so the key is never silently on. */
    fun facing(direction: Direction): Component =
        Component.translatable("text.${AboutFaceClient.MOD_ID}.facing", direction.getName())
}
