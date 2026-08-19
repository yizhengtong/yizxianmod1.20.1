package net.minecraft.client.yiz.xian.core;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 实体移除保护白名单（1.20.1 移植版）。
 *
 * <p>配合 ServerLevel 层移除保护（后续 mixin 落地）：ServerLevel 默认拒绝移除本模组实体
 * （YizxianMob），仅白名单放行。实体生命值 ≤0 时由 YizxianMob 在 aiStep 标记，原版死亡移除据此放行。</p>
 */
public final class EntityRemoveProtection {

    private static final Set<UUID> DEATH_ALLOW = ConcurrentHashMap.newKeySet();
    private static volatile Field IS_SAVING_FIELD;

    private EntityRemoveProtection() {}

    /**
     * 停机/存档放行：服务器停机、已停止、或正处于 {@code isSaving}（save-and-quit 的
     * stopServer 阶段）时必须放行原版实体拆卸。否则移除保护会 cancel 掉 ServerLevel.close()
     * 的实体移除，而完整性守卫又在保存期间回填实体，双向夹击破坏存档写出。
     */
    public static boolean isShuttingDownOrSaving(ServerLevel level) {
        if (level == null) return true;
        MinecraftServer server = level.getServer();
        if (server == null) return true;
        if (!server.isRunning() || server.isStopped()) return true;
        try {
            if (IS_SAVING_FIELD == null) {
                IS_SAVING_FIELD = findField(MinecraftServer.class, "isSaving", "f_195494_");
                if (IS_SAVING_FIELD != null) IS_SAVING_FIELD.setAccessible(true);
            }
            return IS_SAVING_FIELD != null && IS_SAVING_FIELD.getBoolean(server);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Field findField(Class<?> owner, String official, String srg) {
        try {
            return owner.getDeclaredField(official);
        } catch (NoSuchFieldException e1) {
            try {
                return owner.getDeclaredField(srg);
            } catch (NoSuchFieldException e2) {
                return null;
            }
        }
    }

    /** 死亡放行标记：实体生命值 ≤0（本模组死亡监听调用，幂等）。 */
    public static void allowDeathRemove(UUID uuid) {
        if (uuid != null) DEATH_ALLOW.add(uuid);
    }

    /** 消费一次死亡放行（removeEntity 保护放行后清理，防残留）。 */
    public static boolean consumeDeathAllow(UUID uuid) {
        return uuid != null && DEATH_ALLOW.remove(uuid);
    }

    /** 撤销死亡放行（实体血量恢复/复活时调用，防残留标记被外部利用移除活实体）。 */
    public static void revokeDeathAllow(UUID uuid) {
        if (uuid != null) DEATH_ALLOW.remove(uuid);
    }
}
