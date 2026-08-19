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
    }
}
