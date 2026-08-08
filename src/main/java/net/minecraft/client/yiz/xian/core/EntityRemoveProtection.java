package net.minecraft.client.yiz.xian.core;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体移除保护白名单（1.20.1 移植版）。
 *
 * <p>配合 ServerLevel 层移除保护（后续 mixin 落地）：ServerLevel 默认拒绝移除本模组实体
 * （YizxianMob），仅白名单放行。实体生命值 ≤0 时由 YizxianMob 在 aiStep 标记，原版死亡移除据此放行。</p>
 */
public final class EntityRemoveProtection {

    private static final Set<UUID> DEATH_ALLOW = ConcurrentHashMap.newKeySet();

    private EntityRemoveProtection() {}

    /** 死亡放行标记：实体生命值 ≤0（本模组死亡监听调用，幂等）。 */
    public static void allowDeathRemove(UUID uuid) {
        if (uuid != null) DEATH_ALLOW.add(uuid);
    }

    /** 消费一次死亡放行（removeEntity 保护放行后清理，防残留）。 */
    public static boolean consumeDeathAllow(UUID uuid) {
        return uuid != null && DEATH_ALLOW.remove(uuid);
    }
}
