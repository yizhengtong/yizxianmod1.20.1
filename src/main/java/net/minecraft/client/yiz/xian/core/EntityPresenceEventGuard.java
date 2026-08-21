package net.minecraft.client.yiz.xian.core;

import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 加入事件反否决 —— 新增闸门的第二道保险。
 *
 * <p>实体加入世界的事件是可取消的，外部只要以高优先级取消它，本模组实体就再也进不了世界：
 * 既回不来，也召唤不出。这里以<b>最低优先级</b>收尾（此时所有监听方都已表态），
 * 并显式接收已被取消的事件，把本模组实体的取消状态改回放行。</p>
 *
 * <p>与入口处的抢先闸门是两层独立防线：抢先闸门覆盖直接在入口返回失败的做法，
 * 本监听覆盖通过取消事件间接否决的做法。只对本模组实体生效。</p>
 *
 * <p><b>两端都要管</b>：客户端把实体放进世界同样会广播这个可取消事件，
 * 被取消时实体进不了客户端世界 —— 服务端再完好，玩家也看不见、打不着。</p>
 */
public final class EntityPresenceEventGuard {

    private EntityPresenceEventGuard() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof YizxianMob mob)) return;
        if (!event.isCanceled()) return;
        // 权威血量已归零的实体走正常死亡清理，不强行留在世界里
        if (RemovalGateAuth.isDeadByAuthority(mob)) return;
        event.setCanceled(false);
    }
}
