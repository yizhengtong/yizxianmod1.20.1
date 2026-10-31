package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.Map;

/**
 * 1.8.9 钻石盔甲材料。
 *
 * <p>数值严格按 1.8.9：护甲 3/8/6/3、耐久 363/528/495/429、附魔性 10、韧性 0。
 * 韧性为 0 是 1.8 的真实状态；但 1.20.1 的护甲公式即使韧性 0 也会随伤害变大而降低减免，
 * 所以真正的「固定 4%/点、上限 80%」由 {@code LegacyArmorMixin} 接管，不依赖原版公式。</p>
 */
public final class LegacyArmorMaterial implements ArmorMaterial {

    public static final LegacyArmorMaterial INSTANCE = new LegacyArmorMaterial();

    private static final Map<ArmorItem.Type, Integer> DEFENSE = new EnumMap<>(ArmorItem.Type.class);
    private static final Map<ArmorItem.Type, Integer> DURABILITY = new EnumMap<>(ArmorItem.Type.class);

    static {
        DEFENSE.put(ArmorItem.Type.HELMET, 3);
        DEFENSE.put(ArmorItem.Type.CHESTPLATE, 8);
        DEFENSE.put(ArmorItem.Type.LEGGINGS, 6);
        DEFENSE.put(ArmorItem.Type.BOOTS, 3);

        DURABILITY.put(ArmorItem.Type.HELMET, 363);
        DURABILITY.put(ArmorItem.Type.CHESTPLATE, 528);
        DURABILITY.put(ArmorItem.Type.LEGGINGS, 495);
        DURABILITY.put(ArmorItem.Type.BOOTS, 429);
    }

    private LegacyArmorMaterial() {
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return DURABILITY.get(type);
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return DEFENSE.get(type);
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
        // Forge 的 HumanoidArmorLayer#getArmorResource 会把 "命名空间:名称" 拆开：
        // 不带冒号时默认落到 minecraft 命名空间，会去找
        // minecraft:textures/models/armor/legacy_diamond_layer_1.png（不存在）→ 紫黑缺失纹理。
        // 因此这里必须带本模组命名空间。
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
