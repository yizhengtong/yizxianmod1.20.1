package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * 1.8.9 钻石剑。
 *
 * <ul>
 *   <li>伤害 8（1.8 的「+7 攻击伤害」叠加空手 1 点；1.20.1 口径为 1 + 3 + 4）</li>
 *   <li>耐久 1562（1.8 的 off-by-one 值，1.20.1 原版为 1561）</li>
 *   <li>无攻击冷却：内置 {@link LegacyAttackSpeedItem}，不再挂 COOLDOWN_REDUCTION 属性</li>
 *   <li>无横扫：{@link ToolActions#SWORD_SWEEP} 是 1.20.1 {@code Player#attack} 的横扫开关，直接不暴露</li>
 *   <li>格挡：严格 1.8——按住右键格挡 50%，不手持常驻、不切换 75%</li>
 * </ul>
 */
public class LegacyDiamondSwordItem extends SwordItem implements LegacyAttackSpeedItem {

    /** 玩家持久数据：true = 75% 强化格挡，false/缺失 = 50% 格挡。 */
    public static final String GUARD_TAG = "yizxianmod.legacy_sword_guard";

    public LegacyDiamondSwordItem(Item.Properties properties) {
        // 1.8 钻石剑面板 7：Tier 自带 +3、基础 +1，这里填 3 才是 7（填 4 会显示 8）。
        super(Tiers.DIAMOND, 3, -2.4F, properties);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return 1562;
    }

    @Override
    public float attackSpeedDivisor() {
        return 101.0F;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        // 1.8 没有横扫；只要不暴露 SWORD_SWEEP，Player#attack 就不会触发横扫。
        return toolAction != ToolActions.SWORD_SWEEP && super.canPerformAction(stack, toolAction);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 1.8 原版：按住右键进入格挡，松手结束；没有“常驻 50%”或“右键切 75%”。
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}
