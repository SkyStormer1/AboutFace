package com.skystormer.aboutface

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Works out, every tick, how to make the next block go down facing the other way.
 *
 * The approach is to arrange the answer in advance rather than to intervene in the placement. While
 * the key is held this keeps the server believing whatever it needs to believe for the block under
 * the crosshair to come out reversed; the player's own right click is then an ordinary placement
 * that happens to produce a turned-around block. Nothing is cancelled and nothing is replayed,
 * which matters because a click is served before a cancellation could reach it.
 *
 * Two claims are available, and the cheaper one is preferred:
 *
 *  - **Rotation.** Most directional blocks take their facing from where the player is looking, so
 *    claiming a different rotation is enough and nothing else about the placement changes.
 *  - **The click itself.** Some blocks ignore the look direction entirely — a hopper takes its
 *    facing from the face that was clicked. Claiming a click on the empty space the block is going
 *    into leaves it landing in the same place, because a click on a replaceable block targets that
 *    block's own position, while making the clicked face free to choose.
 *
 * Which properties respond to which input is never assumed. Every candidate is tried by asking the
 * block what it would place, so a block that cannot be turned around this way is left alone instead
 * of being placed wrongly.
 */
object Flipper {

    /**
     * Facing properties this understands.
     *
     * Vanilla does not give every block the same one, and the differences are not cosmetic:
     * [BlockStateProperties.FACING_HOPPER] holds five directions rather than six because a hopper
     * cannot point up, so a hopper reading `facing=north` still answers no to `hasProperty(FACING)`.
     * Missing an entry here does not cause a wrong placement — the block is simply treated as having
     * no direction and left alone.
     */
    private val FACINGS: List<EnumProperty<Direction>> = listOf(
        BlockStateProperties.FACING,
        BlockStateProperties.FACING_HOPPER,
        BlockStateProperties.HORIZONTAL_FACING,
    )

    /**
     * Heights within a block to claim a click at, as a fraction of its height.
     *
     * Blocks such as stairs read how high up the face was clicked, so offering both halves lets a
     * candidate satisfy a block that cares, without disturbing the many that do not.
     */
    private val CLICK_HEIGHTS = listOf(0.25, 0.75)

    private var announced: Direction? = null

    /** Called every client tick while the mod is active. */
    fun tick(client: Minecraft) {
        val player = client.player ?: return stop()
        val level = client.level ?: return stop()

        val hit = client.hitResult as? BlockHitResult ?: return stop()
        if (hit.type != HitResult.Type.BLOCK) return stop()

        val hand = handWithBlock(player) ?: return stop()
        val asPlaced = placementState(player, hand, hit)
        val facing = facingOf(asPlaced) ?: return stop()

        val wanted = facing.opposite
        val landing = landingPos(player, hand, hit)
        val plan = planFor(player, level, hand, hit, landing, wanted) ?: return stop()

        // Re-sent every tick rather than once. A player who stands still sends no movement packet
        // of their own for the mixin to rewrite, and the server would go on believing whatever it
        // was told last — which, once the key is released, would be a rotation that no longer
        // applies.
        Claims.rotation = plan.rotation
        Claims.hit = plan.hit
        client.connection?.send(
            ServerboundMovePlayerPacket.Rot(plan.rotation.yaw, plan.rotation.pitch, player.onGround(), false)
        )

        if (announced != wanted) {
            announced = wanted
            client.gui.hud.setOverlayMessage(Messages.facing(wanted), false)
        }
    }

    /**
     * Stops claiming anything, and tells the server the truth immediately.
     *
     * Dropping the claim is not enough on its own. The client only reports rotation when it
     * changes, so a player who releases the key while standing still sends nothing at all, and the
     * server keeps the claimed rotation for as long as they stay put. Sending the real one here is
     * what makes letting go take effect at once rather than whenever they next move.
     */
    fun stop() {
        if (Claims.rotation == null && Claims.hit == null) {
            announced = null
            return
        }
        Claims.clear()
        announced = null

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        client.connection?.send(
            ServerboundMovePlayerPacket.Rot(player.yRot, player.xRot, player.onGround(), false)
        )
    }

    private data class Plan(val rotation: Claims.Rotation, val hit: BlockHitResult?)

    private fun planFor(
        player: LocalPlayer,
        level: net.minecraft.world.level.Level,
        hand: InteractionHand,
        hit: BlockHitResult,
        landing: BlockPos,
        wanted: Direction,
    ): Plan? {
        // Rotation alone first: it leaves everything else about the placement untouched.
        rotationProducing(player, hand, hit, wanted)?.let { return Plan(it, null) }

        // Otherwise claim a click on the space the block is going into. Vanilla resolves a click on
        // a replaceable block to that block's own position, so the placement still lands where the
        // player is pointing while the claimed face becomes ours to choose. This needs nothing to
        // be standing next to it, so it works in mid-air as readily as against a wall.
        if (!level.getBlockState(landing).canBeReplaced()) return null
        for (face in Direction.entries) {
            for (height in CLICK_HEIGHTS) {
                val where = Vec3(landing.x + 0.5, landing.y + height, landing.z + 0.5)
                val candidate = BlockHitResult(where, face, landing, false)
                if (landingPos(player, hand, candidate) != landing) continue
                rotationProducing(player, hand, candidate, wanted)?.let { return Plan(it, candidate) }
            }
        }
        return null
    }

    /**
     * A rotation that makes this placement come out facing [wanted], or null if none does.
     *
     * The player is turned, the block is asked what it would place, and the player is turned back.
     * That is the same question the server will answer for itself, so this cannot claim a rotation
     * that would not actually work.
     */
    private fun rotationProducing(
        player: LocalPlayer,
        hand: InteractionHand,
        hit: BlockHitResult,
        wanted: Direction,
    ): Claims.Rotation? {
        val realYaw = player.yRot
        val realPitch = player.xRot
        val realHead = player.yHeadRot
        try {
            for (candidate in rotationCandidates(realPitch, wanted)) {
                player.yRot = candidate.yaw
                player.yHeadRot = candidate.yaw
                player.xRot = candidate.pitch
                if (facingOf(placementState(player, hand, hit)) == wanted) return candidate
            }
        } finally {
            player.yRot = realYaw
            player.xRot = realPitch
            player.yHeadRot = realHead
        }
        return null
    }

    /**
     * Rotations worth trying, least disruptive first.
     *
     * A horizontal answer is tried without touching pitch before anything else, because the claimed
     * pitch is not private to the facing question — the server believes it for the whole placement,
     * and a claim of staring at the ceiling changes what blocks that read the click height decide.
     */
    private fun rotationCandidates(realPitch: Float, wanted: Direction): List<Claims.Rotation> {
        val all = Direction.entries.map { lookingAlong(it) }
        if (!wanted.axis.isHorizontal) return all
        return all.map { Claims.Rotation(it.yaw, realPitch) } + all
    }

    /**
     * The rotation of a player looking along [direction], in Minecraft's convention: yaw runs from
     * south through west, and pitch is negative looking up.
     */
    private fun lookingAlong(direction: Direction): Claims.Rotation = when (direction) {
        Direction.SOUTH -> Claims.Rotation(0f, 0f)
        Direction.WEST -> Claims.Rotation(90f, 0f)
        Direction.NORTH -> Claims.Rotation(180f, 0f)
        Direction.EAST -> Claims.Rotation(-90f, 0f)
        Direction.UP -> Claims.Rotation(0f, -90f)
        Direction.DOWN -> Claims.Rotation(0f, 90f)
    }

    /** Where a placement made with this click would actually put the block. */
    private fun landingPos(player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult): BlockPos =
        BlockPlaceContext(player, hand, player.getItemInHand(hand), hit).clickedPos

    private fun placementState(player: LocalPlayer, hand: InteractionHand, hit: BlockHitResult): BlockState? {
        val stack = player.getItemInHand(hand)
        val item = stack.item as? BlockItem ?: return null
        return item.block.getStateForPlacement(BlockPlaceContext(player, hand, stack, hit))
    }

    /**
     * Which way a block faces, whatever shape its orientation happens to take.
     *
     * A crafter does not store a facing at all. It stores an orientation — a front paired with a
     * top, twelve combinations rather than six — so it has to be read differently, and only its
     * front is meaningful to turn around. Flipping the front and letting the top fall out of the
     * rotation that produces it gives the same result a player would get facing the other way, which
     * is what turning a block around ought to mean.
     */
    private fun facingOf(state: BlockState?): Direction? {
        if (state == null) return null
        FACINGS.firstOrNull { state.hasProperty(it) }?.let { return state.getValue(it) }
        if (state.hasProperty(BlockStateProperties.ORIENTATION)) {
            return state.getValue(BlockStateProperties.ORIENTATION).front()
        }
        return null
    }

    private fun handWithBlock(player: LocalPlayer): InteractionHand? = when {
        player.getItemInHand(InteractionHand.MAIN_HAND).item is BlockItem -> InteractionHand.MAIN_HAND
        player.getItemInHand(InteractionHand.OFF_HAND).item is BlockItem -> InteractionHand.OFF_HAND
        else -> null
    }
}
