package net.minecraft.client.yiz.xian.item;

import net.minecraft.client.yiz.core.ItemStackSizeOverride;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 堆叠核心的铁砧强化处理（1.20.1 移植版）。
 *
 * <p>交互：铁砧<b>左槽</b>放目标物品、<b>右槽</b>放堆叠核心 → 取出后该物品 ID 的最大堆叠数 ×2
 * （封顶 99）。同一物品 ID 最多强化 {@link ItemStackSizeOverride#MAX_ENHANCE} 次。</p>
 *
 * <h3>两个事件</h3>
 * <ul>
 *   <li>{@link #onAnvilUpdate} — 在 {@code AnvilMenu#createResult()} 触发，计算输出槽<b>预览</b>。
 *       右槽是堆叠核心时，输出 = 左槽副本（身份/NBT 不变），消耗 1 个堆叠核心、不耗经验。
 *       <b>不在此改堆叠表</b>（玩家可能取消）。</li>
 *   <li>{@link #onAnvilRepair} — 玩家取出物品时触发，此时才真正写入堆叠表 + 累加强化次数。</li>
 * </ul>
 */
public final class StackCoreAnvilHandler {

    private StackCoreAnvilHandler() {}

    private static boolean isStackCore(ItemStack right) {
        return !right.isEmpty() && right.getItem() == YizxianMod.STACK_CORE.get();
    }

    private static int currentMax(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        int override = ItemStackSizeOverride.getOverride(id);
        return override > 0 ? override : item.getMaxStackSize();
    }

    private static int enhancedMax(int current) {
        return Math.min(ItemStackSizeOverride.MAX, current * 2);
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!isStackCore(right) || left.isEmpty()) return;

        Item target = left.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(target);

        if (!ItemStackSizeOverride.canEnhance(id)) return;
        if (currentMax(target) >= ItemStackSizeOverride.MAX) return;

        ItemStack output = left.copy();
        event.setOutput(output);
        event.setMaterialCost(1);
        event.setCost(1);
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        // 仅在服务端执行——单机 IntegratedServer 下此事件会在 Client+Server 各触发一次
        if (event.getEntity().level().isClientSide()) return;

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!isStackCore(right) || left.isEmpty()) return;

        Item target = left.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(target);
        if (!ItemStackSizeOverride.canEnhance(id)) return;
        int current = currentMax(target);
        if (current >= ItemStackSizeOverride.MAX) return;

        int newSize = enhancedMax(current);
        ItemStackSizeOverride.set(id, newSize);
        ItemStackSizeOverride.incrementEnhanceCount(id);
    }
}
