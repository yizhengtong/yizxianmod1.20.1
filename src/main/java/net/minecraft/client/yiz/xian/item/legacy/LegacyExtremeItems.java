package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「极尽」物品的公共逻辑。
 *
 * <p>极尽装备 = 耐久 ×10 + {@code Unbreakable:1b} 标签。标签通过三条路径保证：</p>
 * <ul>
 *   <li>{@code Item#getDefaultInstance} / {@code #onCraftedBy} / {@code #inventoryTick}
 *       由 {@code LegacyExtremeItemMixin} 统一补；</li>
 *   <li>非玩家实体（僵尸、骷髅、自定义生物）的装备槽不会走玩家背包 tick，
 *       因此额外用 {@link LivingEvent.LivingTickEvent} 每 20 tick 扫一次装备槽；</li>
 *   <li>标签一旦存在就不再写，避免每 tick 制造 NBT 同步。</li>
 * </ul>
 */
public final class LegacyExtremeItems {

    public static final String TAG_UNBREAKABLE = "Unbreakable";

    private LegacyExtremeItems() {
    }

    /** 给极尽物品补 Unbreakable 标签；非极尽物品直接跳过。 */
    public static void mark(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof LegacyExtremeItem)) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.getBoolean(TAG_UNBREAKABLE)) {
            return;
        }
        stack.getOrCreateTag().putBoolean(TAG_UNBREAKABLE, true);
    }

    /** 任意实体的装备槽兜底（每 20 tick 一次，只在服务端改 NBT）。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity.tickCount % 20 != 0) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mark(entity.getItemBySlot(slot));
        }
    }
}
