package net.minecraft.client.yiz.xian.item.curio;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 心之钢 —— 胸饰槽（{@code curios:body}，见 {@code data/curios/tags/items/body.json}）。
 *
 * <p>占位数值：+6 最大生命、+2 护甲。改数就直接改下面的 {@link CurioAttribute} 列表。
 * 想要「叠层增伤」之类机制，在 {@link #onSlowTick} 里累积 ItemStack 的 NBT（用
 * {@code stack.getOrCreateTag()}），在 {@link #onUnequipped} 里清干净。</p>
 */
public class HeartsteelItem extends YizxianCurioItem {

    public HeartsteelItem(Item.Properties properties) {
        super(properties.rarity(Rarity.RARE),
            CurioAttribute.add(Attributes.MAX_HEALTH, 6.0D, "heartsteel_max_health"),
            CurioAttribute.add(Attributes.ARMOR, 2.0D, "heartsteel_armor"));
    }

    // TODO 心之钢：叠层机制（受击/命中叠层 → 每层转换为攻击力或最大生命）在此实现。
    //  建议叠层数存 ItemStack NBT，属性本身通过 getAttributeModifiers 动态计算
    //  （覆写 getAttributeModifiers，把 base + 层数 × 每层值 作为 amount 返回）。
}
