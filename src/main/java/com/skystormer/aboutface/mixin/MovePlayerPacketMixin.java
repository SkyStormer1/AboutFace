package com.skystormer.aboutface.mixin;

import com.skystormer.aboutface.Claims;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the claimed rotation on movement packets as they are built.
 *
 * Telling the server where to look once is not enough to keep it looking there. The client reports
 * the player's real rotation whenever it changes, so a claim made on one tick is contradicted on
 * the next — and a horizontal facing needs the claim to survive at least that long, because yaw
 * only takes effect on the server after the player entity ticks.
 *
 * Packets carrying no rotation are left alone, as is anything built off the client thread, which is
 * not this mod's business to rewrite.
 */
@Mixin(ServerboundMovePlayerPacket.class)
public abstract class MovePlayerPacketMixin {

    @Shadow
    @Final
    private boolean hasRot;

    @Shadow
    @Final
    @Mutable
    private float yRot;

    @Shadow
    @Final
    @Mutable
    private float xRot;

    @Inject(method = "<init>(DDDFFZZZZ)V", at = @At("RETURN"))
    private void aboutFace$applyClaimedRotation(
            double x, double y, double z,
            float yRot, float xRot,
            boolean onGround, boolean horizontalCollision,
            boolean hasPos, boolean hasRot,
            CallbackInfo ci
    ) {
        if (!this.hasRot) {
            return;
        }
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }

        Claims.Rotation claimed = Claims.rotation;
        if (claimed != null) {
            this.yRot = claimed.getYaw();
            this.xRot = claimed.getPitch();
        }
    }
}
