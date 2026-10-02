package net.minecraft.client.yiz.xian.item.curio;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 辖界的权柄 —— <b>自定义槽位</b>「遗物」{@code yizxianmod:relic}。
 *
 * <p>这是本模组自建槽位的样板：槽位定义在
 * {@code data/yizxianmod/curios/slots/relic.json}，物品绑定在
 * {@code data/curios/tags/items/relic.json}（注意标签命名空间是 {@code curios}），
 * 槽位名走语言键 {@code curios.identifier.relic}。
 * 槽位标识就是槽位 JSON 的文件名（{@code relic}），Curios 会丢掉命名空间。</p>
 *
 * <p>占位数值：+2 攻击伤害。</p>
 */
public class BoundaryAuthorityItem extends YizxianCurioItem {

    public BoundaryAuthorityItem(Item.Properties properties) {
        super(properties.rarity(Rarity.EPIC),
            CurioAttribute.add(Attributes.ATTACK_DAMAGE, 2.0D, "boundary_authority_attack_damage"));
        // 想用前置库 yizmodqzk 的自定义属性（例如涨跌多空），换成：
        // CurioAttribute.add(net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM.get(), 10.0D, "boundary_authority_first_dream")
    }

    // TODO 辖界的权柄：作为「权柄」类饰品的核心机制（例如给辖界者系生物加成、
    //  或按佩戴者的涨跌多空属性放大效果）在 onSlowTick / getAttributeModifiers 里实现。
}
