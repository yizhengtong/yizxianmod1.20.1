package net.minecraft.client.yiz.xian.item.legacy;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 怀旧钻石盔甲——极尽：耐久 ×10 + Unbreakable，并额外提供
 * 每件 +0.25 前置库击退免疫、+0.25 无碰撞（四件合计 1.0）。
 *
 * <p>普通 {@link LegacyArmorItem} 不带这些额外属性，保持 1.8.9 原样。</p>
 */
public class LegacyExtremeArmorItem extends LegacyArmorItem implements LegacyExtremeItem {

    private static final Map<Type, UUID> KB_UUIDS = new EnumMap<>(Type.class);
    private static final Map<Type, UUID> NC_UUIDS = new EnumMap<>(Type.class);

    static {
        for (Type type : Type.values()) {
            KB_UUIDS.put(type, UUID.nameUUIDFromBytes(
                ("yizxianmod:legacy_kb_" + type.name()).getBytes(StandardCharsets.UTF_8)));
            NC_UUIDS.put(type, UUID.nameUUIDFromBytes(
                ("yizxianmod:legacy_nc_" + type.name()).getBytes(StandardCharsets.UTF_8)));
        }
    }

    public LegacyExtremeArmorItem(ArmorMaterial material, Type type, Item.Properties properties) {
        super(material, type, properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return switch (getType()) {
            case HELMET -> 3630;
            case CHESTPLATE -> 5280;
            case LEGGINGS -> 4950;
            case BOOTS -> 4290;
        };
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != getType().getSlot()) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(super.getDefaultAttributeModifiers(slot));
        builder.put(YizAttributes.KNOCKBACK_IMMUNITY.get(),
            new AttributeModifier(KB_UUIDS.get(getType()), "yizxianmod:legacy_knockback_immunity",
                0.25D, AttributeModifier.Operation.ADDITION));
        builder.put(YizAttributes.NO_COLLISION.get(),
            new AttributeModifier(NC_UUIDS.get(getType()), "yizxianmod:legacy_no_collision",
                0.25D, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }
}
