package net.minecraft.client.yiz.xian.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.TransientEntitySectionManager;

/**
 * 客户端存在性守卫 —— 保证本模组实体能进入客户端世界。
 *
 * <p>服务端实体完好、但客户端拒绝把它放进世界时，玩家看不见也打不着，观感与被删除无异。
 * 客户端的加入入口同样可以被外部在前端否决，这里提供一份等价的加入实现供闸门抢先调用。</p>
 *
 * <p>存储字段按类型形状定位，不依赖字段名与映射名；定位失败时安全降级为空操作。</p>
 */
public final class ClientPresenceGuard {

    private static volatile boolean READY;
    private static Field ENTITY_STORAGE;

    private ClientPresenceGuard() {}

    /** 按原版语义把实体放进客户端世界；成功返回 true。 */
    public static boolean forceAdd(ClientLevel level, int id, Entity entity) {
        if (level == null || entity == null) return false;
        ensureFields();
        if (ENTITY_STORAGE == null) return false;
        try {
            if (level.getEntity(id) == entity) return true;
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
            Object storage = ENTITY_STORAGE.get(level);
            if (!(storage instanceof TransientEntitySectionManager<?> sectionManager)) return false;
            @SuppressWarnings("unchecked")
            TransientEntitySectionManager<Entity> typed = (TransientEntitySectionManager<Entity>) sectionManager;
            typed.addEntity(entity);
            entity.onAddedToWorld();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureFields() {
        if (READY) return;
        synchronized (ClientPresenceGuard.class) {
            if (READY) return;
            try {
                for (Field f : ClientLevel.class.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType() != TransientEntitySectionManager.class) continue;
                    f.setAccessible(true);
                    ENTITY_STORAGE = f;
                    break;
                }
            } catch (Throwable ignored) {
            } finally {
                READY = true;
            }
        }
    }
}
