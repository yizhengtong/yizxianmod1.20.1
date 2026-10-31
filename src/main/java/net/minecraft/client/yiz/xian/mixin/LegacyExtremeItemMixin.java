package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.yiz.xian.item.legacy.LegacyExtremeItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给所有 {@code LegacyExtremeItem} 自动补 {@code Unbreakable} 标签：
 * 默认实例（创造页/物品展示）、合成产出、背包 tick。任意实体的装备槽兜底在
 * {@link LegacyExtremeItems#onLivingTick}。
 */
@Mixin(Item.class)
public abstract class LegacyExtremeItemMixin {

    @Inject(method = "getDefaultInstance", at = @At("RETURN"))
    private void yizxianmod$markDefault(CallbackInfoReturnable<ItemStack> cir) {
        LegacyExtremeItems.mark(cir.getReturnValue());
    }

    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void yizxianmod$markCrafted(ItemStack stack, Level level, Player player, CallbackInfo ci) {
        LegacyExtremeItems.mark(stack);
    }

    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void yizxianmod$markInventory(ItemStack stack, Level level, Entity entity, int slot,
                                          boolean selected, CallbackInfo ci) {
        if (!level.isClientSide) {
            LegacyExtremeItems.mark(stack);
        }
    }
}
