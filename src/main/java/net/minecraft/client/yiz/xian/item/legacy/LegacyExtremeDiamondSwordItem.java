package net.minecraft.client.yiz.xian.item.legacy;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 怀旧钻石剑——极尽：继承 1.8 剑全部行为，耐久 ×10 + Unbreakable，
 * 自带 1 点 {@link YizAttributes#AUTO_ATTACK}，并且是唯一带「常驻 50% / 右键 75% 格挡」的剑。
 */
public class LegacyExtremeDiamondSwordItem extends LegacyDiamondSwordItem implements LegacyExtremeItem {

    private static final UUID AUTO_ATTACK_UUID =
        UUID.fromString("6f2a1c3e-8b4d-4e2a-9c71-000000000004");

    public LegacyExtremeDiamondSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return 15620;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(super.getDefaultAttributeModifiers(slot));
        builder.put(YizAttributes.AUTO_ATTACK.get(),
            new AttributeModifier(AUTO_ATTACK_UUID, "yizxianmod:legacy_auto_attack", 1.0D,
                AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 极尽专属：右键切换 50% ↔ 75% 格挡；普通怀旧剑没有这个逻辑。
        if (!level.isClientSide) {
            CompoundTag data = player.getPersistentData();
            data.putBoolean(GUARD_TAG, !data.getBoolean(GUARD_TAG));
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}
