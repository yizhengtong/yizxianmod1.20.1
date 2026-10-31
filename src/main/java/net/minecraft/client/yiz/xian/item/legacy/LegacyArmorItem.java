package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

/**
 * 普通怀旧钻石甲单件：严格 1.8.9，只有护甲/韧性/耐久，没有任何额外属性。
 * 「极尽」变体在 {@link LegacyExtremeArmorItem} 里额外加击退抗性/无碰撞/耐久 ×10。
 */
public class LegacyArmorItem extends ArmorItem {

    public LegacyArmorItem(ArmorMaterial material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }
}
