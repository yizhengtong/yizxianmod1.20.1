package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 极尽镐的内置挖掘速度：主手物品实现 {@link LegacyMiningSpeedItem} 时，
 * 在 {@code BreakSpeed} 最终值上直接乘倍率（+200% = ×3）。
 *
 * <p>不再使用 {@code MINING_EFFICIENCY} 属性，避免属性面板显示/被其它系统二次计算。</p>
 */
public final class LegacyMiningSpeedEvents {

    private LegacyMiningSpeedEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof LegacyMiningSpeedItem item) {
            event.setNewSpeed(event.getNewSpeed() * item.miningSpeedMultiplier());
        }
    }
}
