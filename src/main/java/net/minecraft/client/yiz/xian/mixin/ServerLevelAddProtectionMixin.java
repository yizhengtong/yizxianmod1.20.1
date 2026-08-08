package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ServerLevel 实体新增保护（1.20.1 移植版）— 本模组实体新增闸门（配合 EntityRemoveProtectionMixin）。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelAddProtectionMixin {

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectAdd(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof YizxianMob)) return;
        if (shouldAllowAdd(entity)) return;
        cir.setReturnValue(false);
    }

    @Inject(method = "addDuringTeleport(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true)
    private void yizxianmod$protectAddDuringTeleport(Entity entity, CallbackInfo ci) {
        if (!(entity instanceof YizxianMob)) return;
        ci.cancel();
    }

    private boolean shouldAllowAdd(Entity entity) {
        if (isYizCaller()) return true;
        return isEngineRestore();
    }

    private static boolean isYizCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("net.minecraft.world.level.ServerLevel")) continue;
            if (cn.startsWith("net.minecraft.client.yiz")) return true;
            return false;
        }
        return false;
    }

    private static boolean isEngineRestore() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.equals("net.minecraft.world.level.ServerLevel")) continue;
            if (cn.startsWith("net.minecraft.")
                    || cn.startsWith("net.minecraftforge.")
                    || cn.startsWith("com.mojang.")) {
                return true;
            }
            return false;
        }
        return false;
    }
}
