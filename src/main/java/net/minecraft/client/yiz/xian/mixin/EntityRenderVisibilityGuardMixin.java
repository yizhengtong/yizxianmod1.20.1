package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 渲染可见性抢先判定 —— 本模组实体是否渲染由渲染器自己说了算。
 *
 * <p>可见性判定是可被外部前置否决的：一旦被判为不渲染，实体在服务端再完好，玩家也看不见。
 * 这里以最高优先级注入最前端，直接用该实体渲染器给出的<b>原版判定结果</b>作为返回值并终止回调链，
 * 结果与不受干预时完全一致，只是不再接受后续否决。</p>
 */
@Mixin(value = EntityRenderDispatcher.class, priority = Integer.MAX_VALUE)
public abstract class EntityRenderVisibilityGuardMixin {

    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void yizxianmod$guardVisibility(E entity, Frustum frustum, double camX, double camY,
                                                               double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof YizxianMob)) return;
        try {
            EntityRenderer<? super E> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(entity);
            if (renderer != null) {
                cir.setReturnValue(renderer.shouldRender(entity, frustum, camX, camY, camZ));
            }
        } catch (Throwable ignored) {
            // 判定失败时交回原版流程，不影响正常渲染
        }
    }
}
