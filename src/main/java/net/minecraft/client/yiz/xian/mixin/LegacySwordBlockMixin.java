package net.minecraft.client.yiz.xian.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.yiz.xian.item.legacy.LegacyDiamondSwordItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.8.9 剑格挡的第一人称姿势。
 *
 * <p>1.20.1 的 {@code UseAnim.BLOCK} 分支只调用 {@code applyItemArmTransform}，
 * 物品仍按普通持握变换渲染，所以第一人称看不到格挡动作。这里在 HEAD 取消原渲染，
 * 按 15w33b（1.9 移除剑格挡前的最后一个版本）的变换重建：</p>
 *
 * <pre>
 * translate(dir * -0.14142136, 0.08, 0.14142136)
 * rotate X -102.25°, Y dir*13.365°, Z dir*78.05°
 * </pre>
 *
 * <p>第三视角不需要额外处理：1.8 的 {@code heldItemRight=3} 角度为
 * {@code xRot = base*0.5 - 3π/10}、{@code yRot = -π/6}，与 1.20.1
 * {@code ArmPose.BLOCK} 完全一致，原版 {@code PlayerRenderer} 已经会走该分支。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class LegacySwordBlockMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void yizxianmod$legacySwordBlock(AbstractClientPlayer player, float partialTick, float pitch,
                                             InteractionHand hand, float swingProgress, ItemStack stack,
                                             float equipProgress, PoseStack poseStack,
                                             MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!(stack.getItem() instanceof LegacyDiamondSwordItem)) {
            return;
        }
        if (stack.getUseAnimation() != UseAnim.BLOCK) {
            return;
        }
        if (!player.isUsingItem() || player.getUsedItemHand() != hand) {
            return;
        }
        if (player.getUseItem().getItem() != stack.getItem()) {
            return;
        }

        boolean mainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        int dir = arm == HumanoidArm.RIGHT ? 1 : -1;

        poseStack.pushPose();
        // 与 1.8 一致：先应用手臂基础位置（含装备抬起进度），再叠加剑格挡的旋转。
        // 等价于原版 applyItemArmTransform（private，避免 shadow 在 SRG 环境下的映射问题）：
        poseStack.translate(dir * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
        poseStack.translate(dir * -0.14142136F, 0.08F, 0.14142136F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
        poseStack.mulPose(Axis.YP.rotationDegrees(dir * 13.365F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(dir * 78.05F));

        ((ItemInHandRenderer) (Object) this).renderItem(
            player, stack,
            mainHand ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            dir == -1, poseStack, buffer, light);

        poseStack.popPose();
        ci.cancel();
    }
}
