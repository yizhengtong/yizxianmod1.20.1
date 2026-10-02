package net.minecraft.client.yiz.xian.item.curio;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 辖界之眼 —— 项链槽（{@code curios:necklace}，见 {@code data/curios/tags/items/necklace.json}）。
 *
 * <p>纯功能型饰品（不带属性），演示「持续效果」怎么写：下面 {@link #onSlowTick} 里留了一段
 * 可解锁的夜视示例。Curios 只会分发钩子，效果本身要自己给。</p>
 */
public class BoundaryEyeItem extends YizxianCurioItem {

    public BoundaryEyeItem(Item.Properties properties) {
        super(properties.rarity(Rarity.EPIC));
    }

    @Override
    protected void onEquipped(SlotContext slotContext, ItemStack stack) {
        // TODO 辖界之眼：戴上时的即时效果（例如解锁视野/播放入耳音效/写 NBT 标记）写这里。
    }

    @Override
    protected void onUnequipped(SlotContext slotContext, ItemStack stack) {
        // TODO 辖界之眼：卸下时把 onEquipped/onSlowTick 加的东西清掉，否则会残留到玩家身上。
    }

    @Override
    protected void onSlowTick(SlotContext slotContext, ItemStack stack, LivingEntity wearer) {
        // 持续效果示例（默认注释掉，需要时放开）：
        // wearer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
        //     net.minecraft.world.effect.MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        // 说明：持续时间给 300 tick（15 秒）而每 20 tick 刷一次，是为了让「卸下后自然过期」，
        // 不必在 onUnequipped 里手动 removeEffect（手动移除容易误删玩家自己喝药水的效果）。
    }
}
