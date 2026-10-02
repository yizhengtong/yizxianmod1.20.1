package net.minecraft.client.yiz.xian.item.curio;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 轰然之心 —— 护符槽（{@code curios:charm}，见 {@code data/curios/tags/items/charm.json}）。
 *
 * <p>占位数值：+3 最大生命、+10% 击退抗性。击退抗性取值范围 0~1（1 = 完全免疫击退）。</p>
 */
public class RumblingHeartItem extends YizxianCurioItem {

    public RumblingHeartItem(Item.Properties properties) {
        super(properties.rarity(Rarity.RARE),
            CurioAttribute.add(Attributes.MAX_HEALTH, 3.0D, "rumbling_heart_max_health"),
            CurioAttribute.add(Attributes.KNOCKBACK_RESISTANCE, 0.1D, "rumbling_heart_knockback_resistance"));
    }

    // TODO 轰然之心：血量低于阈值时触发一次「轰然」（范围击退/震慑）—— 在 onSlowTick 里查
    //  wearer.getHealth() / wearer.getMaxHealth()，配合冷却（存 NBT 或玩家持久数据）来触发。
}
