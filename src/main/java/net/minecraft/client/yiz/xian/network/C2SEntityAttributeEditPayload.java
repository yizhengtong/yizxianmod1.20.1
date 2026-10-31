package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * C2S: 实体属性编辑工具「应用」请求。
 * 修改目标实体的 yizmodqzk 自定义属性值。
 *
 * <p>写入策略：本模组实体（YizxianMob）→ EntityAttributeGate（prot_ 受保护）；
 * 其他实体 → ItemAttributeHandler（普通 entity_ 前缀）。</p>
 */
public class C2SEntityAttributeEditPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final int targetId;
    private final String attrId;
    private final double value;

    public C2SEntityAttributeEditPayload(int targetId, String attrId, double value) {
        this.targetId = targetId;
        this.attrId = attrId;
        this.value = value;
    }

    public static void encode(C2SEntityAttributeEditPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
        buf.writeUtf(msg.attrId);
        buf.writeDouble(msg.value);
    }

    public static C2SEntityAttributeEditPayload decode(FriendlyByteBuf buf) {
        return new C2SEntityAttributeEditPayload(buf.readInt(), buf.readUtf(), buf.readDouble());
    }

    /** 客户端发送。 */
    public static void send(int targetId, String attrId, double value) {
        NetworkHandler.CHANNEL.sendToServer(new C2SEntityAttributeEditPayload(targetId, attrId, value));
    }

    /** 安全护栏：每玩家每秒最多 40 次属性写入（防刷包导致 StackWalker 鉴权 + 日志把服务端打满）。 */
    private static final int RATE_LIMIT_PER_SEC = 40;
    private static final java.util.Map<java.util.UUID, long[]> RATE = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean rateOk(java.util.UUID id) {
        long now = System.currentTimeMillis();
        long[] w = RATE.computeIfAbsent(id, k -> new long[]{now, 0L});
        synchronized (w) {
            if (now - w[0] >= 1000L) {
                w[0] = now;
                w[1] = 0L;
            }
            if (w[1] >= RATE_LIMIT_PER_SEC) return false;
            w[1]++;
            return true;
        }
    }

    /** 服务端接收处理。 */
    public static void handle(C2SEntityAttributeEditPayload payload, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            try {
                if (!(ctx.getSender() instanceof ServerPlayer)) return;
                ServerPlayer player = (ServerPlayer) ctx.getSender();
                // 安全护栏：属性 id 必须是合法路径且长度受限（避免 ResourceLocation 构造抛异常打断服务端 tick）
                if (payload.attrId == null || payload.attrId.isEmpty() || payload.attrId.length() > 64
                        || !ResourceLocation.isValidPath(payload.attrId)) {
                    return;
                }
                // 安全护栏：NaN/Infinity 属性值会污染整条属性链（最大生命/攻击力 NaN → 其它模组除零/NaN 崩溃）
                if (!Double.isFinite(payload.value)) {
                    LOGGER.warn("[AttrEdit] 拒绝非有限属性值 {} = {} (player={})",
                        payload.attrId, payload.value, player.getName().getString());
                    return;
                }
                if (!rateOk(player.getUUID())) return;

                if (!(player.level().getEntity(payload.targetId) instanceof LivingEntity)) return;
                LivingEntity target = (LivingEntity) player.level().getEntity(payload.targetId);
                if (player.distanceToSqr(target) > 64.0 * 64.0) return;

                // 1.20.1：ForgeRegistries.ATTRIBUTES 按 id 查找属性
                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(
                    new ResourceLocation("yizmodqzk", payload.attrId));
                if (attr == null) return;

                double v = payload.value;
                if (attr instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged) {
                    v = Math.max(ranged.getMinValue(), Math.min(ranged.getMaxValue(), v));
                    if (!Double.isFinite(v)) return;
                }

                if (target instanceof YizxianMob) {
                    // 编辑器合法编辑标记：该属性豁免属性标准化还原（AttributeStandardizer）
                    net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.markEdited(target, payload.attrId);
                    if (target.getAttribute(attr) == null) {
                        ensureAttribute(target, attr);
                    }
                    // EntityAttributeGate.set 接收 RegistryObject<Attribute>：包一个持有该属性的 RegistryObject
                    net.minecraftforge.registries.RegistryObject<Attribute> ro =
                        net.minecraftforge.registries.RegistryObject.create(
                            new ResourceLocation("yizmodqzk", payload.attrId), ForgeRegistries.ATTRIBUTES);
                    EntityAttributeGate.set(target, ro, payload.attrId, v);
                    // 诊断：确认服务端确实收到并写入编辑器值（实测"编辑器改属性是否实时生效"）
                    LOGGER.info("[AttrEdit] 写入 {} {} = {} (当前 getValue={})",
                        target.getUUID(), payload.attrId, v,
                        target.getAttribute(attr) != null ? target.getAttribute(attr).getValue() : -1);
                } else {
                    if (target.getAttribute(attr) == null) {
                        ensureAttribute(target, attr);
                    }
                    var inst = target.getAttribute(attr);
                    if (inst == null) return;
                    ItemAttributeHandler.setEntityAttribute(target, attr, payload.attrId, v,
                        AttributeModifier.Operation.ADDITION);
                }
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable t) {
                // 安全护栏：包处理异常只记录，不能让服务端 tick 崩溃
                LOGGER.error("[AttrEdit] 属性编辑包处理异常，已隔离", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    /** 反射往目标实体 AttributeMap 注入 yizmodqzk 属性实例（非本模组实体未挂载时）。
     *   1.20.1：AttributeMap.attributes 是 Map<Attribute, AttributeInstance>（非 Holder）。 */
    private static void ensureAttribute(LivingEntity target, Attribute attr) {
        try {
            java.lang.reflect.Field f = net.minecraft.world.entity.ai.attributes.AttributeMap.class.getDeclaredField("attributes");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<Attribute, net.minecraft.world.entity.ai.attributes.AttributeInstance> attrs =
                (java.util.Map<Attribute, net.minecraft.world.entity.ai.attributes.AttributeInstance>) f.get(target.getAttributes());
            if (attrs.containsKey(attr)) return; // 已存在
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                new net.minecraft.world.entity.ai.attributes.AttributeInstance(attr, i -> {});
            attrs.put(attr, inst);
        } catch (Exception e) {
            System.err.println("[AttrEdit] 注入属性失败: " + e.getMessage());
        }
    }
}
