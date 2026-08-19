package net.minecraft.client.yiz.xian.core;

/**
 * 光明指南针工作槽的客户端缓存。
 *
 * <p>1.20.1 无 1.21.1 的 PlayerDataAPI（AttachmentType）体系，工作槽改走两层：
 * 服务端 {@code player.getPersistentData()} 存存档持久化（LightCompassMenu 负责），
 * 变更时经 {@code S2CLightCompassSlotsPayload} 推一份到客户端，客户端缓存于此，
 * 供 {@code LightCompassHighlightRenderer} 扫描时读取。</p>
 *
 * <p>3 个槽位，值为物品注册表 ID，-1 表示空槽。</p>
 */
public final class LightCompassData {

    /** 服务端持久化 NBT key（player.getPersistentData() 内的 int[] 键）。 */
    public static final String NBT_KEY = "yiz_light_compass_slots";

    /** 客户端缓存：3 个工作槽物品 ID，-1 空槽。默认全空。 */
    private static final int[] WORK_SLOTS = {-1, -1, -1};

    private LightCompassData() {}

    /** 客户端：S2C 包到达时更新缓存。 */
    public static synchronized void setClientSlots(int[] slots) {
        for (int i = 0; i < WORK_SLOTS.length; i++) {
            WORK_SLOTS[i] = (slots != null && i < slots.length) ? slots[i] : -1;
        }
    }

    /** 客户端：读取缓存（返回副本，避免外部修改）。 */
    public static synchronized int[] getClientSlots() {
        return WORK_SLOTS.clone();
    }
}
