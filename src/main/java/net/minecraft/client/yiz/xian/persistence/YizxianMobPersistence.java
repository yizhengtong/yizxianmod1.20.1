package net.minecraft.client.yiz.xian.persistence;

import net.minecraft.client.yiz.tool.health.SecureHealthClosure;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 辖界者独立持久化（SavedData）——对抗外部模组「删磁盘数据」：
 * chunk 里的实体 NBT 被外部模组删掉后，本 .dat 仍保留辖界者快照，重进时据此重新 spawn。
 *
 * <p>快照内容：实体类型 key + 位置/朝向 + 逻辑血量（SecureHealthClosure 权威表值）。
 * 血量精确恢复走 {@link SecureHealthClosure#setHealth}（写权威表 + 混淆串）。</p>
 */
public class YizxianMobPersistence extends SavedData {

    private static final String NAME = "yizxianmod_mobs";

    /** 辖界者快照：类型 key + 位置/朝向 + 逻辑血量。 */
    public record MobSnapshot(String entityTypeKey, double x, double y, double z,
                              float yRot, float xRot, float health) {}

    private final Map<UUID, MobSnapshot> mobs = new ConcurrentHashMap<>();

    private YizxianMobPersistence() {}

    // ==================== NBT 序列化 ====================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, MobSnapshot> e : mobs.entrySet()) {
            MobSnapshot s = e.getValue();
            CompoundTag m = new CompoundTag();
            m.putUUID("uuid", e.getKey());
            m.putString("type", s.entityTypeKey());
            m.putDouble("x", s.x());
            m.putDouble("y", s.y());
            m.putDouble("z", s.z());
            m.putFloat("yRot", s.yRot());
            m.putFloat("xRot", s.xRot());
            m.putFloat("health", s.health());
            list.add(m);
        }
        tag.put("mobs", list);
        return tag;
    }

    private static YizxianMobPersistence load(CompoundTag tag) {
        YizxianMobPersistence p = new YizxianMobPersistence();
        ListTag list = tag.getList("mobs", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag m = (CompoundTag) t;
            UUID uuid = m.getUUID("uuid");
            p.mobs.put(uuid, new MobSnapshot(
                m.getString("type"),
                m.getDouble("x"), m.getDouble("y"), m.getDouble("z"),
                m.getFloat("yRot"), m.getFloat("xRot"), m.getFloat("health")));
        }
        return p;
    }

    // ==================== 对外 API ====================

    /** 获取（不存在则创建）该世界的辖界者持久化数据。 */
    public static YizxianMobPersistence get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            YizxianMobPersistence::load, YizxianMobPersistence::new, NAME);
    }

    /** 写入/更新一个辖界者的快照（死亡表值 ≤0 不保存）。 */
    public static void saveMob(ServerLevel level, YizxianMob mob) {
        if (level == null || mob == null || level.isClientSide()) return;
        float hp = SecureHealthClosure.getHealth(mob);
        if (hp <= 0) return;
        ResourceLocation key = EntityType.getKey(mob.getType());
        if (key == null) return;
        YizxianMobPersistence p = get(level);
        p.mobs.put(mob.getUUID(), new MobSnapshot(
            key.toString(),
            mob.getX(), mob.getY(), mob.getZ(),
            mob.getYRot(), mob.getXRot(),
            hp));
        p.setDirty();
    }

    /** 从快照恢复：world 里没有对应 UUID 的辖界者时，重新 spawn 并精确恢复血量。 */
    public static void restoreMobs(ServerLevel level) {
        if (level == null || level.isClientSide()) return;
        YizxianMobPersistence p = get(level);
        for (UUID uuid : new java.util.ArrayList<>(p.mobs.keySet())) {
            respawnMob(level, uuid);
        }
    }

    /** 从快照重新 spawn 单个辖界者（被彻底删除后立即还原 / 重进恢复共用）。返回是否成功。 */
    public static boolean respawnMob(ServerLevel level, UUID uuid) {
        if (level == null || level.isClientSide() || uuid == null) return false;
        YizxianMobPersistence p = get(level);
        MobSnapshot s = p.mobs.get(uuid);
        if (s == null) return false;
        if (level.getEntity(uuid) != null) return false;  // 已存在（没被删），跳过
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(s.entityTypeKey()));
        if (type == null) return false;
        Entity entity;
        try {
            entity = type.create(level);
        } catch (Throwable ignored) {
            return false;
        }
        if (!(entity instanceof YizxianMob mob)) return false;
        try {
            mob.setUUID(uuid);
            mob.setPos(s.x(), s.y(), s.z());
            mob.setYRot(s.yRot());
            mob.setXRot(s.xRot());
            // 精确恢复逻辑血量：写权威表 + 混淆串（哨兵未初始化时也能写，非治疗方向也能写）
            SecureHealthClosure.setHealth(mob, s.health());
        } catch (Throwable ignored) {}
        try {
            level.addFreshEntity(mob);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
