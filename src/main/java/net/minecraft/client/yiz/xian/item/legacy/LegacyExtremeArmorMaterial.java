package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 极尽钻石甲材料：每件比普通套多 1 点护甲。
 *
 * <p>普通 3/8/6/3 → 极尽 4/9/7/4，四件合计 24。其余（耐久底值、韧性 0、
 * 附魔性 10、贴图名）与普通套一致；耐久 ×10 由 {@link LegacyExtremeArmorItem} 覆写。</p>
 */
public final class LegacyExtremeArmorMaterial implements ArmorMaterial {

    public static final LegacyExtremeArmorMaterial INSTANCE = new LegacyExtremeArmorMaterial();

    private LegacyExtremeArmorMaterial() {
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 363;
            case CHESTPLATE -> 528;
            case LEGGINGS -> 495;
            case BOOTS -> 429;
        };
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 4;
            case CHESTPLATE -> 9;
            case LEGGINGS -> 7;
            case BOOTS -> 4;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_DIAMOND;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.DIAMOND);
    }

    @Override
    public String getName() {
        // 与普通套共用盔甲层贴图。
        return "yizxianmod:legacy_diamond";
    }

    @Override
    public float getToughness() {
        return 0.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0F;
    }
}
