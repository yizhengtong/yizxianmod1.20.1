package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * 固定药水类型的药水物品。
 *
 * <p>原版 {@code PotionItem} 的默认栈没有 {@code Potion} NBT；这里用 supplier 延迟取药水，
 * 在默认实例（创造页）和背包 tick（/give）时补上，避免注册顺序问题。</p>
 */
public class LegacyPotionItem extends PotionItem {

    private final Supplier<Potion> potion;

    public LegacyPotionItem(Supplier<Potion> potion, Properties properties) {
        super(properties);
        this.potion = potion;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this);
        Potion value = potion.get();
        if (value != null) {
            PotionUtils.setPotion(stack, value);
        }
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide) {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("Potion")) {
                Potion value = potion.get();
                if (value != null) {
                    PotionUtils.setPotion(stack, value);
                }
            }
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }
}
