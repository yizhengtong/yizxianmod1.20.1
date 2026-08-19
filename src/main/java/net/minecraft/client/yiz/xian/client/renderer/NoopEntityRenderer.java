package net.minecraft.client.yiz.xian.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 空渲染器：弹道实体（火球/陨石/毒云）本体不可见，靠实体 clientTick 里 spawn 的 vanilla 粒子显示。
 * 仅用于满足渲染分发（不注册渲染器会 NPE），render 空实现。
 */
public class NoopEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final ResourceLocation DUMMY = new ResourceLocation("minecraft", "textures/misc/white.png");

    public NoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return DUMMY;
    }
}
