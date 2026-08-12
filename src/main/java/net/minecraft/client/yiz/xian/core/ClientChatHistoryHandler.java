package net.minecraft.client.yiz.xian.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 客户端聊天历史灌回（客户端）— 玩家进世界时把本地持久化的历史灌回 ChatComponent，
 * 之后聊天框上下键即可召回过去指令（跨会话）。
 */
@Mod.EventBusSubscriber(modid = "yizxianmod", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientChatHistoryHandler {

    private ClientChatHistoryHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        if (chat == null) return;
        List<String> history = chat.getRecentChat();
        if (history == null) return;
        List<String> loaded = net.minecraft.client.yiz.xian.core.ClientChatHistory.load();
        // 旧历史按时间序前置（旧→新），去重，容量上限 100
        for (int i = loaded.size() - 1; i >= 0; i--) {
            String s = loaded.get(i);
            if (!history.contains(s)) history.add(0, s);
        }
        while (history.size() > 100) history.remove(history.size() - 1);
    }
}
