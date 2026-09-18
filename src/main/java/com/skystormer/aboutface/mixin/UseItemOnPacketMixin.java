package com.skystormer.aboutface.mixin;

import com.skystormer.aboutface.Claims;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the claimed hit on placements as they are built.
 *
 * Rotation cannot reach every block. A hopper takes its facing from the face that was clicked and
 * ignores the look direction completely, so turning one around means claiming a different click
 * rather than a different rotation. The claim is chosen so the block still lands where the player
 * was pointing; this only carries it as far as the packet.
 */
@Mixin(ServerboundUseItemOnPacket.class)
public abstract class UseItemOnPacketMixin {

    @Shadow
    @Final
    @Mutable
    private BlockHitResult blockHit;

    @Inject(method = "<init>(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;I)V", at = @At("RETURN"))
    private void aboutFace$applyClaimedHit(InteractionHand hand, BlockHitResult hit, int sequence, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }

        BlockHitResult claimed = Claims.hit;
        if (claimed != null) {
            this.blockHit = claimed;
        }
    }
}
