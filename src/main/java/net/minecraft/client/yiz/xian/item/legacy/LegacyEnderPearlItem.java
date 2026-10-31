package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 1.8.9 末影珍珠。
 *
 * <p>1.8：堆叠 16、投掷传送并造成 5 点伤害、<b>没有冷却</b>（1.9 15w34c 才加入 1 秒冷却）。
 * 原版 {@link EnderpearlItem} 在 {@code use} 里会立刻加 20 tick 冷却，所以这里重写 {@code use}，
 * 按 1.8 逻辑生成投掷物且不注册冷却。</p>
 *
 * <p>投掷初速由 {@link #getThrowSpeed()} 决定，「极尽」变体会覆写成 3.0（原版 1.5）。</p>
 */
public class LegacyEnderPearlItem extends EnderpearlItem {

    public LegacyEnderPearlItem(Properties properties) {
        super(properties);
    }

    /** 1.8 原版投掷初速。 */
    protected float getThrowSpeed() {
        return 1.5F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.5F,
            0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            ThrownEnderpearl pearl = new ThrownEnderpearl(level, player);
            pearl.setItem(stack);
            pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                getThrowSpeed(), 1.0F);
            level.addFreshEntity(pearl);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        player.swing(hand, true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
