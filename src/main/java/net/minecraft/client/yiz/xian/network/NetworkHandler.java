package net.minecraft.client.yiz.xian.network;

import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 资源模组 SimpleChannel 网络（1.20.1 移植版）。
 */
public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(YizxianMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private NetworkHandler() {}

    public static void register() {
        CHANNEL.registerMessage(packetId++,
            C2SEntityAttributeEditPayload.class,
            C2SEntityAttributeEditPayload::encode,
            C2SEntityAttributeEditPayload::decode,
            C2SEntityAttributeEditPayload::handle
        );
        CHANNEL.registerMessage(packetId++,
            C2SDreamAttackPayload.class,
            C2SDreamAttackPayload::encode,
            C2SDreamAttackPayload::decode,
            C2SDreamAttackPayload::handle
        );
        CHANNEL.registerMessage(packetId++,
            S2CEntityTargetPayload.class,
            S2CEntityTargetPayload::encode,
            S2CEntityTargetPayload::decode,
            S2CEntityTargetPayload::handle
        );
        // 实体探查镜：服务端把被探查目标实体 id 推给客户端 Screen
        CHANNEL.registerMessage(packetId++,
            S2CEntityProbeTargetPayload.class,
            S2CEntityProbeTargetPayload::encode,
            S2CEntityProbeTargetPayload::decode,
            S2CEntityProbeTargetPayload::handle
        );
        // 光明指南针工作槽：C2S 操作 + S2C 内容同步
        CHANNEL.registerMessage(packetId++,
            C2SLightCompassWorkSlotPayload.class,
            C2SLightCompassWorkSlotPayload::encode,
            C2SLightCompassWorkSlotPayload::decode,
            C2SLightCompassWorkSlotPayload::handle
        );
        CHANNEL.registerMessage(packetId++,
            S2CLightCompassSlotsPayload.class,
            S2CLightCompassSlotsPayload::encode,
            S2CLightCompassSlotsPayload::decode,
            S2CLightCompassSlotsPayload::handle
        );
        // 实体探查镜：蓝条心跳请求 + S2C 蓝量刷新
        CHANNEL.registerMessage(packetId++,
            C2SEntityProbeBlueRequestPayload.class,
            C2SEntityProbeBlueRequestPayload::encode,
            C2SEntityProbeBlueRequestPayload::decode,
            C2SEntityProbeBlueRequestPayload::handle
        );
        CHANNEL.registerMessage(packetId++,
            S2CEntityProbeBluePayload.class,
            S2CEntityProbeBluePayload::encode,
            S2CEntityProbeBluePayload::decode,
            S2CEntityProbeBluePayload::handle
        );
        // 实体属性编辑器：免清除/拉回 保护开关（点击即生效）
        CHANNEL.registerMessage(packetId++,
            C2SEntityToggleEffectPayload.class,
            C2SEntityToggleEffectPayload::encode,
            C2SEntityToggleEffectPayload::decode,
            C2SEntityToggleEffectPayload::handle
        );
    }
}
