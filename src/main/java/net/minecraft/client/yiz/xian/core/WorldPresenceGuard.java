package net.minecraft.client.yiz.xian.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * 世界存在性守卫 —— 保证受保护实体在世界内部结构中的存在不被外力否决。
 *
 * <p>解决两类攻击面，二者都不经过 {@code Entity.setRemoved} / {@code ServerChunkCache.removeEntity}，
 * 因此方法级闸门天然拦不住：</p>
 * <ol>
 *   <li><b>结构直删</b>：反射清 {@code EntityLookup.byId/byUuid}、{@code EntityTickList}、
 *       {@code EntitySection}、{@code knownUuids}、{@code ChunkMap} 中的实体。
 *       对策见 {@link #repair}：按缺失项精准回填，不调用 {@code ServerLevel.addFreshEntity}，
 *       绕开全部新增入口，因此任何对新增路径的封锁都影响不到自愈。</li>
 *   <li><b>新增封锁</b>：在实体加入世界的入口处否决（返回 false / 取消加入事件），
 *       使被清除的实体再也回不来、同类实体也生成不了。
 *       对策见 {@link #forceAdd}：在入口最前端抢先完成加入并终止回调链，
 *       排在后面的否决没有执行机会。</li>
 * </ol>
 *
 * <p>所有内部字段一律按<b>类型形状</b>定位（某类中类型唯一的字段），不依赖字段名、映射名，
 * 也不依赖任何具体实现。定位失败时全部方法安全降级为空操作。</p>
 */
public final class WorldPresenceGuard {

    private static volatile boolean FIELDS_READY;

    /** PersistentEntitySectionManager 的四个内部结构。 */
    private static Field PESM_KNOWN_UUIDS;      // Set<UUID>
    private static Field PESM_VISIBLE_STORAGE;  // EntityLookup
    private static Field PESM_SECTION_STORAGE;  // EntitySectionStorage
    private static Field PESM_CALLBACKS;        // LevelCallback

    /** EntityLookup 的两张索引表。 */
    private static Field LOOKUP_BY_ID;          // Int2ObjectMap
    private static Field LOOKUP_BY_UUID;        // Map

    /** ServerLevel 的两个实体容器。 */
    private static Field LEVEL_ENTITY_MANAGER;  // PersistentEntitySectionManager
    private static Field LEVEL_TICK_LIST;       // EntityTickList

    /** 区块跟踪表（按 int 键的 Map），用于判断实体是否仍被跟踪。 */
    private static Field CHUNK_MAP_FIELD;
    private static Field CHUNK_MAP_TRACKED;

    /** 原版挂在实体上的 section 回调（PersistentEntitySectionManager 的内部类）构造器。 */
    private static Constructor<?> SECTION_CALLBACK_CTOR;

    private WorldPresenceGuard() {}

    // ------------------------------------------------------------------ 入口

    /**
     * 新增入口抢先完成：把实体按原版完整流程加入世界，成功返回 true。
     *
     * <p>由新增闸门 mixin 在入口最前端调用；返回 true 后调用方直接返回，
     * 排在后面的任何否决都不会被执行。走带事件的入口时加入事件仍会照常广播
     * （忽略其取消结果），使正常监听方的逻辑不受影响。</p>
     *
     * @param postEvent 是否广播加入事件；仅带事件的入口传 true，与原版行为保持一致
     */
    public static boolean forceAdd(Object manager, Object entityAccess, boolean worldGen, boolean postEvent) {
        if (!(manager instanceof PersistentEntitySectionManager<?> pesm)) return false;
        if (!(entityAccess instanceof Entity entity)) return false;
        ensureFields();
        if (PESM_SECTION_STORAGE == null || PESM_CALLBACKS == null) return false;
        try {
            // 广播加入事件供正常监听方处理，但不接受否决
            if (postEvent) {
                try {
                    MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, entity.level(), worldGen));
                } catch (Throwable ignored) {}
            }

            // UUID 登记表：已存在不算失败（重新加入受保护实体时本来就可能残留）
            addKnownUuid(pesm, entity);
            attachToSection(pesm, entity);
            putIntoLookup(pesm, entity);

            LevelCallback<Entity> callbacks = levelCallbacks(pesm);
            if (callbacks == null) return false;
            if (!worldGen) {
                try { callbacks.onCreated(entity); } catch (Throwable ignored) {}
            }
            Visibility visibility = effectiveVisibility(pesm, entity);
            if (visibility.isAccessible()) {
                try { callbacks.onTrackingStart(entity); } catch (Throwable ignored) {}
            }
            if (visibility.isTicking()) {
                try { callbacks.onTickingStart(entity); } catch (Throwable ignored) {}
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 按缺失项精准回填实体在世界结构中的存在，返回是否修补过任何一项。
     *
     * <p>每一项独立检测、独立修补，全部幂等：已经在位的结构不会被重复写入。
     * 不调用 {@code ServerLevel.addFreshEntity}，因此不经过任何可被否决的新增入口。</p>
     */
    public static boolean repair(ServerLevel level, Entity entity) {
        if (level == null || entity == null) return false;
        ensureFields();
        Object manager = entityManager(level);
        if (!(manager instanceof PersistentEntitySectionManager<?> pesm)) return false;

        boolean repaired = false;
        try {
            if (addKnownUuid(pesm, entity)) repaired = true;
            if (isSectionMissing(pesm, entity)) {
                attachToSection(pesm, entity);
                repaired = true;
            } else {
                // section 存在但可能残留重复引用：仍摘一次再挂，去重（移除保护导致残留多份）
                attachToSection(pesm, entity);
            }
            if (putIntoLookup(pesm, entity)) repaired = true;

            LevelCallback<Entity> callbacks = levelCallbacks(pesm);
            if (callbacks != null) {
                if (isTrackingMissing(level, entity)) {
                    // 跟踪表里没有：重新开始跟踪（同时补回 isAddedToWorld 与区块跟踪）
                    try { callbacks.onTrackingStart(entity); } catch (Throwable ignored) {}
                    repaired = true;
                }
                if (isTickListMissing(level, entity)) {
                    try { callbacks.onTickingStart(entity); } catch (Throwable ignored) {}
                    repaired = true;
                }
            }
        } catch (Throwable ignored) {
        }
        return repaired;
    }

    /** 实体是否已从世界内部结构的任意一处缺失（供守卫决定是否需要修补）。 */
    public static boolean isMissingFromWorld(ServerLevel level, Entity entity) {
        if (level == null || entity == null) return false;
        ensureFields();
        Object manager = entityManager(level);
        if (!(manager instanceof PersistentEntitySectionManager<?> pesm)) return false;
        try {
            if (isLookupMissing(pesm, entity)) return true;
            if (isKnownUuidMissing(pesm, entity)) return true;
            if (isSectionMissing(pesm, entity)) return true;
            if (isTickListMissing(level, entity)) return true;
            if (isTrackingMissing(level, entity)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    // -------------------------------------------------------------- 单项修补

    /** knownUuids 缺本实体 UUID 时补回；返回是否补过。 */
    private static boolean addKnownUuid(PersistentEntitySectionManager<?> pesm, Entity entity) {
        try {
            if (PESM_KNOWN_UUIDS == null) return false;
            Object value = PESM_KNOWN_UUIDS.get(pesm);
            if (value instanceof Set<?> set) {
                @SuppressWarnings("unchecked")
                Set<UUID> uuids = (Set<UUID>) set;
                return uuids.add(entity.getUUID());
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 把实体挂回它当前坐标所属的 section，并重建原版 section 回调。 */
    private static void attachToSection(PersistentEntitySectionManager<?> pesm, Entity entity) {
        try {
            if (PESM_SECTION_STORAGE == null) return;
            Object storageObj = PESM_SECTION_STORAGE.get(pesm);
            if (!(storageObj instanceof EntitySectionStorage<?> storage)) return;
            long sectionKey = SectionPos.asLong(entity.blockPosition());

            @SuppressWarnings("unchecked")
            EntitySectionStorage<Entity> typed = (EntitySectionStorage<Entity>) storage;
            EntitySection<Entity> section = typed.getOrCreateSection(sectionKey);
            if (section == null) return;
            // 彻底去重：移除保护拦截 onRemove 会让 section 残留同一实体的多份引用，
            // 单次 remove 只删一个不够，循环清空后再 add 一次，确保 section 里只有一份。
            try {
                for (Entity e : section.getEntities().toList()) {
                    if (e == entity) {
                        section.remove(entity);
                    }
                }
            } catch (Throwable ignored) {}
            section.add(entity);

            EntityInLevelCallback callback = newSectionCallback(pesm, entity, sectionKey, section);
            if (callback != null) entity.setLevelCallback(callback);
        } catch (Throwable ignored) {
        }
    }

    /** 直接写 EntityLookup 的两张表；返回是否补过任意一张。 */
    private static boolean putIntoLookup(PersistentEntitySectionManager<?> pesm, Entity entity) {
        boolean repaired = false;
        try {
            Object lookup = visibleStorage(pesm);
            if (lookup == null) return false;
            if (LOOKUP_BY_ID != null) {
                Object byId = LOOKUP_BY_ID.get(lookup);
                if (byId instanceof Int2ObjectMap<?> map) {
                    @SuppressWarnings("unchecked")
                    Int2ObjectMap<Entity> typed = (Int2ObjectMap<Entity>) map;
                    if (typed.get(entity.getId()) != entity) {
                        typed.put(entity.getId(), entity);
                        repaired = true;
                    }
                }
            }
            if (LOOKUP_BY_UUID != null) {
                Object byUuid = LOOKUP_BY_UUID.get(lookup);
                if (byUuid instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<UUID, Entity> typed = (Map<UUID, Entity>) map;
                    if (typed.get(entity.getUUID()) != entity) {
                        typed.put(entity.getUUID(), entity);
                        repaired = true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return repaired;
    }

    // -------------------------------------------------------------- 单项检测

    private static boolean isLookupMissing(PersistentEntitySectionManager<?> pesm, Entity entity) {
        try {
            Object lookup = visibleStorage(pesm);
            if (lookup == null) return false;
            if (LOOKUP_BY_ID != null && LOOKUP_BY_ID.get(lookup) instanceof Int2ObjectMap<?> byId) {
                if (byId.get(entity.getId()) != entity) return true;
            }
            if (LOOKUP_BY_UUID != null && LOOKUP_BY_UUID.get(lookup) instanceof Map<?, ?> byUuid) {
                if (byUuid.get(entity.getUUID()) != entity) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isKnownUuidMissing(PersistentEntitySectionManager<?> pesm, Entity entity) {
        try {
            if (PESM_KNOWN_UUIDS == null) return false;
            Object value = PESM_KNOWN_UUIDS.get(pesm);
            return value instanceof Set<?> set && !set.contains(entity.getUUID());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSectionMissing(PersistentEntitySectionManager<?> pesm, Entity entity) {
        try {
            if (PESM_SECTION_STORAGE == null) return false;
            Object storageObj = PESM_SECTION_STORAGE.get(pesm);
            if (!(storageObj instanceof EntitySectionStorage<?> storage)) return false;
            @SuppressWarnings("unchecked")
            EntitySectionStorage<Entity> typed = (EntitySectionStorage<Entity>) storage;
            EntitySection<Entity> section = typed.getSection(SectionPos.asLong(entity.blockPosition()));
            if (section == null) return true;
            for (Entity inSection : section.getEntities().toList()) {
                if (inSection == entity) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTickListMissing(ServerLevel level, Entity entity) {
        try {
            if (LEVEL_TICK_LIST == null) return false;
            Object list = LEVEL_TICK_LIST.get(level);
            return list instanceof EntityTickList tickList && !tickList.contains(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTrackingMissing(ServerLevel level, Entity entity) {
        try {
            if (!entity.isAddedToWorld()) return true;
            if (CHUNK_MAP_FIELD == null || CHUNK_MAP_TRACKED == null) return false;
            Object chunkMap = CHUNK_MAP_FIELD.get(level.getChunkSource());
            if (chunkMap == null) return false;
            Object tracked = CHUNK_MAP_TRACKED.get(chunkMap);
            return tracked instanceof Map<?, ?> map && !map.containsKey(entity.getId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------ 结构访问器

    private static Object entityManager(ServerLevel level) {
        try {
            return LEVEL_ENTITY_MANAGER == null ? null : LEVEL_ENTITY_MANAGER.get(level);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object visibleStorage(PersistentEntitySectionManager<?> pesm) {
        try {
            if (PESM_VISIBLE_STORAGE == null) return null;
            Object value = PESM_VISIBLE_STORAGE.get(pesm);
            return value instanceof EntityLookup<?> ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static LevelCallback<Entity> levelCallbacks(PersistentEntitySectionManager<?> pesm) {
        try {
            if (PESM_CALLBACKS == null) return null;
            Object value = PESM_CALLBACKS.get(pesm);
            return value instanceof LevelCallback<?> ? (LevelCallback<Entity>) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Visibility effectiveVisibility(PersistentEntitySectionManager<?> pesm, Entity entity) {
        if (entity.isAlwaysTicking()) return Visibility.TICKING;
        try {
            if (PESM_SECTION_STORAGE != null
                    && PESM_SECTION_STORAGE.get(pesm) instanceof EntitySectionStorage<?> storage) {
                @SuppressWarnings("unchecked")
                EntitySectionStorage<Entity> typed = (EntitySectionStorage<Entity>) storage;
                EntitySection<Entity> section = typed.getSection(SectionPos.asLong(entity.blockPosition()));
                if (section != null) return section.getStatus();
            }
        } catch (Throwable ignored) {
        }
        return Visibility.TICKING;
    }

    /** 构造原版挂在实体上的 section 回调（内部类，按「实现了实体回调接口」定位，不依赖类名）。 */
    private static EntityInLevelCallback newSectionCallback(PersistentEntitySectionManager<?> pesm,
                                                            Entity entity, long sectionKey, Object section) {
        try {
            if (SECTION_CALLBACK_CTOR == null) return null;
            Object created = SECTION_CALLBACK_CTOR.getParameterCount() == 4
                ? SECTION_CALLBACK_CTOR.newInstance(pesm, entity, sectionKey, section)
                : SECTION_CALLBACK_CTOR.newInstance(entity, sectionKey, section);
            return created instanceof EntityInLevelCallback callback ? callback : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------ 字段形状定位

    private static void ensureFields() {
        if (FIELDS_READY) return;
        synchronized (WorldPresenceGuard.class) {
            if (FIELDS_READY) return;
            try {
                PESM_KNOWN_UUIDS = exactType(PersistentEntitySectionManager.class, Set.class);
                PESM_VISIBLE_STORAGE = exactType(PersistentEntitySectionManager.class, EntityLookup.class);
                PESM_SECTION_STORAGE = exactType(PersistentEntitySectionManager.class, EntitySectionStorage.class);
                PESM_CALLBACKS = exactType(PersistentEntitySectionManager.class, LevelCallback.class);

                LOOKUP_BY_ID = exactType(EntityLookup.class, Int2ObjectMap.class);
                LOOKUP_BY_UUID = exactType(EntityLookup.class, Map.class);

                LEVEL_ENTITY_MANAGER = exactType(ServerLevel.class, PersistentEntitySectionManager.class);
                LEVEL_TICK_LIST = exactType(ServerLevel.class, EntityTickList.class);

                CHUNK_MAP_FIELD = exactType(net.minecraft.server.level.ServerChunkCache.class,
                    net.minecraft.server.level.ChunkMap.class);
                CHUNK_MAP_TRACKED = exactType(net.minecraft.server.level.ChunkMap.class, Int2ObjectMap.class);

                SECTION_CALLBACK_CTOR = findSectionCallbackCtor();
            } catch (Throwable ignored) {
            } finally {
                FIELDS_READY = true;
            }
        }
    }

    /** 在 owner 中查找声明类型<b>恰好</b>为 type 的实例字段（同类型多于一个时放弃，避免误伤）。 */
    private static Field exactType(Class<?> owner, Class<?> type) {
        Field found = null;
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() != type) continue;
                if (found != null) return null;
                found = f;
            }
            if (found != null) break;
        }
        if (found != null) {
            try {
                found.setAccessible(true);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return found;
    }

    private static Constructor<?> findSectionCallbackCtor() {
        try {
            for (Class<?> inner : PersistentEntitySectionManager.class.getDeclaredClasses()) {
                if (!EntityInLevelCallback.class.isAssignableFrom(inner)) continue;
                for (Constructor<?> ctor : inner.getDeclaredConstructors()) {
                    Class<?>[] params = ctor.getParameterTypes();
                    // (外部类, 实体, section 键, section) 或静态形式 (实体, section 键, section)
                    boolean matches = (params.length == 4
                            && params[0] == PersistentEntitySectionManager.class
                            && EntityAccess.class.isAssignableFrom(params[1])
                            && params[2] == long.class
                            && params[3] == EntitySection.class)
                        || (params.length == 3
                            && EntityAccess.class.isAssignableFrom(params[0])
                            && params[1] == long.class
                            && params[2] == EntitySection.class);
                    if (matches) {
                        ctor.setAccessible(true);
                        return ctor;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
