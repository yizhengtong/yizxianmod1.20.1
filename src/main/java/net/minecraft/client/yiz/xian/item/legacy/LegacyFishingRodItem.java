package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 1.8.9 钓鱼竿：65 耐久；右键抛竿/收竿。
 * 鱼饵命中实体 → {@link LegacyFishingHook} 向外击退；收竿 → 原版 pullEntity 拉回。
 */
public class LegacyFishingRodItem extends FishingRodItem {

    public LegacyFishingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.fishing != null) {
            if (!level.isClientSide) {
                int damage = player.fishing.retrieve(stack);
                stack.hurtAndBreak(damage, player, p -> p.broadcastBreakEvent(hand));
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                1.0F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            if (!level.isClientSide) {
                int lure = EnchantmentHelper.getFishingSpeedBonus(stack);
                int luck = EnchantmentHelper.getFishingLuckBonus(stack);
                level.addFreshEntity(new LegacyFishingHook(player, level, luck, lure));
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            player.gameEvent(GameEvent.ITEM_INTERACT_START);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
